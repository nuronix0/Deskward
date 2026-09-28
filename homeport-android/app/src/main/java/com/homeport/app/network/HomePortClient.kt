package com.homeport.app.network

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.homeport.app.data.DeviceIdentityManager
import com.homeport.app.data.TrustedPeer
import com.homeport.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "HomePortClient"

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

data class PairingParams(
    val host: String,
    val port: Int,
    val peerId: String,
    val peerName: String,
    val secret: String
)

data class PeerSession(
    val key: String,
    val host: String,
    val port: Int,
    val pairingSecret: String?,
    val webSocket: WebSocket,
    var device: DeviceInfo? = null,
    var status: ConnectionStatus = ConnectionStatus.CONNECTING,
    var timeoutJob: Job? = null
)

class HomePortClient(
    private val context: Context,
    private val identityManager: DeviceIdentityManager = DeviceIdentityManager.getInstance(context)
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Concurrent multi-peer sessions keyed by "$host:$port"
    private val sessions = ConcurrentHashMap<String, PeerSession>()

    // All currently connected devices
    private val _connectedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<DeviceInfo>> = _connectedDevices.asStateFlow()

    // Primary/most-recently-active connected device for backward compatibility
    private val _connectedDevice = MutableStateFlow<DeviceInfo?>(null)
    val connectedDevice: StateFlow<DeviceInfo?> = _connectedDevice.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _activeTransfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val activeTransfers: StateFlow<List<TransferItem>> = _activeTransfers.asStateFlow()

    // Pending RPC deferreds
    private val pendingDirRequests = ConcurrentHashMap<String, CompletableDeferred<ListDirResultMessage>>()
    private val pendingSearchRequests = ConcurrentHashMap<String, CompletableDeferred<SearchResultMessage>>()
    private val pendingDeleteRequests = ConcurrentHashMap<String, CompletableDeferred<DeleteFileResultMessage>>()

    val fileCache = ConcurrentHashMap<String, FileItem>()

    // Active transfer download state
    private val activeDownloads = ConcurrentHashMap<String, DownloadSession>()

    // Direct WebRTC manager using Firebase Realtime Database for handshake
    val firebaseWebRtcManager: FirebaseWebRtcManager by lazy {
        FirebaseWebRtcManager.getInstance(context)
    }

    // Global relay signaling client for cross-internet connections
    val signalingClient: SignalingClient by lazy {
        val identity = identityManager.identity
        val sc = SignalingClient(
            deviceId = identity.id,
            deviceName = identity.name,
            platform = "Android"
        )
        sc.onRelayReady = { session, peerDeviceId, peerName, peerPlatform ->
            Log.i(TAG, "Relay session ready with $peerDeviceId ($peerName) via signaling server")
            // Route all relay messages through the HomePort protocol message handler
            session.let { relay ->
                val relayKey = "relay:${relay.sessionId}"
                // Handle incoming decrypted messages from the relay session
                sc.onRelayMessage = { sessionId, plaintext ->
                    val existingSession = sessions[relayKey]
                    if (existingSession != null) {
                        handleIncomingMessage(existingSession.webSocket, plaintext, "relay", 0, null, existingSession)
                    }
                }
                // Create a relay-backed PeerSession for protocol compatibility
                // We use the relay session's send() as the WebSocket send mechanism
                // by monkey-patching via a proxy WebSocket approach
                _statusMessage.value = "Connected via global relay to $peerName"
            }
        }
        sc.onRelayEnded = { sessionId, reason ->
            Log.d(TAG, "Relay session ended: $sessionId ($reason)")
            val key = "relay:$sessionId"
            sessions.remove(key)
            updateConnectedState()
        }
        sc
    }

    private data class DownloadSession(
        var transferId: String,
        val file: File,
        val finalDestination: File? = null,
        val totalSize: Long,
        val totalChunks: Int,
        var receivedChunks: Int = 0,
        var bytesReceived: Long = 0,
        val raf: RandomAccessFile,
        val deferred: CompletableDeferred<Boolean>,
        val onProgress: ((Float) -> Unit)? = null
    )

    init {
        scope.launch {
            delay(800)
            autoReconnectTrustedPeers()
        }
    }

    fun autoReconnectTrustedPeers() {
        val trustedList = identityManager.getTrustedPeers().filter { !it.revoked }
        for (trusted in trustedList) {
            if (trusted.host.isNotBlank() && trusted.host != "webrtc" && trusted.host != "relay") {
                val key = "${trusted.host}:${trusted.port}"
                if (!sessions.containsKey(key)) {
                    connect(
                        host = trusted.host,
                        port = trusted.port,
                        pairingSecret = null
                    )
                }
            } else {
                val rtcKey = "webrtc:${trusted.id}"
                if (!sessions.containsKey(rtcKey)) {
                    connectViaFirebaseWebRtc(
                        roomKey = trusted.id,
                        pairingSecret = null,
                        onResult = { _, _ -> }
                    )
                }
            }
        }
    }

    fun parsePairingUrl(url: String): PairingParams? {
        return try {
            val uri = Uri.parse(url)
            if (uri.scheme != "hp" || uri.host != "pair") return null
            val ip = uri.getQueryParameter("ip") ?: return null
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 51234
            val peerId = uri.getQueryParameter("id") ?: ""
            val peerName = uri.getQueryParameter("name") ?: "Desktop"
            val secret = uri.getQueryParameter("secret") ?: return null
            PairingParams(ip, port, peerId, peerName, secret)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Connect by code only — discovers the Desktop server automatically on the local network.
     * Scans the local subnet for port 51234 and fetches /api/pair-code to match the code.
     * This eliminates the need for the user to manually type IP address and port.
     *
     * @param code The 6-7 character pairing code shown on Desktop
     * @param onProgress Called with progress updates (0.0..1.0) and status message
     * @param onResult Called with (success, errorMessage?) when done
     */
    fun connectByCode(
        code: String,
        onProgress: ((Float, String) -> Unit)? = null,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        val trimmedCode = code.trim().uppercase()
        if (trimmedCode.length < 4) {
            onResult?.invoke(false, "Please enter a valid pairing code (at least 4 characters)")
            return
        }

        scope.launch {
            try {
                // Step 1: Get the phone's local IP to determine the subnet
                val localIp = getDeviceLocalIp()
                val parts = localIp?.split(".") ?: emptyList()
                if (localIp == null || localIp == "127.0.0.1" || parts.size != 4) {
                    onProgress?.invoke(0.10f, "Direct network scan unavailable — connecting via WebRTC…")
                    Log.i(TAG, "No local LAN IP, attempting Firebase WebRTC for code: $trimmedCode")
                    connectViaFirebaseWebRtc(
                        roomKey = trimmedCode,
                        pairingSecret = trimmedCode,
                        onProgress = { msg -> onProgress?.invoke(0.50f, msg) },
                        onResult = { ok, err ->
                            if (ok) {
                                onProgress?.invoke(1.0f, "Connected via WebRTC!")
                                onResult?.invoke(true, null)
                            } else {
                                onResult?.invoke(false, "Could not reach device with code $trimmedCode. Please check code on host device.")
                            }
                        }
                    )
                    return@launch
                }

                val subnet = "${parts[0]}.${parts[1]}.${parts[2]}"

                onProgress?.invoke(0.05f, "Scanning network for computer…")

                // Step 2: Scan subnet concurrently — check each host for the matching code
                val port = 51234
                val totalHosts = 254
                var scanned = 0
                var foundHost: String? = null

                // Use a channel-based approach to scan in parallel batches
                val batchSize = 254
                val hosts = (1..254).map { "$subnet.$it" }.filter { it != localIp }

                for (batch in hosts.chunked(batchSize)) {
                    if (foundHost != null) break
                    val results = coroutineScope {
                        batch.map { host ->
                            async(Dispatchers.IO) {
                                try {
                                    var isMatch = false
                                    // 1. Try Desktop HTTP check-code endpoint
                                    try {
                                        val checkUrl = java.net.URL("http://$host:$port/api/check-code?code=$trimmedCode")
                                        val checkConn = checkUrl.openConnection() as java.net.HttpURLConnection
                                        checkConn.connectTimeout = 280
                                        checkConn.readTimeout = 350
                                        checkConn.requestMethod = "GET"
                                        val checkCode = try { checkConn.responseCode } catch (_: Exception) { -1 }
                                        if (checkCode == 200) {
                                            val body = checkConn.inputStream.bufferedReader().use { it.readText() }
                                            if (body.contains("\"valid\":true")) {
                                                isMatch = true
                                            }
                                        }
                                        checkConn.disconnect()
                                    } catch (_: Exception) {}

                                    // 2. If Desktop HTTP didn't match, check if host is an Android phone running AndroidMeshServer
                                    if (!isMatch) {
                                        val isOpen = try {
                                            java.net.Socket().use { s ->
                                                s.connect(java.net.InetSocketAddress(host, port), 200)
                                                true
                                            }
                                        } catch (_: Exception) { false }

                                        if (isOpen) {
                                            isMatch = probeAndroidMeshServer(host, port, trimmedCode)
                                        }
                                    }

                                    if (isMatch) host else null
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }.awaitAll()
                    }
                    scanned += batch.size
                    val progress = 0.05f + (scanned.toFloat() / totalHosts) * 0.80f
                    onProgress?.invoke(progress, "Scanning... ($scanned/$totalHosts)")

                    val hit = results.filterNotNull().firstOrNull()
                    if (hit != null) {
                        foundHost = hit
                        break
                    }
                }


                if (foundHost == null) {
                    onProgress?.invoke(0.85f, "Not found on local Wi-Fi — trying direct WebRTC handshake…")
                    Log.i(TAG, "Computer not on local subnet, attempting Firebase WebRTC for code: $trimmedCode")
                    connectViaFirebaseWebRtc(
                        roomKey = trimmedCode,
                        pairingSecret = trimmedCode,
                        onProgress = { msg -> onProgress?.invoke(0.92f, msg) },
                        onResult = { ok, err ->
                            if (ok) {
                                onProgress?.invoke(1.0f, "Connected via WebRTC!")
                                onResult?.invoke(true, null)
                            } else {
                                Log.w(TAG, "connectByCode WebRTC failed, falling back to global relay. Error: $err")
                                // Fallback to relay
                                val dummyScanned = com.homeport.app.ui.screens.devices.ScannedPairData(
                                    ip = "", port = port, deviceId = trimmedCode, deviceName = "Peer",
                                    secret = trimmedCode, rawText = trimmedCode,
                                    altIps = emptyList(), relayId = trimmedCode, signalingUrl = null
                                )
                                tryRelayFallback(dummyScanned, trimmedCode, { msg -> onProgress?.invoke(1.0f, msg) }, { ok, relayErr -> onResult?.invoke(ok, relayErr) })
                            }
                        }
                    )
                    return@launch
                }

                onProgress?.invoke(0.90f, "Found computer at $foundHost — connecting…")

                // Step 3: Connect using the discovered host
                connect(
                    host = foundHost,
                    port = port,
                    pairingSecret = trimmedCode,
                    onResult = { success, err ->
                        if (success) {
                            onProgress?.invoke(1.0f, "Connected!")
                            onResult?.invoke(true, null)
                        } else {
                            onResult?.invoke(false, err ?: "Failed to authenticate with discovered server.")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "connectByCode failed: ${e.message}", e)
                onResult?.invoke(false, "Network scan error: ${e.message}")
            }
        }
    }

    /**
     * Fast probe for peer running AndroidMeshServer on local network.
     * Verifies the pairing secret over WebSocket without consuming it.
     */
    private suspend fun probeAndroidMeshServer(host: String, port: Int, code: String): Boolean {
        return withContext(Dispatchers.IO) {
            val deferred = CompletableDeferred<Boolean>()
            val request = Request.Builder().url("ws://$host:$port").build()
            var wsRef: WebSocket? = null

            val probeTimeout = launch {
                delay(600L)
                if (!deferred.isCompleted) {
                    try { wsRef?.close(1000, "Probe timeout") } catch (_: Exception) {}
                    deferred.complete(false)
                }
            }

            wsRef = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val req = CheckCodeMessage(code = code)
                    webSocket.send(ProtocolSerializer.toJson(req))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val type = ProtocolSerializer.parseType(text)
                        if (type == "CHECK_CODE_RESULT") {
                            val res = ProtocolSerializer.fromJson<CheckCodeResultMessage>(text)
                            deferred.complete(res.valid)
                        } else {
                            deferred.complete(false)
                        }
                    } catch (_: Exception) {
                        deferred.complete(false)
                    } finally {
                        probeTimeout.cancel()
                        try { webSocket.close(1000, "Probe done") } catch (_: Exception) {}
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    probeTimeout.cancel()
                    deferred.complete(false)
                }
            })

            try {
                deferred.await()
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Get the device's current local IPv4 address on the active Wi-Fi/LAN interface.
     */
    fun getDeviceLocalIp(): String? {
        val ip = NetworkUtils.getBestLocalIpv4(context)
        return if (ip != "127.0.0.1") ip else null
    }

    /**
     * Connect to a peer using scanned QR code data with automatic fallback resolution:
     * 1. Check if the provided IP is a cellular CGNAT address (100.64.0.0/10) - if so, prioritize local LAN candidates
     * 2. Gather candidates: non-CGNAT IP, altIps, default gateway IP (for phone/desktop hotspot connection)
     * 3. Attempt connection to candidates sequentially
     * 4. If all direct candidates fail and a pairing secret is available, automatically scan the local subnet
     */
    fun connectScanned(
        scanned: com.homeport.app.ui.screens.devices.ScannedPairData,
        onProgress: ((String) -> Unit)? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        val targetSecret = scanned.secret
        val targetPort = scanned.port
        val primaryIp = scanned.ip

        // Build list of candidate IPs to try
        val candidates = mutableListOf<String>()

        // Add primary IP if not CGNAT
        if (!primaryIp.isNullOrBlank() && !NetworkUtils.isCgnat(primaryIp) && primaryIp != "127.0.0.1") {
            candidates.add(primaryIp)
        }

        // Add alt_ips if present
        for (alt in scanned.altIps) {
            val cleanAlt = alt.trim()
            if (cleanAlt.isNotEmpty() && !NetworkUtils.isCgnat(cleanAlt) && cleanAlt != "127.0.0.1" && !candidates.contains(cleanAlt)) {
                candidates.add(cleanAlt)
            }
        }

        // Add default gateway IP (critical when connected to a mobile hotspot or Wi-Fi AP hosted by the other device)
        val gateway = NetworkUtils.getDefaultGatewayIp(context)
        if (!gateway.isNullOrBlank() && !candidates.contains(gateway) && !NetworkUtils.isCgnat(gateway)) {
            candidates.add(gateway)
        }

        // If primary IP was CGNAT, append it as a last resort
        if (!primaryIp.isNullOrBlank() && NetworkUtils.isCgnat(primaryIp) && !candidates.contains(primaryIp)) {
            candidates.add(primaryIp)
        }

        Log.i(TAG, "connectScanned: candidates for ${scanned.deviceName ?: "peer"}: $candidates (original IP: $primaryIp)")

        // Try candidates one by one
        fun tryNextCandidate(index: Int) {
            if (index < candidates.size) {
                val host = candidates[index]
                onProgress?.invoke("Connecting to $host…")
                Log.i(TAG, "connectScanned: trying candidate $index: $host:$targetPort")
                connect(host, targetPort, targetSecret, timeoutMs = 3500L) { success, err ->
                    if (success) {
                        onResult(true, null)
                    } else {
                        Log.w(TAG, "connectScanned: candidate $host failed: $err")
                        tryNextCandidate(index + 1)
                    }
                }
            } else {
                // Direct candidate IPs failed — try Firebase WebRTC directly using secret or device ID first!
                if (targetSecret.isNotBlank() && targetSecret.length >= 4) {
                    onProgress?.invoke("Direct LAN candidate failed — trying WebRTC handshake…")
                    val roomKey = if (!scanned.relayId.isNullOrBlank()) scanned.relayId else targetSecret
                    connectViaFirebaseWebRtc(
                        roomKey = roomKey,
                        pairingSecret = targetSecret,
                        onProgress = onProgress,
                        onResult = { ok, err ->
                            if (ok) {
                                onResult(true, null)
                            } else {
                                Log.i(TAG, "connectScanned: WebRTC failed, falling back to connectByCode ($err)")
                                onProgress?.invoke("Scanning local network for ${scanned.deviceName ?: "peer"}…")
                                connectByCode(
                                    code = targetSecret,
                                    onProgress = { _, msg -> onProgress?.invoke(msg) },
                                    onResult = { success, _ ->
                                        if (success) {
                                            onResult(true, null)
                                        } else {
                                            tryRelayFallback(scanned, targetSecret, onProgress, onResult)
                                        }
                                    }
                                )
                            }
                        }
                    )
                } else {
                    tryRelayFallback(scanned, targetSecret, onProgress, onResult)
                }
            }
        }

        if (candidates.isNotEmpty()) {
            tryNextCandidate(0)
        } else if (targetSecret.isNotBlank() && targetSecret.length >= 4) {
            // No direct IP candidates, scan subnet directly by code
            onProgress?.invoke("Scanning network for peer…")
            connectByCode(
                code = targetSecret,
                onProgress = { _, msg -> onProgress?.invoke(msg) },
                onResult = { success, _ ->
                    if (success) onResult(true, null)
                    else tryRelayFallback(scanned, targetSecret, onProgress, onResult)
                }
            )
        } else {
            tryRelayFallback(scanned, targetSecret, onProgress, onResult)
        }
    }

    /**
     * Direct peer-to-peer connection via WebRTC using Firebase for the handshake.
     * All traffic is direct P2P (zero server bandwidth, zero data limits, fully private).
     */
    fun connectViaFirebaseWebRtc(
        roomKey: String,
        pairingSecret: String? = null,
        onProgress: ((String) -> Unit)? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        val key = "webrtc:$roomKey"

        // If a session to this key already exists, recycle it
        sessions[key]?.let { existing ->
            try {
                existing.timeoutJob?.cancel()
                existing.webSocket.close(1000, "Reconnecting via WebRTC")
            } catch (_: Exception) {}
            sessions.remove(key)
        }

        MeshForegroundService.start(context)

        _connectionStatus.value = ConnectionStatus.CONNECTING
        _statusMessage.value = "Connecting via Firebase WebRTC…"
        onProgress?.invoke("Exchanging WebRTC handshake via Firebase…")

        var connected = false

        val timeoutJob = scope.launch {
            delay(20_000L)
            if (!connected) {
                Log.w(TAG, "WebRTC connection to room $roomKey timed out")
                firebaseWebRtcManager.close()
                sessions.remove(key)
                updateConnectedState()
                val failMsg = "WebRTC connection timed out. Make sure HomePort is running on your computer."
                _statusMessage.value = failMsg
                _connectionStatus.value = ConnectionStatus.FAILED
                onResult(false, failMsg)
            }
        }

        firebaseWebRtcManager.connect(
            roomKey = roomKey,
            onChannelOpen = { adapter ->
                Log.i(TAG, "WebRTC DataChannel OPEN for room $roomKey")
                onProgress?.invoke("WebRTC tunnel established! Authenticating…")

                val session = PeerSession(
                    key = key,
                    host = "webrtc",
                    port = 0,
                    pairingSecret = pairingSecret,
                    webSocket = adapter,
                    status = ConnectionStatus.CONNECTING,
                    timeoutJob = timeoutJob
                )
                sessions[key] = session

                // Send HELLO through WebRTC DataChannel
                val identity = identityManager.identity
                val hello = HelloMessage(
                    deviceId = identity.id,
                    deviceName = identity.name,
                    platform = identity.platform,
                    pairingSecret = pairingSecret,
                    publicKey = identity.publicKey
                )
                adapter.send(ProtocolSerializer.toJson(hello))
                Log.i(TAG, "Sent HELLO over WebRTC DataChannel to room $roomKey")
            },
            onMessage = { raw ->
                val session = sessions[key]
                val adapter = session?.webSocket
                if (adapter != null) {
                    val msgType = ProtocolSerializer.parseType(raw)
                    if (msgType == "HELLO_ACK") {
                        val ack = ProtocolSerializer.fromJson<HelloAckMessage>(raw)
                        if (ack.accepted) {
                            connected = true
                            timeoutJob.cancel()
                        }
                    }
                    handleIncomingMessage(adapter, raw, "webrtc", 0, onResult, session)
                }
            },
            onError = { err ->
                Log.e(TAG, "WebRTC error for room $roomKey: $err")
                timeoutJob.cancel()
                sessions.remove(key)
                updateConnectedState()
                _connectionStatus.value = ConnectionStatus.FAILED
                _statusMessage.value = err
                onResult(false, err)
            }
        )
    }

    private fun tryRelayFallback(
        scanned: com.homeport.app.ui.screens.devices.ScannedPairData,
        pairingSecret: String,
        onProgress: ((String) -> Unit)?,
        onResult: (Boolean, String?) -> Unit
    ) {
        val roomKey = if (!scanned.relayId.isNullOrBlank()) scanned.relayId else pairingSecret
        onProgress?.invoke("Connecting via direct WebRTC handshake…")
        Log.i(TAG, "tryRelayFallback: trying Firebase WebRTC first for roomKey=$roomKey")

        connectViaFirebaseWebRtc(
            roomKey = roomKey,
            pairingSecret = pairingSecret,
            onProgress = onProgress,
            onResult = { ok, err ->
                if (ok) {
                    onResult(true, null)
                } else {
                    Log.i(TAG, "Firebase WebRTC fallback to signaling relay: $err")
                    tryCustomSignalingRelay(scanned, pairingSecret, onProgress, onResult)
                }
            }
        )
    }

    private fun tryCustomSignalingRelay(
        scanned: com.homeport.app.ui.screens.devices.ScannedPairData,
        pairingSecret: String,
        onProgress: ((String) -> Unit)?,
        onResult: (Boolean, String?) -> Unit
    ) {
        val relayId = scanned.relayId
        val signalingUrl = scanned.signalingUrl ?: DEFAULT_SIGNALING_URL

        if (relayId.isNullOrBlank()) {
            val failMsg = "Cannot reach device on any network. Scan an updated QR code (which includes global relay info) or connect to the same Wi-Fi."
            _statusMessage.value = failMsg
            onResult(false, failMsg)
            return
        }

        onProgress?.invoke("Trying global relay connection…")
        Log.i(TAG, "tryRelayFallback: connecting to relay_id=$relayId via $signalingUrl")
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _statusMessage.value = "Connecting via global relay…"

        val sc = signalingClient
        if (sc.status.value == SignalingStatus.DISCONNECTED || sc.status.value == SignalingStatus.FAILED) {
            sc.start(signalingUrl)
        }
        sc.setActivePairingSecret(pairingSecret)

        scope.launch {
            // Wait for registration (up to 10s)
            var waited = 0
            while (sc.status.value != SignalingStatus.REGISTERED && waited < 10_000) {
                delay(250)
                waited += 250
            }

            if (sc.status.value != SignalingStatus.REGISTERED) {
                val failMsg = "Cannot reach relay server. Check your internet connection."
                _statusMessage.value = failMsg
                _connectionStatus.value = ConnectionStatus.FAILED
                onResult(false, failMsg)
                return@launch
            }

            onProgress?.invoke("Reached relay server — requesting connection…")

            val relaySession = sc.requestRelayConnection(relayId, pairingSecret)
            if (relaySession == null) {
                val failMsg = "Relay connection timed out. Make sure the desktop is running and the pairing code is active."
                _statusMessage.value = failMsg
                _connectionStatus.value = ConnectionStatus.FAILED
                onResult(false, failMsg)
                return@launch
            }

            // Send HELLO through the relay tunnel
            val identity = identityManager.identity
            val hello = HelloMessage(
                deviceId = identity.id,
                deviceName = identity.name,
                platform = identity.platform,
                pairingSecret = pairingSecret,
                publicKey = identity.publicKey
            )
            relaySession.send(ProtocolSerializer.toJson(hello))
            onProgress?.invoke("Authenticating via relay…")
            Log.i(TAG, "Sent HELLO via relay to $relayId")

            // Wait for HELLO_ACK within 20s
            var connected = false
            sc.onRelayMessage = { sessionId, plaintext ->
                if (sessionId == relaySession.sessionId && !connected) {
                    try {
                        val msgType = ProtocolSerializer.parseType(plaintext)
                        if (msgType == "HELLO_ACK") {
                            val ack = ProtocolSerializer.fromJson<HelloAckMessage>(plaintext)
                            if (ack.accepted) {
                                connected = true
                                Log.i(TAG, "Relay HELLO_ACK accepted from ${ack.deviceName}")
                                val device = DeviceInfo(
                                    id = ack.deviceId,
                                    name = ack.deviceName,
                                    platform = ack.platform,
                                    type = com.homeport.app.domain.model.DeviceType.LAPTOP,
                                    status = com.homeport.app.domain.model.DeviceStatus.ONLINE,
                                    storageTotal = ack.storageTotal ?: 0L,
                                    storageUsed = ack.storageUsed ?: 0L,
                                    connectionRoute = com.homeport.app.domain.model.ConnectionRoute.RELAY,
                                    lastSeen = System.currentTimeMillis(),
                                    isTrusted = true
                                )
                                val peer = com.homeport.app.data.TrustedPeer(
                                    id = ack.deviceId, name = ack.deviceName, platform = ack.platform,
                                    host = "relay", port = 0, lastSeen = System.currentTimeMillis()
                                )
                                identityManager.addOrUpdateTrustedPeer(peer)
                                _connectedDevice.value = device
                                _connectedDevices.value = listOf(device)
                                _connectionStatus.value = ConnectionStatus.CONNECTED
                                try {
                                    com.homeport.app.util.SoundManager.getInstance(context).playConnect()
                                } catch (_: Exception) {}
                                _statusMessage.value = "Connected to ${ack.deviceName} via global relay 🌐"
                                onResult(true, null)
                            } else {
                                Log.w(TAG, "Relay HELLO_ACK rejected: ${ack.reason}")
                                _connectionStatus.value = ConnectionStatus.FAILED
                                _statusMessage.value = "Connection rejected: ${ack.reason}"
                                onResult(false, "Connection rejected by remote: ${ack.reason}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing relay HELLO_ACK: ${e.message}")
                    }
                }
            }

            // Timeout guard
            delay(20_000)
            if (!connected) {
                val failMsg = "No response from desktop via relay. Make sure HomePort Desktop is running."
                _statusMessage.value = failMsg
                _connectionStatus.value = ConnectionStatus.FAILED
                onResult(false, failMsg)
            }
        }
    }

    fun connect(
        host: String,
        port: Int = 51234,
        pairingSecret: String? = null,
        timeoutMs: Long = 8000L,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        val key = "$host:$port"

        // If a session to THIS exact host:port already exists, recycle ONLY this specific session
        sessions[key]?.let { existing ->
            try {
                existing.timeoutJob?.cancel()
                existing.webSocket.close(1000, "Reconnecting")
            } catch (_: Exception) {}
            sessions.remove(key)
        }

        MeshForegroundService.start(context)

        _connectionStatus.value = ConnectionStatus.CONNECTING
        _statusMessage.value = "Connecting to $host:$port…"

        val request = Request.Builder()
            .url("ws://$host:$port")
            .build()

        var currentSession: PeerSession? = null

        val timeoutJob = scope.launch {
            delay(timeoutMs)
            if (currentSession?.status == ConnectionStatus.CONNECTING) {
                Log.w(TAG, "Connection to $host:$port timed out")
                try {
                    currentSession?.webSocket?.close(1000, "Timeout")
                } catch (_: Exception) {}
                sessions.remove(key)
                updateConnectedState()
                val failMsg = "Cannot reach $host:$port (Timed out). Check IP & port."
                _statusMessage.value = failMsg
                onResult?.invoke(false, failMsg)
            }
        }

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened to $host:$port")
                _statusMessage.value = "Connected to $host. Authenticating…"
                val identity = identityManager.identity
                val hello = HelloMessage(
                    deviceId = identity.id,
                    deviceName = identity.name,
                    platform = identity.platform,
                    pairingSecret = pairingSecret,
                    publicKey = identity.publicKey
                )
                webSocket.send(ProtocolSerializer.toJson(hello))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(webSocket, text, host, port, onResult, currentSession)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Connection failure to $host:$port: ${t.message}")
                currentSession?.timeoutJob?.cancel()
                sessions.remove(key)
                updateConnectedState()
                val msg = t.message ?: "Host unreachable"
                val failMsg = if (msg.contains("failed to connect", true) || msg.contains("refused", true)) {
                    "Cannot reach $host:$port. Check IP address."
                } else {
                    "Connection failed: $msg"
                }
                _statusMessage.value = failMsg
                onResult?.invoke(false, failMsg)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closed to $host:$port: code=$code, reason=$reason")
                currentSession?.timeoutJob?.cancel()
                sessions.remove(key)
                updateConnectedState()
            }
        })

        val session = PeerSession(
            key = key,
            host = host,
            port = port,
            pairingSecret = pairingSecret,
            webSocket = ws,
            timeoutJob = timeoutJob
        )
        currentSession = session
        sessions[key] = session
    }

    private fun updateConnectedState() {
        val connected = sessions.values
            .filter { it.status == ConnectionStatus.CONNECTED }
            .mapNotNull { it.device }
            .distinctBy { it.id }

        _connectedDevices.value = connected
        _connectedDevice.value = connected.lastOrNull()

        val wasConnected = _connectionStatus.value == ConnectionStatus.CONNECTED

        if (connected.isNotEmpty()) {
            _connectionStatus.value = ConnectionStatus.CONNECTED
            if (!wasConnected) {
                try {
                    com.homeport.app.util.SoundManager.getInstance(context).playConnect()
                } catch (_: Exception) {}
            }
        } else if (sessions.values.any { it.status == ConnectionStatus.CONNECTING }) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
        } else {
            if (_connectionStatus.value != ConnectionStatus.DISCONNECTED) {
                cancelAllActiveTransfers()
                if (wasConnected) {
                    try {
                        com.homeport.app.util.SoundManager.getInstance(context).playDisconnect()
                    } catch (_: Exception) {}
                }
            }
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
        MeshForegroundService.syncState(context)
    }

    fun cancelAllActiveTransfers() {
        val current = _activeTransfers.value.toMutableList()
        var changed = false
        for (i in current.indices) {
            val item = current[i]
            if (item.status == TransferStatus.ACTIVE || item.status == TransferStatus.QUEUED) {
                current[i] = item.copy(status = TransferStatus.CANCELLED, error = "Connection lost")
                changed = true
                activeDownloads.remove(item.id)?.deferred?.complete(false)
            }
        }
        if (changed) {
            _activeTransfers.value = current
        }
    }
    
    fun cancelTransfer(id: String) {
        val current = _activeTransfers.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val item = current[idx]
            if (item.status == TransferStatus.ACTIVE || item.status == TransferStatus.QUEUED) {
                current[idx] = item.copy(status = TransferStatus.CANCELLED, error = "Cancelled by user")
                _activeTransfers.value = current
                activeDownloads.remove(id)?.deferred?.complete(false)
            }
        }
    }

    fun disconnect(deviceId: String? = null, userInitiated: Boolean = true) {
        try {
            firebaseWebRtcManager.close()
        } catch (_: Exception) {}
        if (!deviceId.isNullOrEmpty()) {
            val toRemove = sessions.entries.filter { it.value.device?.id == deviceId }
            for (entry in toRemove) {
                try {
                    entry.value.timeoutJob?.cancel()
                    entry.value.webSocket.close(1000, "User disconnect")
                } catch (_: Exception) {}
                sessions.remove(entry.key)
            }
        } else {
            // Disconnect all sessions
            sessions.values.forEach { s ->
                try {
                    s.timeoutJob?.cancel()
                    s.webSocket.close(1000, "User disconnect")
                } catch (_: Exception) {}
            }
            sessions.clear()
        }
        updateConnectedState()
    }

    fun getWebSocketForDevice(deviceId: String? = null): WebSocket? {
        if (!deviceId.isNullOrEmpty()) {
            val session = sessions.values.firstOrNull { it.device?.id == deviceId && it.status == ConnectionStatus.CONNECTED }
            if (session != null) return session.webSocket
        }
        return sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }?.webSocket
    }

    private fun handleIncomingMessage(
        ws: WebSocket,
        raw: String,
        host: String,
        port: Int,
        onResult: ((Boolean, String?) -> Unit)?,
        session: PeerSession?
    ) {
        val type = ProtocolSerializer.parseType(raw) ?: return

        when (type) {
            "HELLO_ACK" -> {
                val ack = ProtocolSerializer.fromJson<HelloAckMessage>(raw)
                session?.timeoutJob?.cancel()
                if (ack.accepted) {
                    val realTotal = ack.storageTotal ?: 998_960_525_312L
                    val realUsed = ack.storageUsed ?: (realTotal - (ack.storageFree ?: 350_000_000_000L))
                    val device = DeviceInfo(
                        id = ack.deviceId,
                        name = ack.deviceName,
                        platform = ack.platform,
                        type = when {
                            ack.platform.contains("Android", true) -> DeviceType.PHONE
                            ack.platform.contains("iOS", true) || ack.platform.contains("iPhone", true) || ack.platform.contains("iPad", true) -> DeviceType.PHONE
                            ack.platform.contains("Windows", true) || ack.platform.contains("Mac", true) || ack.platform.contains("Linux", true) -> DeviceType.LAPTOP
                            else -> DeviceType.COMPUTER
                        },
                        status = DeviceStatus.ONLINE,
                        isTrusted = true,
                        connectionRoute = ConnectionRoute.LOCAL_LAN,
                        storageTotal = realTotal,
                        storageUsed = realUsed,
                        lastSeen = System.currentTimeMillis()
                    )
                    session?.device = device
                    session?.status = ConnectionStatus.CONNECTED
                    updateConnectedState()
                    _statusMessage.value = "Connected to ${device.name}"

                    identityManager.addOrUpdateTrustedPeer(
                        TrustedPeer(
                            id = ack.deviceId,
                            name = ack.deviceName,
                            platform = ack.platform,
                            host = host,
                            port = port
                        )
                    )
                    onResult?.invoke(true, null)
                } else {
                    session?.timeoutJob?.cancel()
                    try { session?.webSocket?.close(1000, "Rejected") } catch (_: Exception) {}
                    val key = "$host:$port"
                    sessions.remove(key)
                    updateConnectedState()
                    val reason = ack.reason ?: "Incorrect pairing code. Check code on computer."
                    // If rejected because revoked, mark locally so we don't auto-reconnect
                    if (reason.contains("revoked", ignoreCase = true) ||
                        reason.contains("Unknown device", ignoreCase = true) ||
                        reason.contains("Invalid", ignoreCase = true)) {
                        identityManager.revokeTrustedPeer(ack.deviceId)
                    }
                    _statusMessage.value = reason
                    onResult?.invoke(false, reason)
                }
            }

            "PING" -> {
                val ping = ProtocolSerializer.fromJson<PingMessage>(raw)
                val pong = PongMessage(seq = ping.seq)
                ws.send(ProtocolSerializer.toJson(pong))
            }

            "GOODBYE" -> {
                session?.timeoutJob?.cancel()
                val key = "$host:$port"
                sessions.remove(key)
                updateConnectedState()
                try { ws.close(1000, "Goodbye received") } catch (_: Exception) {}
                Log.i(TAG, "Server sent GOODBYE for $host:$port")
            }

            "LIST_DIR_RESULT" -> {
                val res = ProtocolSerializer.fromJson<ListDirResultMessage>(raw)
                res.requestId?.let { reqId ->
                    pendingDirRequests.remove(reqId)?.complete(res)
                }
            }

            "SEARCH_RESULT" -> {
                val res = ProtocolSerializer.fromJson<SearchResultMessage>(raw)
                res.requestId?.let { reqId ->
                    pendingSearchRequests.remove(reqId)?.complete(res)
                }
            }

            "DELETE_FILE_RESULT" -> {
                val res = ProtocolSerializer.fromJson<DeleteFileResultMessage>(raw)
                res.requestId?.let { reqId ->
                    pendingDeleteRequests.remove(reqId)?.complete(res)
                } ?: run {
                    val match = pendingDeleteRequests.entries.firstOrNull { it.value.isCompleted.not() }
                    match?.let {
                        pendingDeleteRequests.remove(it.key)?.complete(res)
                    }
                }
            }

            "TRANSFER_ACCEPT" -> {
                val accept = ProtocolSerializer.fromJson<TransferAcceptMessage>(raw)
                val sessionItem = accept.requestId?.let { activeDownloads[it] } ?: activeDownloads[accept.fileId]
                if (sessionItem != null) {
                    activeDownloads[accept.transferId] = sessionItem
                    val oldId = sessionItem.transferId
                    sessionItem.transferId = accept.transferId
                    val current = _activeTransfers.value.toMutableList()
                    val idx = current.indexOfFirst { it.id == oldId }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(id = accept.transferId)
                        _activeTransfers.value = current
                    }
                }
            }

            "TRANSFER_CHUNK" -> {
                val chunk = ProtocolSerializer.fromJson<TransferChunkMessage>(raw)
                val downloadSession = activeDownloads[chunk.transferId]
                if (downloadSession != null) {
                    try {
                        val bytes = Base64.decode(chunk.data, Base64.NO_WRAP)
                        val offset = chunk.index.toLong() * 65536L
                        synchronized(downloadSession.raf) {
                            downloadSession.raf.seek(offset)
                            downloadSession.raf.write(bytes)
                        }
                        downloadSession.receivedChunks++
                        downloadSession.bytesReceived += bytes.size
                        val progress = if (downloadSession.totalSize > 0) {
                            (downloadSession.bytesReceived.toFloat() / downloadSession.totalSize).coerceIn(0f, 1f)
                        } else 0f
                        downloadSession.onProgress?.invoke(progress)
                        updateTransferProgress(downloadSession.transferId, downloadSession.bytesReceived, downloadSession.totalSize)
                    } catch (e: Exception) {
                        downloadSession.deferred.complete(false)
                        activeDownloads.remove(chunk.transferId)
                    }
                }
            }

            "TRANSFER_DONE" -> {
                val done = ProtocolSerializer.fromJson<TransferDoneMessage>(raw)
                val downloadSession = activeDownloads.remove(done.transferId)
                if (downloadSession != null) {
                    activeDownloads.entries.removeAll { it.value === downloadSession }
                    try {
                        downloadSession.raf.fd.sync()
                        downloadSession.raf.close()
                    } catch (_: Exception) {}
                    if (downloadSession.finalDestination != null) {
                        try {
                            if (downloadSession.finalDestination.exists()) {
                                downloadSession.finalDestination.delete()
                            }
                            downloadSession.file.renameTo(downloadSession.finalDestination)
                        } catch (_: Exception) {}
                    }
                    downloadSession.onProgress?.invoke(1f)
                    markTransferCompleted(done.transferId)
                    downloadSession.deferred.complete(true)
                }
            }

            "TRANSFER_ERROR" -> {
                val err = ProtocolSerializer.fromJson<TransferErrorMessage>(raw)
                val downloadSession = activeDownloads.remove(err.transferId)
                if (downloadSession != null) {
                    activeDownloads.entries.removeAll { it.value === downloadSession }
                    try {
                        downloadSession.raf.close()
                        if (downloadSession.finalDestination != null && downloadSession.file.exists()) {
                            downloadSession.file.delete()
                        }
                    } catch (_: Exception) {}
                    markTransferFailed(err.transferId, err.error)
                    downloadSession.deferred.complete(false)
                }
            }

            "LIST_DIR" -> {
                handleIncomingListDir(ws, raw)
            }

            "TRANSFER_REQUEST" -> {
                handleIncomingTransferRequest(ws, raw)
            }

            "DELETE_FILE" -> {
                handleIncomingDeleteFile(ws, raw)
            }
        }
    }

    fun processIncomingResponse(raw: String) {
        val type = ProtocolSerializer.parseType(raw) ?: return
        when (type) {
            "LIST_DIR_RESULT" -> {
                val res = ProtocolSerializer.fromJson<ListDirResultMessage>(raw)
                res.requestId?.let { reqId ->
                    pendingDirRequests.remove(reqId)?.complete(res)
                }
            }
            "SEARCH_RESULT" -> {
                val res = ProtocolSerializer.fromJson<SearchResultMessage>(raw)
                res.requestId?.let { reqId ->
                    pendingSearchRequests.remove(reqId)?.complete(res)
                }
            }
            "DELETE_FILE_RESULT" -> {
                val res = ProtocolSerializer.fromJson<DeleteFileResultMessage>(raw)
                res.requestId?.let { reqId ->
                    pendingDeleteRequests.remove(reqId)?.complete(res)
                } ?: run {
                    val match = pendingDeleteRequests.entries.firstOrNull { it.value.isCompleted.not() }
                    match?.let {
                        pendingDeleteRequests.remove(it.key)?.complete(res)
                    }
                }
            }
            "TRANSFER_ACCEPT" -> {
                val accept = ProtocolSerializer.fromJson<TransferAcceptMessage>(raw)
                val sessionItem = accept.requestId?.let { activeDownloads[it] } ?: activeDownloads[accept.fileId]
                if (sessionItem != null) {
                    activeDownloads[accept.transferId] = sessionItem
                    val current = _activeTransfers.value.toMutableList()
                    val idx = current.indexOfFirst { it.id == sessionItem.transferId }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(id = accept.transferId)
                        _activeTransfers.value = current
                    }
                    sessionItem.transferId = accept.transferId
                }
            }
            "TRANSFER_CHUNK" -> {
                val chunk = ProtocolSerializer.fromJson<TransferChunkMessage>(raw)
                val downloadSession = activeDownloads[chunk.transferId]
                if (downloadSession != null) {
                    try {
                        val bytes = Base64.decode(chunk.data, Base64.NO_WRAP)
                        val offset = chunk.index.toLong() * 65536L
                        synchronized(downloadSession.raf) {
                            downloadSession.raf.seek(offset)
                            downloadSession.raf.write(bytes)
                        }
                        downloadSession.receivedChunks++
                        downloadSession.bytesReceived += bytes.size
                        val progress = if (downloadSession.totalSize > 0) {
                            (downloadSession.bytesReceived.toFloat() / downloadSession.totalSize).coerceIn(0f, 1f)
                        } else 0f
                        downloadSession.onProgress?.invoke(progress)
                        updateTransferProgress(downloadSession.transferId, downloadSession.bytesReceived, downloadSession.totalSize)
                    } catch (e: Exception) {
                        downloadSession.deferred.complete(false)
                        activeDownloads.remove(chunk.transferId)
                    }
                }
            }
            "TRANSFER_DONE" -> {
                val done = ProtocolSerializer.fromJson<TransferDoneMessage>(raw)
                val downloadSession = activeDownloads.remove(done.transferId)
                if (downloadSession != null) {
                    activeDownloads.entries.removeAll { it.value === downloadSession }
                    try {
                        downloadSession.raf.fd.sync()
                        downloadSession.raf.close()
                    } catch (_: Exception) {}
                    if (downloadSession.finalDestination != null) {
                        try {
                            if (downloadSession.finalDestination.exists()) {
                                downloadSession.finalDestination.delete()
                            }
                            downloadSession.file.renameTo(downloadSession.finalDestination)
                        } catch (_: Exception) {}
                    }
                    downloadSession.onProgress?.invoke(1f)
                    markTransferCompleted(done.transferId)
                    downloadSession.deferred.complete(true)
                }
            }
            "TRANSFER_ERROR" -> {
                val err = ProtocolSerializer.fromJson<TransferErrorMessage>(raw)
                val downloadSession = activeDownloads.remove(err.transferId)
                if (downloadSession != null) {
                    activeDownloads.entries.removeAll { it.value === downloadSession }
                    try {
                        downloadSession.raf.close()
                        if (downloadSession.finalDestination != null && downloadSession.file.exists()) {
                            downloadSession.file.delete()
                        }
                    } catch (_: Exception) {}
                    markTransferFailed(err.transferId, err.error)
                    downloadSession.deferred.complete(false)
                }
            }
        }
    }

    private fun handleIncomingListDir(ws: WebSocket, raw: String) {
        val msg = ProtocolSerializer.fromJson<ListDirMessage>(raw)
        val path = msg.path.trim()
        val entries = mutableListOf<FileEntry>()

        if (path.isEmpty() || path == "/" || path.equals("root", ignoreCase = true)) {
            val sharedRoots = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStorageDirectory()
            ).distinctBy { it.absolutePath }

            for (dir in sharedRoots) {
                if (dir.exists()) {
                    entries.add(
                        FileEntry(
                            id = dir.absolutePath,
                            name = if (dir.name == "0") "Internal Storage" else dir.name,
                            isDirectory = true,
                            size = 0L,
                            modifiedAt = dir.lastModified(),
                            extension = ""
                        )
                    )
                }
            }
        } else {
            val targetDir = File(path)
            if (targetDir.exists() && targetDir.isDirectory) {
                val files = targetDir.listFiles()
                if (files == null) {
                    Log.w(TAG, "listFiles() returned null for path: $path (permission denied or I/O error)")
                } else {
                    val sorted = files.sortedWith(
                        compareByDescending<File> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    for (file in sorted) {
                        entries.add(
                            FileEntry(
                                id = file.absolutePath,
                                name = file.name,
                                isDirectory = file.isDirectory,
                                size = if (file.isDirectory) 0L else file.length(),
                                modifiedAt = file.lastModified(),
                                mimeType = null,
                                extension = file.extension
                            )
                        )
                    }
                }
            } else {
                Log.w(TAG, "Path does not exist or is not a directory: $path")
            }
        }

        val res = ListDirResultMessage(
            path = msg.path,
            entries = entries,
            total = entries.size,
            requestId = msg.requestId
        )
        ws.send(ProtocolSerializer.toJson(res))
    }

    private fun handleIncomingTransferRequest(ws: WebSocket, raw: String) {
        val msg = ProtocolSerializer.fromJson<TransferRequestMessage>(raw)
        if (msg.direction == "upload") {
            // Desktop is uploading a file to phone
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dlDir.mkdirs()
            val destFile = File(dlDir, msg.fileName)
            val raf = RandomAccessFile(destFile, "rw")
            raf.setLength(msg.fileSize)
            val chunkSize = if (msg.chunkSize > 0) msg.chunkSize else 65536
            val totalChunks = Math.max(1, Math.ceil(msg.fileSize.toDouble() / chunkSize.toDouble()).toInt())
            val sessionTransferId = "tx_${System.currentTimeMillis()}"

            val downloadSession = DownloadSession(
                transferId = sessionTransferId,
                file = destFile,
                totalSize = msg.fileSize,
                totalChunks = totalChunks,
                raf = raf,
                deferred = CompletableDeferred()
            )
            activeDownloads[sessionTransferId] = downloadSession
            activeDownloads[msg.fileId] = downloadSession

            addActiveTransfer(
                TransferItem(
                    id = sessionTransferId,
                    fileName = msg.fileName,
                    fileSize = msg.fileSize,
                    bytesTransferred = 0,
                    direction = TransferDirection.DOWNLOAD,
                    status = TransferStatus.ACTIVE,
                    speedBps = 0,
                    etaSeconds = null,
                    sourceDeviceId = _connectedDevice.value?.id ?: "desktop",
                    destinationDeviceId = identityManager.identity.id,
                    startedAt = System.currentTimeMillis()
                )
            )

            val accept = TransferAcceptMessage(
                transferId = sessionTransferId,
                fileId = msg.fileId,
                totalChunks = totalChunks,
                requestId = msg.requestId
            )
            ws.send(ProtocolSerializer.toJson(accept))
            return
        }

        // Desktop is downloading a file from phone
        var targetFile = File(msg.fileId)
        if (!targetFile.exists()) {
            val dl = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), msg.fileName)
            if (dl.exists()) targetFile = dl
        }
        if (!targetFile.exists() || !targetFile.isFile) {
            val err = TransferErrorMessage(
                transferId = "err_${System.currentTimeMillis()}",
                error = "File not found on device: ${msg.fileName}",
                requestId = msg.requestId
            )
            ws.send(ProtocolSerializer.toJson(err))
            return
        }

        val fileSize = targetFile.length()
        val chunkSize = if (msg.chunkSize > 0) msg.chunkSize else 65536
        val totalChunks = Math.max(1, Math.ceil(fileSize.toDouble() / chunkSize.toDouble()).toInt())
        val transferId = "xfer_${System.currentTimeMillis()}"

        val accept = TransferAcceptMessage(
            transferId = transferId,
            fileId = msg.fileId,
            totalChunks = totalChunks,
            requestId = msg.requestId
        )
        ws.send(ProtocolSerializer.toJson(accept))

        // Stream file chunks asynchronously
        Thread {
            try {
                val fis = FileInputStream(targetFile)
                val buffer = ByteArray(chunkSize)
                var chunkIndex = 0
                val md = MessageDigest.getInstance("SHA-256")

                while (true) {
                    var totalRead = 0
                    while (totalRead < chunkSize) {
                        val r = fis.read(buffer, totalRead, chunkSize - totalRead)
                        if (r == -1) break
                        totalRead += r
                    }
                    if (totalRead <= 0) break
                    val actualBytes = if (totalRead == chunkSize) buffer else buffer.copyOf(totalRead)
                    md.update(actualBytes)
                    val encoded = Base64.encodeToString(actualBytes, Base64.NO_WRAP)
                    val chunkMsg = TransferChunkMessage(
                        transferId = transferId,
                        index = chunkIndex,
                        data = encoded,
                        checksum = ""
                    )
                    
                    // Implement backpressure
                    while (ws.queueSize() > 256 * 1024L) {
                        Thread.sleep(10)
                    }
                    
                    ws.send(ProtocolSerializer.toJson(chunkMsg))
                    chunkIndex++
                }
                fis.close()

                val checksum = md.digest().joinToString("") { "%02x".format(it) }
                val doneMsg = TransferDoneMessage(
                    transferId = transferId,
                    fileId = msg.fileId,
                    totalBytes = fileSize,
                    checksum = checksum
                )
                ws.send(ProtocolSerializer.toJson(doneMsg))
                Log.i(TAG, "Completed streaming of ${targetFile.name} ($fileSize bytes) to Desktop")
            } catch (e: Exception) {
                Log.e(TAG, "Streaming to Desktop failed: ${e.message}")
                val err = TransferErrorMessage(
                    transferId = transferId,
                    error = e.localizedMessage ?: "File read error"
                )
                ws.send(ProtocolSerializer.toJson(err))
            }
        }.start()
    }

    private fun handleIncomingDeleteFile(ws: WebSocket, raw: String) {
        val msg = ProtocolSerializer.fromJson<DeleteFileMessage>(raw)
        val file = File(msg.fileId)
        val success = if (file.exists()) file.delete() else false
        val res = DeleteFileResultMessage(
            fileId = msg.fileId,
            success = success,
            requestId = msg.requestId
        )
        ws.send(ProtocolSerializer.toJson(res))
    }

    // ── Public Remote RPCs with multi-device targeting ────────────────────────

    suspend fun listDirectory(path: String = "/", deviceId: String? = null): List<FileItem> {
        val session = if (!deviceId.isNullOrEmpty()) {
            sessions.values.firstOrNull { it.device?.id == deviceId && it.status == ConnectionStatus.CONNECTED }
                ?: sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        } else {
            sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        }
        val targetDevice = session?.device ?: _connectedDevice.value

        val reqId = ProtocolSerializer.generateRequestId()
        val deferred = CompletableDeferred<ListDirResultMessage>()
        pendingDirRequests[reqId] = deferred

        val msg = ListDirMessage(path = path, requestId = reqId)
        val json = ProtocolSerializer.toJson(msg)
        
        val sent = if (session?.webSocket != null) {
            session.webSocket.send(json)
        } else if (!deviceId.isNullOrEmpty()) {
            com.homeport.app.network.AndroidMeshServerManager.getInstance(context).sendMessageToClient(deviceId, json)
        } else false
        
        if (!sent) {
            pendingDirRequests.remove(reqId)
            return emptyList()
        }

        return try {
            withTimeout(10000) {
                val result = deferred.await()
                val items = result.entries.map { entry ->
                    FileItem(
                        id = entry.id,
                        name = entry.name,
                        extension = entry.extension.removePrefix("."),
                        path = if (path == "/") entry.name else "$path/${entry.name}",
                        size = entry.size,
                        modifiedAt = entry.modifiedAt,
                        isDirectory = entry.isDirectory,
                        category = if (entry.isDirectory) FileCategory.UNKNOWN
                                   else FileTypeResolver.resolve(entry.extension.removePrefix(".")),
                        isRemote = true,
                        deviceId = targetDevice?.id
                    )
                }
                items.forEach { fileCache[it.id] = it }
                items
            }
        } catch (e: Exception) {
            pendingDirRequests.remove(reqId)
            emptyList()
        }
    }

    suspend fun searchFiles(query: String, deviceId: String? = null): List<FileItem> {
        val session = if (!deviceId.isNullOrEmpty()) {
            sessions.values.firstOrNull { it.device?.id == deviceId && it.status == ConnectionStatus.CONNECTED }
                ?: sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        } else {
            sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        }
        val targetDevice = session?.device ?: _connectedDevice.value

        val reqId = ProtocolSerializer.generateRequestId()
        val deferred = CompletableDeferred<SearchResultMessage>()
        pendingSearchRequests[reqId] = deferred

        val msg = SearchMessage(query = query, requestId = reqId)
        val json = ProtocolSerializer.toJson(msg)
        
        val sent = if (session?.webSocket != null) {
            session.webSocket.send(json)
        } else if (!deviceId.isNullOrEmpty()) {
            com.homeport.app.network.AndroidMeshServerManager.getInstance(context).sendMessageToClient(deviceId, json)
        } else false
        
        if (!sent) {
            pendingSearchRequests.remove(reqId)
            return emptyList()
        }

        return try {
            withTimeout(10000) {
                val result = deferred.await()
                val items = result.results.map { entry ->
                    FileItem(
                        id = entry.id,
                        name = entry.name,
                        extension = entry.extension.removePrefix("."),
                        path = entry.name,
                        size = entry.size,
                        modifiedAt = entry.modifiedAt,
                        isDirectory = entry.isDirectory,
                        category = if (entry.isDirectory) FileCategory.UNKNOWN
                                   else FileTypeResolver.resolve(entry.extension.removePrefix(".")),
                        isRemote = true,
                        deviceId = targetDevice?.id
                    )
                }
                items.forEach { fileCache[it.id] = it }
                items
            }
        } catch (e: Exception) {
            pendingSearchRequests.remove(reqId)
            emptyList()
        }
    }

    suspend fun deleteFile(fileId: String, deviceId: String? = null): Boolean {
        val targetDevId = deviceId ?: fileCache[fileId]?.deviceId
        val session = if (!targetDevId.isNullOrEmpty()) {
            sessions.values.firstOrNull { it.device?.id == targetDevId && it.status == ConnectionStatus.CONNECTED }
                ?: sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        } else {
            sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        }

        val reqId = ProtocolSerializer.generateRequestId()
        val deferred = CompletableDeferred<DeleteFileResultMessage>()
        pendingDeleteRequests[reqId] = deferred

        val msg = DeleteFileMessage(fileId = fileId, requestId = reqId)
        val json = ProtocolSerializer.toJson(msg)
        
        val sent = if (session?.webSocket != null) {
            session.webSocket.send(json)
        } else if (!targetDevId.isNullOrEmpty()) {
            com.homeport.app.network.AndroidMeshServerManager.getInstance(context).sendMessageToClient(targetDevId, json)
        } else false
        
        if (!sent) {
            pendingDeleteRequests.remove(reqId)
            return false
        }

        return try {
            withTimeout(10000) {
                val result = deferred.await()
                if (result.success) {
                    fileCache.remove(fileId)
                }
                result.success
            }
        } catch (e: Exception) {
            pendingDeleteRequests.remove(reqId)
            false
        }
    }

    suspend fun downloadFile(
        fileId: String,
        fileName: String,
        fileSize: Long,
        destinationFile: File,
        onProgress: ((Float) -> Unit)? = null,
        deviceId: String? = null
    ): Boolean {
        val targetDevId = deviceId ?: fileCache[fileId]?.deviceId
        val session = if (!targetDevId.isNullOrEmpty()) {
            sessions.values.firstOrNull { it.device?.id == targetDevId && it.status == ConnectionStatus.CONNECTED }
                ?: sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        } else {
            sessions.values.firstOrNull { it.status == ConnectionStatus.CONNECTED }
        }
        if (session?.webSocket == null && targetDevId.isNullOrEmpty()) return false

        val reqId = ProtocolSerializer.generateRequestId()
        val deferred = CompletableDeferred<Boolean>()

        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.downloading")
        destinationFile.parentFile?.mkdirs()
        if (partFile.exists()) partFile.delete()
        val raf = RandomAccessFile(partFile, "rw")
        raf.setLength(fileSize)

        val totalChunks = Math.max(1, Math.ceil(fileSize.toDouble() / 65536.0).toInt())
        val transferItemId = "dl_${System.currentTimeMillis()}"

        addActiveTransfer(
            TransferItem(
                id = transferItemId,
                fileName = fileName,
                fileSize = fileSize,
                bytesTransferred = 0,
                direction = TransferDirection.DOWNLOAD,
                status = TransferStatus.ACTIVE,
                speedBps = 0,
                sourceDeviceId = targetDevId ?: _connectedDevice.value?.id ?: "remote",
                destinationDeviceId = identityManager.identity.id,
                startedAt = System.currentTimeMillis()
            )
        )

        val msg = TransferRequestMessage(
            fileId = fileId,
            fileName = fileName,
            fileSize = fileSize,
            direction = "download",
            chunkSize = 65536,
            requestId = reqId
        )

        val downloadSession = DownloadSession(
            transferId = transferItemId,
            file = partFile,
            finalDestination = destinationFile,
            totalSize = fileSize,
            totalChunks = totalChunks,
            raf = raf,
            deferred = deferred,
            onProgress = onProgress
        )
        activeDownloads[reqId] = downloadSession

        val json = ProtocolSerializer.toJson(msg)

        val sent = if (session?.webSocket != null) {
            session.webSocket.send(json)
        } else if (!targetDevId.isNullOrEmpty()) {
            com.homeport.app.network.AndroidMeshServerManager.getInstance(context).sendMessageToClient(targetDevId, json)
        } else false
        
        if (!sent) {
            activeDownloads.remove(reqId)
            try { raf.close(); if (partFile.exists()) partFile.delete() } catch (_: Exception) {}
            deferred.complete(false)
            return false
        }
        activeDownloads[transferItemId] = downloadSession
        activeDownloads[fileId] = downloadSession

        return try {
            // Dynamic timeout: minimum 60s, scales with file size assuming ~500KB/s with 2x safety margin
            val timeoutMs = maxOf(60_000L, (fileSize / 500L) * 2L + 30_000L)
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: Exception) {
            try { 
                raf.close()
                if (partFile.exists()) partFile.delete()
            } catch (_: Exception) {}
            activeDownloads.remove(transferItemId)
            activeDownloads.remove(fileId)
            markTransferFailed(transferItemId, e.message ?: "Transfer timed out")
            false
        }
    }

    private fun addActiveTransfer(item: TransferItem) {
        val current = _activeTransfers.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        _activeTransfers.value = current
    }

    private val speedTrackers = java.util.concurrent.ConcurrentHashMap<String, SpeedTracker>()
    private class SpeedTracker(var lastUpdateTime: Long = System.currentTimeMillis(), var lastBytes: Long = 0L)

    private fun updateTransferProgress(id: String, bytes: Long, total: Long) {
        val current = _activeTransfers.value.toMutableList()
        var idx = current.indexOfFirst { it.id == id }
        if (idx < 0) {
            val session = activeDownloads[id]
            if (session != null) {
                idx = current.indexOfFirst { it.id == session.transferId || it.fileName == session.file.name }
            }
        }
        if (idx < 0) {
            idx = current.indexOfFirst { it.status == TransferStatus.ACTIVE }
        }
        if (idx >= 0) {
            val item = current[idx]
            var newSpeed = item.speedBps
            
            val tracker = speedTrackers.getOrPut(item.id) { SpeedTracker(System.currentTimeMillis(), bytes) }
            val now = System.currentTimeMillis()
            val timeDiff = now - tracker.lastUpdateTime
            if (timeDiff >= 400) {
                val bytesDiff = bytes - tracker.lastBytes
                if (bytesDiff > 0 && timeDiff > 0) {
                    newSpeed = (bytesDiff * 1000) / timeDiff
                }
                tracker.lastUpdateTime = now
                tracker.lastBytes = bytes
            }
            
            current[idx] = item.copy(
                bytesTransferred = bytes,
                fileSize = if (total > 0) total else item.fileSize,
                status = TransferStatus.ACTIVE,
                speedBps = newSpeed
            )
            _activeTransfers.value = current
        }
    }

    private fun markTransferCompleted(id: String) {
        val current = _activeTransfers.value.toMutableList()
        var idx = current.indexOfFirst { it.id == id }
        if (idx < 0) {
            val session = activeDownloads[id]
            if (session != null) {
                idx = current.indexOfFirst { it.id == session.transferId || it.fileName == session.file.name }
            }
        }
        if (idx < 0) {
            idx = current.indexOfFirst { it.status == TransferStatus.ACTIVE }
        }
        if (idx >= 0) {
            val item = current[idx]
            current[idx] = item.copy(
                bytesTransferred = item.fileSize,
                status = TransferStatus.COMPLETED,
                completedAt = System.currentTimeMillis()
            )
            _activeTransfers.value = current
        }
    }

    private fun markTransferFailed(id: String, error: String) {
        val current = _activeTransfers.value.toMutableList()
        var idx = current.indexOfFirst { it.id == id }
        if (idx < 0) {
            idx = current.indexOfFirst { it.status == TransferStatus.ACTIVE }
        }
        if (idx >= 0) {
            val item = current[idx]
            current[idx] = item.copy(
                status = TransferStatus.FAILED,
                error = error
            )
            _activeTransfers.value = current
        }
    }

    companion object {
        @Volatile
        private var instance: HomePortClient? = null

        fun getInstance(context: Context): HomePortClient {
            return instance ?: synchronized(this) {
                instance ?: HomePortClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
