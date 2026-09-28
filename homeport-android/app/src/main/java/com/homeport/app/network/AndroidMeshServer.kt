package com.homeport.app.network

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import android.util.Log
import com.homeport.app.data.DeviceIdentityManager
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.max
import java.io.RandomAccessFile

private const val TAG = "AndroidMeshServer"

/**
 * Generic connection abstraction supporting both direct TCP WebSockets and WebRTC DataChannels.
 */
interface IMeshConnection {
    fun send(text: String): Boolean
    fun close(code: Int = 1000, reason: String = "")
    val remoteAddress: String
    val isBufferFull: Boolean
}

class JavaWebSocketMeshConnection(val ws: WebSocket) : IMeshConnection {
    override fun send(text: String): Boolean {
        return try {
            ws.send(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close(code: Int, reason: String) {
        try { ws.close(code, reason) } catch (_: Exception) {}
    }

    override val remoteAddress: String
        get() = (ws.remoteSocketAddress as? InetSocketAddress)?.hostString ?: ws.remoteSocketAddress?.toString() ?: ""
        
    override val isBufferFull: Boolean
        get() = ws.hasBufferedData()
}

/**
 * Embedded WebSocket server running directly on Android.
 * Enables phone-to-phone direct mesh connectivity:
 * - Direct authentication via HELLO / HELLO_ACK
 * - Directory browsing via LIST_DIR (browsing DCIM, Downloads, Documents, Internal Storage)
 * - P2P file downloads via TRANSFER_REQUEST / TRANSFER_CHUNK / TRANSFER_DONE
 */
class AndroidMeshServer(
    private val context: Context,
    port: Int = 51234
) : WebSocketServer(InetSocketAddress(port)) {

    init {
        isReuseAddr = true
        isTcpNoDelay = true
        connectionLostTimeout = 90
    }

    private val identityManager = DeviceIdentityManager.getInstance(context)
    private val wsMap = java.util.concurrent.ConcurrentHashMap<WebSocket, JavaWebSocketMeshConnection>()
    private val clientMap = java.util.concurrent.ConcurrentHashMap<IMeshConnection, ConnectedClient>()
    private val connectionMap = java.util.concurrent.ConcurrentHashMap<String, IMeshConnection>()
    
    private data class UploadSession(
        val transferId: String,
        val file: File,
        val totalSize: Long,
        val raf: RandomAccessFile,
        var receivedBytes: Long = 0
    )
    private val activeUploads = java.util.concurrent.ConcurrentHashMap<String, UploadSession>()

    override fun onStart() {
        Log.i(TAG, "Android Mesh Server started on port $port")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "New peer connected: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.i(TAG, "Peer disconnected: ${conn.remoteSocketAddress}, code: $code, reason: $reason")
        val meshConn = wsMap.remove(conn) ?: JavaWebSocketMeshConnection(conn)
        val client = clientMap.remove(meshConn)
        if (client != null) {
            if (connectionMap[client.id] == meshConn) {
                connectionMap.remove(client.id)
                AndroidMeshServerManager.getInstance(context).onClientDisconnected(client.id)
            }
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "Server error: ${ex.message}", ex)
        if (conn != null) {
            val meshConn = wsMap.remove(conn) ?: JavaWebSocketMeshConnection(conn)
            val client = clientMap.remove(meshConn)
            if (client != null) {
                if (connectionMap[client.id] == meshConn) {
                    connectionMap.remove(client.id)
                    AndroidMeshServerManager.getInstance(context).onClientDisconnected(client.id)
                }
            }
        }
    }

    fun disconnectPeer(peerId: String) {
        val conn = connectionMap.remove(peerId)
        if (conn != null) {
            clientMap.remove(conn)
            try {
                val goodbye = GoodbyeMessage(reason = "Revoked by host")
                conn.send(ProtocolSerializer.toJson(goodbye))
                conn.close(1000, "Revoked by host")
            } catch (_: Exception) {}
        }
    }

    fun sendMessageToClient(clientId: String, message: String): Boolean {
        val conn = connectionMap[clientId] ?: return false
        return conn.send(message)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val meshConn = wsMap.getOrPut(conn) { JavaWebSocketMeshConnection(conn) }
        processMessage(meshConn, message)
    }

    /**
     * Process an incoming wire protocol message from either a direct WebSocket or a WebRTC DataChannel.
     */
    fun processMessage(conn: IMeshConnection, message: String) {
        try {
            val type = ProtocolSerializer.parseType(message)
            when (type) {
                "HELLO" -> handleHello(conn, message)
                "PING" -> handlePing(conn, message)
                "LIST_DIR" -> handleListDir(conn, message)
                "TRANSFER_REQUEST" -> handleTransferRequest(conn, message)
                "TRANSFER_CHUNK" -> handleTransferChunk(conn, message)
                "TRANSFER_DONE" -> handleTransferDone(conn, message)
                "TRANSFER_ERROR" -> handleTransferError(conn, message)
                "SEARCH" -> handleSearch(conn, message)
                "DELETE_FILE" -> handleDeleteFile(conn, message)
                "CHECK_CODE" -> handleCheckCode(conn, message)
                "LIST_DIR_RESULT", "SEARCH_RESULT", "DELETE_FILE_RESULT",
                "TRANSFER_ACCEPT", "TRANSFER_CHUNK", "TRANSFER_DONE", "TRANSFER_ERROR" -> {
                    HomePortClient.getInstance(context).processIncomingResponse(message)
                }
                else -> Log.d(TAG, "Ignored message type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
        }
    }

    private fun handleCheckCode(conn: IMeshConnection, raw: String) {
        try {
            val req = ProtocolSerializer.fromJson<CheckCodeMessage>(raw)
            val manager = AndroidMeshServerManager.getInstance(context)
            val isValid = manager.peekPairingSecret(req.code)
            val ack = CheckCodeResultMessage(
                valid = isValid,
                deviceId = identityManager.identity.id,
                deviceName = identityManager.identity.name,
                requestId = req.requestId
            )
            conn.send(ProtocolSerializer.toJson(ack))
            Log.i(TAG, "Processed CHECK_CODE for code ${req.code}: valid=$isValid")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling CHECK_CODE: ${e.message}", e)
        }
    }

    private fun handleHello(conn: IMeshConnection, raw: String) {
        val hello = ProtocolSerializer.fromJson<HelloMessage>(raw)
        val myIdentity = identityManager.identity
        val remoteHost = conn.remoteAddress

        // Bug A fix: validate connecting peer — check revocation and trust
        val allPeers = identityManager.getAllPeers()
        val revokedPeer = allPeers.find { it.id == hello.deviceId && it.revoked }
        if (revokedPeer != null) {
            val ack = HelloAckMessage(
                deviceId = myIdentity.id,
                deviceName = myIdentity.name,
                platform = "Android ${Build.VERSION.RELEASE}",
                protocolVersion = PROTOCOL_VERSION,
                publicKey = myIdentity.publicKey,
                accepted = false,
                reason = "Device access revoked."
            )
            conn.send(ProtocolSerializer.toJson(ack))
            try { conn.close(1000, "Revoked") } catch (_: Exception) {}
            Log.w(TAG, "Rejected revoked peer: ${hello.deviceId}")
            return
        }

        val knownPeer = identityManager.getTrustedPeers().find { it.id == hello.deviceId }
        val manager = AndroidMeshServerManager.getInstance(context)

        if (knownPeer == null) {
            // Unknown device — must supply a valid, unexpired pairing secret
            val incomingSecret = hello.pairingSecret
            if (incomingSecret.isNullOrEmpty()) {
                val ack = HelloAckMessage(
                    deviceId = myIdentity.id,
                    deviceName = myIdentity.name,
                    platform = "Android ${Build.VERSION.RELEASE}",
                    protocolVersion = PROTOCOL_VERSION,
                    publicKey = myIdentity.publicKey,
                    accepted = false,
                    reason = "Unknown device. Please scan the QR code or enter the pairing code first."
                )
                conn.send(ProtocolSerializer.toJson(ack))
                try { conn.close(1000, "Unknown") } catch (_: Exception) {}
                Log.w(TAG, "Rejected unknown peer (no secret): ${hello.deviceId}")
                return
            }
            // Validate the one-time pairing secret
            if (!manager.validateAndConsumePairingSecret(incomingSecret)) {
                val ack = HelloAckMessage(
                    deviceId = myIdentity.id,
                    deviceName = myIdentity.name,
                    platform = "Android ${Build.VERSION.RELEASE}",
                    protocolVersion = PROTOCOL_VERSION,
                    publicKey = myIdentity.publicKey,
                    accepted = false,
                    reason = "Invalid or expired pairing code. Please generate a new QR code."
                )
                conn.send(ProtocolSerializer.toJson(ack))
                try { conn.close(1000, "Bad secret") } catch (_: Exception) {}
                Log.w(TAG, "Rejected unknown peer (bad secret): ${hello.deviceId}")
                return
            }
            Log.i(TAG, "New peer authenticated via pairing secret: ${hello.deviceId}")
        }

        // Track connected client session
        val client = ConnectedClient(
            id = hello.deviceId,
            name = hello.deviceName,
            platform = hello.platform,
            socketAddress = conn.remoteAddress
        )
        clientMap[conn] = client
        connectionMap[hello.deviceId] = conn
        AndroidMeshServerManager.getInstance(context).onClientConnected(client)

        // Bug F fix: store port 51234 (Desktop server port) not the ephemeral client port
        identityManager.addOrUpdateTrustedPeer(
            com.homeport.app.data.TrustedPeer(
                id = hello.deviceId,
                name = hello.deviceName,
                platform = hello.platform,
                host = remoteHost,
                port = 51234  // Desktop always listens on 51234
            )
        )

        val (totalStorage, usedStorage, freeStorage) = getDeviceStorage()
        val ack = HelloAckMessage(
            deviceId = myIdentity.id,
            deviceName = myIdentity.name,
            platform = "Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})",
            protocolVersion = PROTOCOL_VERSION,
            publicKey = myIdentity.publicKey,
            accepted = true,
            storageTotal = totalStorage,
            storageUsed = usedStorage,
            storageFree = freeStorage
        )
        conn.send(ProtocolSerializer.toJson(ack))
        Log.i(TAG, "Accepted peer ${hello.deviceName} (${hello.deviceId})")
    }

    private fun handlePing(conn: IMeshConnection, raw: String) {
        val ping = ProtocolSerializer.fromJson<PingMessage>(raw)
        val pong = PongMessage(seq = ping.seq)
        conn.send(ProtocolSerializer.toJson(pong))
    }

    private fun handleListDir(conn: IMeshConnection, raw: String) {
        val msg = ProtocolSerializer.fromJson<ListDirMessage>(raw)
        val path = msg.path.trim()

        val entries = mutableListOf<FileEntry>()

        if (path.isEmpty() || path == "/" || path.equals("root", ignoreCase = true)) {
            // Root directories on Android
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
            // Browse specific folder
            val targetDir = File(path)
            if (targetDir.exists() && targetDir.isDirectory) {
                val files = targetDir.listFiles()
                if (files != null) {
                    // Sort: directories first, then alphabetical
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
            }
        }

        val res = ListDirResultMessage(
            path = msg.path,
            entries = entries,
            total = entries.size,
            requestId = msg.requestId
        )
        conn.send(ProtocolSerializer.toJson(res))
    }

    private fun handleSearch(conn: IMeshConnection, raw: String) {
        val msg = ProtocolSerializer.fromJson<SearchMessage>(raw)
        val query = msg.query.trim().lowercase()
        val results = mutableListOf<FileEntry>()

        if (query.isNotEmpty()) {
            val roots = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            )

            // Bug I fix: use sequence + filter + take to properly limit results
            for (root in roots) {
                if (root.exists() && results.size < 50) {
                    root.walkTopDown().maxDepth(3)
                        .filter { it.name.lowercase().contains(query) }
                        .take(50 - results.size)
                        .forEach { file ->
                            results.add(
                                FileEntry(
                                    id = file.absolutePath,
                                    name = file.name,
                                    isDirectory = file.isDirectory,
                                    size = if (file.isDirectory) 0L else file.length(),
                                    modifiedAt = file.lastModified(),
                                    extension = file.extension
                                )
                            )
                        }
                }
            }
        }

        val res = SearchResultMessage(
            query = msg.query,
            results = results,
            took = 10L,
            requestId = msg.requestId
        )
        conn.send(ProtocolSerializer.toJson(res))
    }

    private fun handleTransferRequest(conn: IMeshConnection, raw: String) {
        val msg = ProtocolSerializer.fromJson<TransferRequestMessage>(raw)

        if (msg.direction == "upload") {
            // Peer is uploading a file to this server
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dlDir.mkdirs()
            val destFile = File(dlDir, msg.fileName)
            try {
                val raf = RandomAccessFile(destFile, "rw")
                raf.setLength(msg.fileSize)
                val chunkSize = if (msg.chunkSize > 0) msg.chunkSize else 65536
                val totalChunks = max(1, ceil(msg.fileSize.toDouble() / chunkSize.toDouble()).toInt())
                val transferId = "tx_${System.currentTimeMillis()}"

                activeUploads[transferId] = UploadSession(
                    transferId = transferId,
                    file = destFile,
                    totalSize = msg.fileSize,
                    raf = raf
                )

                val accept = TransferAcceptMessage(
                    transferId = transferId,
                    fileId = msg.fileId,
                    totalChunks = totalChunks,
                    requestId = msg.requestId
                )
                conn.send(ProtocolSerializer.toJson(accept))
                Log.i(TAG, "Accepted upload request for ${msg.fileName}")
            } catch (e: Exception) {
                val err = TransferErrorMessage(
                    transferId = "err_${System.currentTimeMillis()}",
                    error = "Failed to create file: ${e.message}",
                    requestId = msg.requestId
                )
                conn.send(ProtocolSerializer.toJson(err))
            }
            return
        }

        // Find file by path/ID
        var targetFile = File(msg.fileId)
        if (!targetFile.exists()) {
            // Check Downloads as fallback
            val dl = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), msg.fileName)
            if (dl.exists()) targetFile = dl
        }

        if (!targetFile.exists() || !targetFile.isFile) {
            val err = TransferErrorMessage(
                transferId = "err_${System.currentTimeMillis()}",
                error = "File not found on device: ${msg.fileName}",
                requestId = msg.requestId
            )
            conn.send(ProtocolSerializer.toJson(err))
            return
        }

        val fileSize = targetFile.length()
        val chunkSize = 65536
        val totalChunks = max(1, ceil(fileSize.toDouble() / chunkSize.toDouble()).toInt())
        val transferId = "xfer_${System.currentTimeMillis()}"

        val accept = TransferAcceptMessage(
            transferId = transferId,
            fileId = msg.fileId,
            totalChunks = totalChunks,
            requestId = msg.requestId
        )
        conn.send(ProtocolSerializer.toJson(accept))

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
                    
                    // Implement backpressure: wait if the buffer is full
                    while (conn.isBufferFull) {
                        Thread.sleep(10) // wait for buffer to drain
                    }
                    
                    conn.send(ProtocolSerializer.toJson(chunkMsg))
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
                conn.send(ProtocolSerializer.toJson(doneMsg))
                Log.i(TAG, "Completed transfer of ${targetFile.name} ($fileSize bytes) to peer")
            } catch (e: Exception) {
                Log.e(TAG, "Transfer failed for ${targetFile.name}: ${e.message}")
                val err = TransferErrorMessage(
                    transferId = transferId,
                    error = e.localizedMessage ?: "File read error"
                )
                conn.send(ProtocolSerializer.toJson(err))
            }
        }.start()
    }

    private fun handleTransferChunk(conn: IMeshConnection, raw: String) {
        val chunk = ProtocolSerializer.fromJson<TransferChunkMessage>(raw)
        val session = activeUploads[chunk.transferId]
        if (session != null) {
            try {
                val bytes = Base64.decode(chunk.data, Base64.NO_WRAP)
                val offset = chunk.index.toLong() * 65536L
                synchronized(session.raf) {
                    session.raf.seek(offset)
                    session.raf.write(bytes)
                }
                session.receivedBytes += bytes.size
            } catch (e: Exception) {
                Log.e(TAG, "Error writing chunk: ${e.message}")
                activeUploads.remove(chunk.transferId)
            }
        } else {
            HomePortClient.getInstance(context).processIncomingResponse(raw)
        }
    }

    private fun handleTransferDone(conn: IMeshConnection, raw: String) {
        val done = ProtocolSerializer.fromJson<TransferDoneMessage>(raw)
        val session = activeUploads.remove(done.transferId)
        if (session != null) {
            try {
                session.raf.fd.sync()
                session.raf.close()
            } catch (_: Exception) {}
            Log.i(TAG, "Finished receiving uploaded file: ${session.file.name}")
        } else {
            HomePortClient.getInstance(context).processIncomingResponse(raw)
        }
    }

    private fun handleTransferError(conn: IMeshConnection, raw: String) {
        val err = ProtocolSerializer.fromJson<TransferErrorMessage>(raw)
        val session = activeUploads.remove(err.transferId)
        if (session != null) {
            try { session.raf.close() } catch (_: Exception) {}
            Log.w(TAG, "Upload failed for ${session.file.name}: ${err.error}")
        } else {
            HomePortClient.getInstance(context).processIncomingResponse(raw)
        }
    }

    private fun handleDeleteFile(conn: IMeshConnection, raw: String) {
        val msg = ProtocolSerializer.fromJson<DeleteFileMessage>(raw)
        var success = false
        var error: String? = null
        try {
            val target = File(msg.fileId)
            if (target.exists()) {
                success = if (target.isDirectory) {
                    target.deleteRecursively()
                } else {
                    target.delete()
                }
                if (!success) {
                    error = "Failed to delete file from device storage"
                }
            } else {
                error = "File does not exist"
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Delete error"
        }

        val res = DeleteFileResultMessage(
            fileId = msg.fileId,
            success = success,
            error = error,
            requestId = msg.requestId
        )
        conn.send(ProtocolSerializer.toJson(res))
    }

    private fun getDeviceStorage(): Triple<Long, Long, Long> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = total - free
            Triple(total, used, free)
        } catch (e: Exception) {
            Triple(128_000_000_000L, 45_000_000_000L, 83_000_000_000L)
        }
    }
}
