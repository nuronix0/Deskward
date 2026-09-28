package com.homeport.app.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

const val PROTOCOL_VERSION = "1.0"

// ── Base ──────────────────────────────────────────────────────────────────
interface BaseMessage {
    val type: String
    val requestId: String?
    val timestamp: Long
}

// ── Handshake ─────────────────────────────────────────────────────────────
data class HelloMessage(
    val deviceId: String,
    val deviceName: String,
    val platform: String = "Android",
    val protocolVersion: String = PROTOCOL_VERSION,
    val pairingSecret: String? = null,
    val publicKey: String = "",
    override val type: String = "HELLO",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class HelloAckMessage(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val protocolVersion: String,
    val publicKey: String,
    val accepted: Boolean,
    val reason: String? = null,
    val storageTotal: Long? = null,
    val storageUsed: Long? = null,
    val storageFree: Long? = null,
    override val type: String = "HELLO_ACK",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── Code Verification ─────────────────────────────────────────────────────
data class CheckCodeMessage(
    val code: String,
    override val type: String = "CHECK_CODE",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class CheckCodeResultMessage(
    val valid: Boolean,
    val deviceId: String? = null,
    val deviceName: String? = null,
    override val type: String = "CHECK_CODE_RESULT",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── Heartbeat ─────────────────────────────────────────────────────────────
data class PingMessage(
    val seq: Int,
    override val type: String = "PING",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class PongMessage(
    val seq: Int,
    override val type: String = "PONG",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── Filesystem ────────────────────────────────────────────────────────────
data class ListDirMessage(
    val path: String,
    val recursive: Boolean? = false,
    override val type: String = "LIST_DIR",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class FileEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val mimeType: String? = null,
    val extension: String = ""
)

data class ListDirResultMessage(
    val path: String,
    val entries: List<FileEntry> = emptyList(),
    val total: Int = 0,
    override val type: String = "LIST_DIR_RESULT",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── Search ────────────────────────────────────────────────────────────────
data class SearchMessage(
    val query: String,
    val limit: Int? = 50,
    override val type: String = "SEARCH",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class SearchResultMessage(
    val query: String,
    val results: List<FileEntry> = emptyList(),
    val took: Long = 0,
    override val type: String = "SEARCH_RESULT",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── Transfer ──────────────────────────────────────────────────────────────
data class TransferRequestMessage(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val direction: String, // "download" | "upload"
    val chunkSize: Int = 65536,
    override val type: String = "TRANSFER_REQUEST",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class TransferAcceptMessage(
    val transferId: String,
    val fileId: String,
    val totalChunks: Int,
    override val type: String = "TRANSFER_ACCEPT",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class TransferRejectMessage(
    val transferId: String,
    val reason: String,
    override val type: String = "TRANSFER_REJECT",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class TransferChunkMessage(
    val transferId: String,
    val index: Int,
    val data: String, // Base64
    val checksum: String,
    override val type: String = "TRANSFER_CHUNK",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class TransferDoneMessage(
    val transferId: String,
    val fileId: String? = null,
    val totalBytes: Long? = 0L,
    val checksum: String? = null,
    override val type: String = "TRANSFER_DONE",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class TransferErrorMessage(
    val transferId: String,
    val error: String,
    override val type: String = "TRANSFER_ERROR",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── File Deletion ────────────────────────────────────────────────────────
data class DeleteFileMessage(
    val fileId: String,
    override val type: String = "DELETE_FILE",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class DeleteFileResultMessage(
    val fileId: String,
    val success: Boolean,
    val error: String? = null,
    override val type: String = "DELETE_FILE_RESULT",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── Error & Goodbye ───────────────────────────────────────────────────────
data class ErrorMessage(
    val code: String,
    val message: String,
    override val type: String = "ERROR",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

data class GoodbyeMessage(
    val reason: String? = null,
    override val type: String = "GOODBYE",
    override val requestId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BaseMessage

// ── Serializer ────────────────────────────────────────────────────────────
object ProtocolSerializer {
    val gson: Gson = GsonBuilder().create()

    fun toJson(msg: Any): String = gson.toJson(msg)

    inline fun <reified T> fromJson(json: String): T = gson.fromJson(json, T::class.java)

    fun parseType(json: String): String? {
        return try {
            val el = JsonParser.parseString(json).asJsonObject
            el.get("type")?.asString
        } catch (_: Exception) {
            null
        }
    }

    fun generateRequestId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
}

