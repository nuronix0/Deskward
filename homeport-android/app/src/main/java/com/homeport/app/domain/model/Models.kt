package com.homeport.app.domain.model

import androidx.compose.ui.graphics.Color
import com.homeport.app.ui.theme.*

// ── File Category (15 universal categories) ───────────────────────────────
enum class FileCategory(
    val displayName: String,
    val color: Color,
    val emoji: String = ""
) {
    DOCUMENT    ("Document",    ColorDocument),
    IMAGE       ("Image",       ColorImage),
    VIDEO       ("Video",       ColorVideo),
    AUDIO       ("Audio",       ColorAudio),
    ARCHIVE     ("Archive",     ColorArchive),
    CODE        ("Code",        ColorCode),
    DATABASE    ("Database",    ColorDatabase),
    APPLICATION ("Application", ColorApplication),
    DESIGN      ("Design",      ColorDesign),
    MODEL_3D    ("3D / CAD",    ColorModel3D),
    FONT        ("Font",        ColorFont),
    CONFIG      ("Config",      ColorConfig),
    SECURITY    ("Security",    ColorSecurity),
    SYSTEM      ("System",      ColorSystem),
    UNKNOWN     ("File",        ColorUnknown)
}

// ── Device Type (5 universal types) ──────────────────────────────────────
enum class DeviceType(val displayName: String, val emoji: String = "") {
    PHONE       ("Phone"),
    TABLET      ("Tablet"),
    LAPTOP      ("Laptop"),
    DESKTOP     ("Desktop"),
    COMPUTER    ("Computer")
}

// ── Device Status ──────────────────────────────────────────────────────────
enum class DeviceStatus(val displayName: String) {
    ONLINE       ("Online"),
    OFFLINE      ("Offline"),
    CONNECTING   ("Connecting…"),
    RECONNECTING ("Reconnecting…"),
    BUSY         ("Busy")
}

// ── File Model ────────────────────────────────────────────────────────────
data class FileItem(
    val id: String,
    val name: String,
    val extension: String,
    val path: String,
    val size: Long,
    val modifiedAt: Long,
    val isDirectory: Boolean = false,
    val category: FileCategory = FileCategory.UNKNOWN,
    val thumbnailUrl: String? = null,
    val isRemote: Boolean = true,
    val deviceId: String? = null
) {
    val formattedSize: String get() = formatSize(size)
    val formattedDate: String get() = formatRelativeTime(modifiedAt)
}

// ── Device Model ──────────────────────────────────────────────────────────
data class DeviceInfo(
    val id: String,
    val name: String,
    val platform: String,           // "Android 14", "Windows 11"
    val type: DeviceType,
    val status: DeviceStatus,
    val storageTotal: Long = 0L,
    val storageUsed: Long = 0L,
    val lastSeen: Long = 0L,
    val isTrusted: Boolean = false,
    val connectionRoute: ConnectionRoute = ConnectionRoute.NONE,
    val permissions: Set<Permission> = emptySet()
) {
    val storageFree: Long get() = storageTotal - storageUsed
}

// ── Transfer Model ────────────────────────────────────────────────────────
data class TransferItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val speedBps: Long = 0L,
    val etaSeconds: Long? = null,
    val sourceDeviceId: String,
    val destinationDeviceId: String,
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
    val error: String? = null,
    /** Absolute path where the received file was saved (null for uploads) */
    val localFilePath: String? = null
) {
    val progress: Float get() = if (fileSize > 0) bytesTransferred.toFloat() / fileSize else 0f
    val formattedProgress: String get() = "${(progress * 100).toInt()}%"
    val formattedSpeed: String get() = "${formatSize(speedBps)}/s"
    val formattedSize: String get() = "${formatSize(bytesTransferred)} of ${formatSize(fileSize)}"
}

// ── Activity Log Item ─────────────────────────────────────────────────────
data class ActivityEvent(
    val id: String,
    val type: ActivityEventType,
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val deviceId: String? = null,
    val fileId: String? = null
)

// ── Enums ─────────────────────────────────────────────────────────────────
enum class TransferDirection { DOWNLOAD, UPLOAD }

enum class TransferStatus(val displayName: String) {
    ACTIVE      ("Downloading"),
    QUEUED      ("Queued"),
    PAUSED      ("Paused"),
    COMPLETED   ("Completed"),
    FAILED      ("Failed"),
    CANCELLED   ("Cancelled"),
    VERIFYING   ("Verifying…"),
    RESUMING    ("Resuming…")
}

enum class ConnectionRoute(val displayName: String) {
    DIRECT_P2P  ("Direct P2P"),
    RELAY       ("Relay"),
    LOCAL_LAN   ("Local Network"),
    NONE        ("Not connected")
}

enum class Permission(val displayName: String, val description: String) {
    READ        ("Read",     "View and open files"),
    WRITE       ("Write",    "Modify file contents"),
    UPLOAD      ("Upload",   "Send files to this device"),
    DOWNLOAD    ("Download", "Receive files from this device"),
    MOVE        ("Move",     "Move files between folders"),
    COPY        ("Copy",     "Duplicate files"),
    DELETE      ("Delete",   "Permanently remove files"),
    PROCESS     ("Process",  "Run remote processing jobs")
}

enum class ActivityEventType(val displayName: String) {
    TRANSFER_COMPLETE ("Transfer completed"),
    TRANSFER_FAILED   ("Transfer failed"),
    DEVICE_PAIRED     ("Device paired"),
    DEVICE_CONNECTED  ("Device connected"),
    DEVICE_DISCONNECTED("Device disconnected"),
    DEVICE_REVOKED    ("Device revoked"),
    FILE_CREATED      ("File created"),
    FILE_RENAMED      ("File renamed"),
    FILE_DELETED      ("File deleted"),
    PERMISSION_CHANGED("Permissions changed"),
    JOB_COMPLETED     ("Job completed"),
    JOB_FAILED        ("Job failed"),
    SECURITY_ALERT    ("Security alert")
}

// ── Helpers ───────────────────────────────────────────────────────────────
fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1   -> "%.1f GB".format(gb)
        mb >= 1   -> "%.1f MB".format(mb)
        kb >= 1   -> "%.0f KB".format(kb)
        else      -> "$bytes B"
    }
}

fun formatRelativeTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1   -> "Just now"
        minutes < 60  -> "$minutes min ago"
        hours < 24    -> "$hours hour${if (hours > 1) "s" else ""} ago"
        days == 1L    -> "Yesterday"
        days < 7      -> "$days days ago"
        else          -> {
            val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
            fmt.format(java.util.Date(epochMs))
        }
    }
}
