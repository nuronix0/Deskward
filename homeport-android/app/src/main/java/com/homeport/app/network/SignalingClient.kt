package com.homeport.app.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SignalingClient"

// HMAC secret - must match the signaling server APP_HMAC_SECRET
const val APP_HMAC_SECRET = "homeport-global-relay-v1-secret-change-in-prod"
const val DEFAULT_SIGNALING_URL = "wss://homeport-signal.onrender.com"

enum class SignalingStatus { DISCONNECTED, CONNECTING, REGISTERED, FAILED }

/**
 * Relay session encryption adapter.
 * Wraps AES-256-GCM encryption/decryption over the signaling relay channel.
 * The signaling server only sees encrypted blobs - never plaintext.
 */
class RelaySession(
    val sessionId: String,
    private val encKey: SecretKey,
    private val decKey: SecretKey,
    private val onSend: (sessionId: String, data: String, iv: String, tag: String) -> Unit,
    private val onEnd: (sessionId: String) -> Unit
) {
    private val rng = SecureRandom()

    fun send(plaintext: String) {
        try {
            val ivBytes = ByteArray(12).also { rng.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, encKey, GCMParameterSpec(128, ivBytes))
            val cipherWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ciphertext = cipherWithTag.copyOf(cipherWithTag.size - 16)
            val tag = cipherWithTag.copyOfRange(cipherWithTag.size - 16, cipherWithTag.size)
            val dataB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val ivB64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP)
            val tagB64 = Base64.encodeToString(tag, Base64.NO_WRAP)
            onSend(sessionId, dataB64, ivB64, tagB64)
        } catch (e: Exception) {
            Log.e(TAG, "Relay encrypt failed: ${e.message}", e)
        }
    }

    fun receive(dataB64: String, ivB64: String, tagB64: String): String? {
        return try {
            val ciphertext = Base64.decode(dataB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val tag = Base64.decode(tagB64, Base64.NO_WRAP)
            val cipherWithTag = ciphertext + tag
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, decKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherWithTag), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Relay decrypt failed: ${e.message}", e)
            null
        }
    }

    fun close() { onEnd(sessionId) }
}

private fun deriveRelayKey(pairingSecret: String, sessionId: String, role: String): SecretKey {
    val salt = "homeport-relay:${sessionId}:${role}".toByteArray(Charsets.UTF_8)
    val spec = javax.crypto.spec.PBEKeySpec(pairingSecret.toCharArray(), salt, 100_000, 256)
    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
}

