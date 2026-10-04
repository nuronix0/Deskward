package com.homeport.app.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MeshServerManager"

class WebRtcMeshConnection(val adapter: WebRtcWebSocketAdapter) : IMeshConnection {
    override fun send(text: String): Boolean = adapter.send(text)
    override fun close(code: Int, reason: String) { adapter.close(code, reason) }
    override val remoteAddress: String = "webrtc"
    override val isBufferFull: Boolean
        get() = adapter.queueSize() > 256 * 1024 // 256 KB threshold
}

data class ConnectedClient(
    val id: String,
    val name: String,
    val platform: String,
    val socketAddress: String,
    val connectedAt: Long = System.currentTimeMillis()
)

class AndroidMeshServerManager private constructor(private val context: Context) {

    private var server: AndroidMeshServer? = null
    private val _serverPort = MutableStateFlow<Int?>(null)
    val serverPort: StateFlow<Int?> = _serverPort.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _incomingClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val incomingClients: StateFlow<List<ConnectedClient>> = _incomingClients.asStateFlow()

    // ---- Pairing secret for incoming connections (phone-to-phone or desktop-scans-phone) ----
    @Volatile private var activePairingSecret: String? = null
    @Volatile private var pairingSecretExpiry: Long = 0L
    private val TTL_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Generate a fresh 6-character alphanumeric pairing secret valid for 5 minutes.
     * Used by the "My QR" mode and "Show Code" flow.
     */
    fun generatePairingSecret(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // unambiguous charset
        val rng = SecureRandom()
        val secret = (1..6).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        activePairingSecret = secret
        pairingSecretExpiry = System.currentTimeMillis() + TTL_MS
        Log.i(TAG, "Generated pairing secret: $secret")
        return secret
    }

    /**
     * Validate an incoming pairing secret. Returns true if it matches the active one and hasn't expired.
     * Invalidates the secret on first successful use (one-time use).
     */
    fun validateAndConsumePairingSecret(incoming: String): Boolean {
        val active = activePairingSecret ?: return false
        val now = System.currentTimeMillis()
        if (now > pairingSecretExpiry) {
            activePairingSecret = null
            Log.w(TAG, "Pairing secret expired")
            return false
        }
        val match = incoming.trim().uppercase() == active.trim().uppercase()
        if (match) {
            activePairingSecret = null // consume — one-time use
            Log.i(TAG, "Pairing secret accepted and consumed")
        } else {
            Log.w(TAG, "Pairing secret mismatch: expected=$active, got=$incoming")
        }
        return match
    }

    /**
     * Check if an incoming pairing secret matches the active one without consuming it.
     * Used by subnet scanning / code discovery probes.
     */
    fun peekPairingSecret(incoming: String): Boolean {
        val active = activePairingSecret ?: return false
        val now = System.currentTimeMillis()
        if (now > pairingSecretExpiry) return false
        return incoming.trim().uppercase() == active.trim().uppercase()
    }

    fun getActivePairingSecret(): String? = if (System.currentTimeMillis() < pairingSecretExpiry) activePairingSecret else null

    /**
     * Inject an externally-generated one-time pairing secret (e.g. from NFC tap flow).
     * Valid for the same TTL as a generated secret.
     */
    fun setActivePairingSecret(secret: String) {
        activePairingSecret = secret.trim().uppercase()
        pairingSecretExpiry = System.currentTimeMillis() + TTL_MS
        Log.i(TAG, "External pairing secret registered (NFC flow)")
    }

    /**
     * Returns this device's best local LAN IPv4 address derived from the running server's
     * network interface, or null if the server isn't running yet.
     */
    fun getServerIp(): String? {
        return if (_isRunning.value) {
            NetworkUtils.getBestLocalIpv4(context)
        } else null
    }

    fun onClientConnected(client: ConnectedClient) {
        val current = _incomingClients.value.toMutableList()
        current.removeAll { it.id == client.id }
        current.add(0, client)
        _incomingClients.value = current
        Log.i(TAG, "Client registered in manager: ${client.name} (${client.id})")
        MeshForegroundService.start(context)
        try {
            com.homeport.app.util.SoundManager.getInstance(context).playConnect()
        } catch (_: Exception) {}
    }

    fun onClientDisconnected(clientId: String) {
        val current = _incomingClients.value.toMutableList()
        val removed = current.removeAll { it.id == clientId }
        if (removed) {
            _incomingClients.value = current
            Log.i(TAG, "Client removed from manager: $clientId")
            MeshForegroundService.syncState(context)
            try {
                com.homeport.app.util.SoundManager.getInstance(context).playDisconnect()
            } catch (_: Exception) {}
        }
    }

    fun disconnectClient(clientId: String) {
        server?.disconnectPeer(clientId)
        onClientDisconnected(clientId)
    }

    fun sendMessageToClient(clientId: String, message: String): Boolean {
        return server?.sendMessageToClient(clientId, message) ?: false
    }

    fun startServer(): Int {
        if (_isRunning.value && server != null) {
            MeshForegroundService.start(context)
            return _serverPort.value ?: 51234
        }

        // Try preferred port 51234 first, or fall back to an available ephemeral port
        val port = findAvailablePort(51234)
        try {
            server = AndroidMeshServer(context, port).apply {
                start()
            }
            _serverPort.value = port
            _isRunning.value = true
            MeshForegroundService.start(context)
            Log.i(TAG, "Android Mesh Server started successfully on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Android Mesh Server on port $port: ${e.message}", e)
        }
        return _serverPort.value ?: 51234
    }

    private val activeWebRtcRooms = ConcurrentHashMap.newKeySet<String>()
    private val meshConnMap = ConcurrentHashMap<WebRtcWebSocketAdapter, WebRtcMeshConnection>()

    /**
     * Start listening for incoming WebRTC peer connections via Firebase signaling on the given roomKey.
     * roomKey can be a pairing secret or device ID.
     */
    fun listenOnFirebase(roomKey: String) {
        val cleanKey = roomKey.trim().uppercase()
        if (cleanKey.isBlank() || activeWebRtcRooms.contains(cleanKey)) return
        activeWebRtcRooms.add(cleanKey)

        startServer()
        val webrtcManager = FirebaseWebRtcManager.getInstance(context)

        webrtcManager.listenForOffer(
            roomKey = cleanKey,
            onChannelOpen = { adapter ->
                Log.i(TAG, "WebRTC DataChannel connected on room $cleanKey")
                val meshConn = WebRtcMeshConnection(adapter)
                meshConnMap[adapter] = meshConn
            },
            onMessage = { message, adapter ->
                val meshConn = meshConnMap.getOrPut(adapter) { WebRtcMeshConnection(adapter) }
                server?.processMessage(meshConn, message)
            },
            onError = { err ->
                Log.w(TAG, "WebRTC listener error for room $cleanKey: $err")
                activeWebRtcRooms.remove(cleanKey)
            }
        )
        Log.i(TAG, "Registered Firebase WebRTC listener for room: $cleanKey")
    }

    fun stopServer() {
        try {
            server?.stop()
            server = null
            _isRunning.value = false
            _serverPort.value = null
            _incomingClients.value = emptyList()
            MeshForegroundService.syncState(context)
            Log.i(TAG, "Android Mesh Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Android Mesh Server: ${e.message}", e)
        }
    }

    private fun findAvailablePort(preferredPort: Int): Int {
        return try {
            val socket = ServerSocket(preferredPort)
            val port = socket.localPort
            socket.close()
            port
        } catch (e: Exception) {
            // Pick an ephemeral port
            try {
                val socket = ServerSocket(0)
                val port = socket.localPort
                socket.close()
                port
            } catch (ex: Exception) {
                preferredPort
            }
        }
    }

    companion object {
        @Volatile
        private var instance: AndroidMeshServerManager? = null

        fun getInstance(context: Context): AndroidMeshServerManager {
            return instance ?: synchronized(this) {
                instance ?: AndroidMeshServerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