private fun computeHmacToken(deviceId: String, timestamp: Long): String {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(APP_HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val raw = mac.doFinal("${deviceId}:${timestamp}".toByteArray(Charsets.UTF_8))
    return raw.joinToString("") { "%02x".format(it) }
}

/**
 * HomePort Signaling Client (Android)
 *
 * Maintains a persistent WebSocket connection to the HomePort Global Signaling Server.
 * Enables cross-internet connections between devices via relay.
 *
 * How it works:
 *   1. Both devices register with their deviceId and HMAC auth token
 *   2. The QR code contains relay_id (= Desktop deviceId) and signaling_url
 *   3. Android scans QR -> calls requestRelayConnection(relayId, secret)
 *   4. Signaling server forwards CONNECT_OFFER to Desktop
 *   5. Desktop accepts -> RELAY_READY received by both sides
 *   6. All HomePort protocol messages tunnel through the relay, E2E encrypted
 */
class SignalingClient(
    private val deviceId: String,
    private val deviceName: String,
    private val platform: String = "Android"
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var signalingUrl: String = DEFAULT_SIGNALING_URL
    private var reconnectAttempt = 0
    private val reconnectDelays = listOf(1000L, 2000L, 5000L, 10000L, 30000L)
    private var stopped = false

    val relaySessions = ConcurrentHashMap<String, RelaySession>()
    private var activePairingSecret: String? = null
    private var activePairingExpiry = 0L

    var onStatusChanged: ((SignalingStatus) -> Unit)? = null
    var onRelayReady: ((RelaySession, String, String, String) -> Unit)? = null
    var onRelayMessage: ((String, String) -> Unit)? = null
    var onRelayEnded: ((String, String) -> Unit)? = null

    private val _status = MutableStateFlow(SignalingStatus.DISCONNECTED)
    val status: StateFlow<SignalingStatus> = _status.asStateFlow()

    private val pendingRelayRequests = ConcurrentHashMap<String, CompletableDeferred<RelaySession>>()

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun start(url: String = DEFAULT_SIGNALING_URL) {
        stopped = false
        signalingUrl = url
        reconnect()
    }

    fun stop() {
        stopped = true
        webSocket?.close(1000, "stopped")
        webSocket = null
        scope.cancel()
        _status.value = SignalingStatus.DISCONNECTED
    }

    fun setActivePairingSecret(secret: String?, expiresAt: Long = System.currentTimeMillis() + 5 * 60 * 1000L) {
        activePairingSecret = secret
        activePairingExpiry = expiresAt
    }

    suspend fun requestRelayConnection(
        targetRelayId: String,
        pairingSecret: String,
        timeoutMs: Long = 30_000
    ): RelaySession? {
        if (_status.value != SignalingStatus.REGISTERED) {
            Log.w(TAG, "Cannot request relay connection - not registered with signaling server")
            return null
        }
        val deferred = CompletableDeferred<RelaySession>()
        pendingRelayRequests[targetRelayId] = deferred
        sendJson(buildJsonObj("type" to "CONNECT_REQUEST", "targetDeviceId" to targetRelayId, "pairingSecret" to pairingSecret))
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Relay connection to $targetRelayId timed out")
            pendingRelayRequests.remove(targetRelayId)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Relay connection failed: ${e.message}")
            pendingRelayRequests.remove(targetRelayId)
            null
        }
    }

    private fun reconnect() {
        if (stopped) return
        _status.value = SignalingStatus.CONNECTING
        scope.launch {
            val delay = reconnectDelays.getOrElse(reconnectAttempt) { reconnectDelays.last() }
            if (reconnectAttempt > 0) {
                Log.d(TAG, "Reconnecting to signaling server in ${delay}ms (attempt $reconnectAttempt)...")
                delay(delay)
            }
            reconnectAttempt++
            connectWebSocket()
        }
    }

    private fun connectWebSocket() {
        if (stopped) return
        Log.d(TAG, "Connecting to signaling server: $signalingUrl")
        val request = Request.Builder().url(signalingUrl).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Connected to signaling server")
                reconnectAttempt = 0
                register()
            }
            override fun onMessage(ws: WebSocket, text: String) { handleMessage(text) }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling connection failed: ${t.message}")
                _status.value = SignalingStatus.DISCONNECTED
                onStatusChanged?.invoke(SignalingStatus.DISCONNECTED)
                if (!stopped) reconnect()
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Signaling disconnected: $code $reason")
                _status.value = SignalingStatus.DISCONNECTED
                onStatusChanged?.invoke(SignalingStatus.DISCONNECTED)
                if (!stopped) reconnect()
            }
        })
    }

    private fun register() {
        val timestamp = System.currentTimeMillis()
        val token = computeHmacToken(deviceId, timestamp)
        sendJson(buildJsonObj("type" to "REGISTER", "deviceId" to deviceId, "deviceName" to deviceName, "platform" to platform, "timestamp" to timestamp, "token" to token))
    }

    private fun handleMessage(raw: String) {
        try {
            val json = org.json.JSONObject(raw)
            when (json.optString("type")) {
                "REGISTER_ACK" -> {
                    if (json.optBoolean("success", false)) {
                        Log.i(TAG, "Registered with signaling server as $deviceId")
                        _status.value = SignalingStatus.REGISTERED
                        onStatusChanged?.invoke(SignalingStatus.REGISTERED)
                    } else {
                        Log.e(TAG, "Registration failed: ${json.optString("reason")}")
                        _status.value = SignalingStatus.FAILED
                        onStatusChanged?.invoke(SignalingStatus.FAILED)
                    }
                }
                "PING" -> sendJson(buildJsonObj("type" to "PONG", "ts" to json.optLong("ts")))
                "CONNECT_OFFER" -> {
                    val fromId = json.optString("fromDeviceId")
                    val fromName = json.optString("fromDeviceName", "Unknown")
                    val fromPlatform = json.optString("fromPlatform", "unknown")
                    val secret = json.optString("pairingSecret")
                    Log.d(TAG, "Incoming relay offer from $fromId ($fromName)")
                    val valid = activePairingSecret != null
                        && System.currentTimeMillis() < activePairingExpiry
                        && secret.trim().uppercase() == (activePairingSecret ?: "").trim().uppercase()
                    if (!valid) {
                        Log.w(TAG, "Rejecting relay offer from $fromId - invalid pairing secret")
                        sendJson(buildJsonObj("type" to "CONNECT_REJECT", "fromDeviceId" to fromId, "reason" to "invalid_pairing_secret"))
                        return
                    }
                    sendJson(buildJsonObj("type" to "CONNECT_ACCEPT", "fromDeviceId" to fromId))
                    Log.i(TAG, "Accepted relay connection from $fromId")
                }
                "CONNECT_PENDING" -> Log.d(TAG, "Connect offer forwarded to ${json.optString("targetDeviceId")}")
                "CONNECT_ERROR" -> {
                    val targetId = json.optString("targetDeviceId")
                    val reason = json.optString("reason")
                    Log.e(TAG, "Relay connect error (target=$targetId): $reason")
                    pendingRelayRequests[targetId]?.completeExceptionally(Exception(reason))
                    pendingRelayRequests.remove(targetId)
                }
                "CONNECT_REJECTED" -> {
                    val targetId = json.optString("targetDeviceId")
                    val reason = json.optString("reason")
                    Log.w(TAG, "Relay connection rejected by $targetId")
                    pendingRelayRequests[targetId]?.completeExceptionally(Exception("rejected: $reason"))
                    pendingRelayRequests.remove(targetId)
                }
                "RELAY_READY" -> {
                    val sessionId = json.optString("sessionId")
                    val peerDeviceId = json.optString("peerDeviceId")
                    val peerName = json.optString("peerDeviceName", "Unknown")
                    val peerPlatform = json.optString("peerPlatform", "unknown")
                    Log.i(TAG, "Relay session ready: $sessionId with $peerDeviceId ($peerName)")
                    val pairingSecret = activePairingSecret ?: "default"
                    val isInitiator = pendingRelayRequests.containsKey(peerDeviceId)
                    val encKey = deriveRelayKey(pairingSecret, sessionId, if (isInitiator) "A" else "B")
                    val decKey = deriveRelayKey(pairingSecret, sessionId, if (isInitiator) "B" else "A")
                    val session = RelaySession(
                        sessionId = sessionId, encKey = encKey, decKey = decKey,
                        onSend = { sid, data, iv, tag ->
                            sendJson(buildJsonObj("type" to "RELAY_DATA", "sessionId" to sid, "data" to data, "iv" to iv, "tag" to tag))
                        },
                        onEnd = { sid -> sendJson(buildJsonObj("type" to "RELAY_END", "sessionId" to sid)) }
                    )
                    relaySessions[sessionId] = session
                    pendingRelayRequests.remove(peerDeviceId)?.complete(session)
                    onRelayReady?.invoke(session, peerDeviceId, peerName, peerPlatform)
                }
                "RELAY_DATA" -> {
                    val sessionId = json.optString("sessionId")
                    val session = relaySessions[sessionId]
                    if (session == null) { Log.w(TAG, "No relay session: $sessionId"); return }
                    val plaintext = session.receive(json.optString("data"), json.optString("iv"), json.optString("tag"))
                    if (plaintext != null) onRelayMessage?.invoke(sessionId, plaintext)
                }
                "RELAY_ENDED" -> {
                    val sessionId = json.optString("sessionId")
                    val reason = json.optString("reason")
                    Log.d(TAG, "Relay ended: $sessionId ($reason)")
                    relaySessions.remove(sessionId)
                    onRelayEnded?.invoke(sessionId, reason)
                }
                "RELAY_ERROR" -> Log.e(TAG, "Relay error (${json.optString("sessionId")}): ${json.optString("reason")}")
                else -> Log.v(TAG, "Unknown signaling type: ${json.optString("type")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle signaling message: ${e.message}", e)
        }
    }

    private fun sendJson(json: String) {
        if (webSocket?.send(json) == false) {
            Log.w(TAG, "Failed to send signaling message (connection may be closed)")
        }
    }

    private fun buildJsonObj(vararg pairs: Pair<String, Any?>): String {
        val sb = StringBuilder("{")
        pairs.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append('"').append(k).append('"').append(':')
            when (v) {
                null -> sb.append("null")
                is String -> sb.append('"').append(v.replace("\"", "\\\"")).append('"')
                is Boolean -> sb.append(v)
                is Number -> sb.append(v)
                else -> sb.append('"').append(v.toString()).append('"')
            }
        }
        return sb.append("}").toString()
    }
}
