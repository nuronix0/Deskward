package com.homeport.app.ui.screens.preview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homeport.app.data.mock.MockDataRepository
import com.homeport.app.domain.model.*
import com.homeport.app.network.HomePortClient
import com.homeport.app.ui.components.*
import com.homeport.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

// ── File Type Classifiers ───────────────────────────────────────────────────

private fun getMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt", "log", "ini", "conf", "env", "properties" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "ts", "jsx", "tsx" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "md", "markdown" -> "text/markdown"
            "kt", "java", "py", "c", "cpp", "h", "cs", "go", "rs", "sh" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
}

private fun isImageFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
}

private fun isPdfFile(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() == "pdf"
}

private fun isTextOrCodeFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in listOf(
        "txt", "md", "markdown", "json", "xml", "html", "htm", "css",
        "js", "ts", "jsx", "tsx", "kt", "kts", "java", "py", "c", "cpp", "h", "hpp",
        "cs", "go", "rs", "sh", "bash", "log", "csv", "yaml", "yml", "ini", "conf",
        "sql", "env", "properties", "gradle", "toml"
    )
}

private fun isVideoFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv")
}

private fun isAudioFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in listOf("mp3", "wav", "m4a", "flac", "ogg", "aac", "wma")
}

private fun isMediaFile(fileName: String): Boolean {
    return isImageFile(fileName) || isVideoFile(fileName) || isAudioFile(fileName)
}

/**
 * Only scan ACTUAL media files (images, audio, video) into MediaStore so
 * archives (.7z, .zip), code files, and PDFs do NOT appear as broken items in Gallery.
 */
private fun scanFileToMediaStore(context: Context, file: File) {
    if (!isMediaFile(file.name)) return
    try {
        val mimeType = getMimeType(file.name)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType)
        ) { _, uri ->
            android.util.Log.i("FilePreview", "Scanned media to MediaStore: $uri")
        }
    } catch (_: Exception) {}
}

/**
 * Open a file with the system's default app via ACTION_VIEW and FileProvider
 */
private fun isValidPdf(file: File): Boolean {
    if (!file.exists() || file.length() < 32L) return false
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            read == 4 && header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Open a file with the system's default app via ACTION_VIEW and FileProvider
 */
private fun openFileWithSystem(context: Context, file: File): Boolean {
    return try {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "File is not downloaded yet", Toast.LENGTH_SHORT).show()
            return false
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val mimeType = getMimeType(file.name)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open ${file.name}").apply {
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resInfoList = context.packageManager.queryIntentActivities(
            chooser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        android.util.Log.e("FilePreview", "Failed to open file: ${e.message}")
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val directIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file.name))
                clipData = android.content.ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(directIntent)
            true
        } catch (ex: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

// ── In-App PDF Preview ──────────────────────────────────────────────────────

@Composable
private fun PdfPreviewCard(
    file: File,
    onOpenSystem: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(file.absolutePath, file.lastModified()) { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember(file.absolutePath, file.lastModified()) { mutableStateOf(0) }
    var currentPage by remember(file.absolutePath) { mutableStateOf(0) }
    var isLoading by remember(file.absolutePath, file.lastModified()) { mutableStateOf(true) }
    var errorMsg by remember(file.absolutePath, file.lastModified()) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.absolutePath, file.lastModified(), currentPage) {
        withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null
            try {
                isLoading = true
                errorMsg = null
                if (!file.exists() || file.length() < 32L) {
                    errorMsg = "File is empty or incomplete"
                    isLoading = false
                    return@withContext
                }

                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                if (pageCount > 0) {
                    val pageIndex = currentPage.coerceIn(0, pageCount - 1)
                    page = renderer.openPage(pageIndex)

                    val targetWidth = 1080.coerceAtMost(page.width * 2).coerceAtLeast(300)
                    val aspect = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * aspect).toInt().coerceIn(150, 1920)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmap = bitmap
                }
                isLoading = false
            } catch (e: Throwable) {
                errorMsg = e.localizedMessage ?: "Could not render PDF preview"
                isLoading = false
            } finally {
                try { page?.close() } catch (_: Exception) {}
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }
        }
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PDF", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (pageCount > 0) "Page ${currentPage + 1} of $pageCount" else "PDF Document",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (pageCount > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.ChevronLeft, "Prev", tint = if (currentPage > 0) SoftEmerald else White40)
                        }
                        IconButton(
                            onClick = { if (currentPage < pageCount - 1) currentPage++ },
                            enabled = currentPage < pageCount - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.ChevronRight, "Next", tint = if (currentPage < pageCount - 1) SoftEmerald else White40)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 340.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = SoftEmerald, modifier = Modifier.size(32.dp))
                } else if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page",
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onOpenSystem),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, tint = Color(0xFFFF5252), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            errorMsg ?: "Tap below to open in external PDF viewer",
                            color = Color(0xFF222222),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onOpenSystem,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SoftEmerald,
                                contentColor = Color(0xFF090A0E)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Open in System Viewer", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF090A0E))
                        }
                    }
                }
            }
        }
    }
}

// ── In-App Text / Code Viewer ───────────────────────────────────────────────

@Composable
private fun TextCodePreviewCard(
    file: File,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var lines by remember(file.absolutePath) { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember(file.absolutePath) { mutableStateOf(true) }
    var isTruncated by remember(file.absolutePath) { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                isLoading = true
                val allLines = mutableListOf<String>()
                file.bufferedReader().useLines { sequence ->
                    for (line in sequence) {
                        allLines.add(line)
                        if (allLines.size >= 800) {
                            isTruncated = true
                            break
                        }
                    }
                }
                lines = allLines
                isLoading = false
            } catch (_: Exception) {
                isLoading = false
            }
        }
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Code, null, tint = VoltGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${file.extension.uppercase()} Preview (${lines.size} lines${if (isTruncated) " - Truncated" else ""})",
                        style = MaterialTheme.typography.bodySmall,
                        color = White90,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = {
                        val text = lines.joinToString("\n")
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, tint = VoltGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall, color = VoltGreen)
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D0F14))
                    .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VoltGreen, modifier = Modifier.size(28.dp))
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Line numbers
                        Column(modifier = Modifier.padding(end = 12.dp)) {
                            lines.indices.forEach { idx ->
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = White40
                                )
                            }
                        }

                        // Content
                        Column(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                        ) {
                            lines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = Color(0xFFECEFF4)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── In-App Video Preview ────────────────────────────────────────────────────

@Composable
private fun VideoPreviewCard(
    file: File,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var durationStr by remember(file.absolutePath) { mutableStateOf("") }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                thumbnail = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val sec = (durationMs / 1000) % 60
                val min = (durationMs / (1000 * 60))
                durationStr = String.format("%02d:%02d", min, sec)
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F1117))
            .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = "Video Thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(VoltGreen)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PlayArrow, "Play Video", tint = Color(0xFF0A0A0C), modifier = Modifier.size(36.dp))
        }

        if (durationStr.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(durationStr, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── In-App Audio Player ─────────────────────────────────────────────────────

@Composable
private fun AudioPlayerCard(
    file: File,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var totalDuration by remember { mutableStateOf(1) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(file.absolutePath) {
        val player = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                totalDuration = duration.coerceAtLeast(1)
                setOnCompletionListener {
                    isPlaying = false
                    currentPos = 0
                }
            } catch (_: Exception) {}
        }
        mediaPlayer = player

        onDispose {
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                try {
                    currentPos = player.currentPosition
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.delay(250)
        }
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VoltGlassLight)
                        .border(0.8.dp, VoltBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.MusicNote, null, tint = VoltGreen, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Audio Track • ${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                IconButton(
                    onClick = {
                        mediaPlayer?.let { player ->
                            try {
                                if (isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.start()
                                    isPlaying = true
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VoltGreen)
                ) {
                    Icon(
                        if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        "Toggle Audio",
                        tint = Color(0xFF0A0A0C),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Slider(
                value = (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac ->
                    mediaPlayer?.let { player ->
                        try {
                            val newPos = (frac * totalDuration).toInt()
                            player.seekTo(newPos)
                            currentPos = newPos
                        } catch (_: Exception) {}
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = VoltGreen,
                    activeTrackColor = VoltGreen,
                    inactiveTrackColor = VoltGreen.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val curSec = (currentPos / 1000) % 60
                val curMin = (currentPos / 60000)
                val totSec = (totalDuration / 1000) % 60
                val totMin = (totalDuration / 60000)
                Text(String.format("%02d:%02d", curMin, curSec), style = MaterialTheme.typography.labelSmall, color = White60)
                Text(String.format("%02d:%02d", totMin, totSec), style = MaterialTheme.typography.labelSmall, color = White60)
            }
        }
    }
}

// ── Fullscreen Image Viewer Modal ───────────────────────────────────────────

@Composable
private fun FullscreenImageViewer(
    file: File,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = file,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Main FilePreviewScreen Composable ───────────────────────────────────────

@Composable
fun FilePreviewScreen(fileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val client = remember { HomePortClient.getInstance(context) }
    val repo = remember { MockDataRepository() }
    val scope = rememberCoroutineScope()
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(-1f) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var localFile by remember { mutableStateOf<File?>(null) }
    var showFullscreenImage by remember { mutableStateOf(false) }

    // Robust file resolution: handles URL encoded ID, ID/name/path lookup, and never produces blank void
    val decodedId = remember(fileId) {
        try { URLDecoder.decode(fileId, "UTF-8") } catch (_: Exception) { fileId }
    }

    val file = remember(fileId, decodedId) {
        client.fileCache[fileId]
            ?: client.fileCache[decodedId]
            ?: client.fileCache.values.firstOrNull {
                it.id == fileId || it.id == decodedId ||
                it.name.equals(fileId, ignoreCase = true) || it.name.equals(decodedId, ignoreCase = true) ||
                it.path == fileId || it.path == decodedId
            }
            ?: repo.recentFiles.find { it.id == fileId || it.id == decodedId }
            ?: repo.searchResults.find { it.id == fileId || it.id == decodedId }
            ?: repo.recentFiles.firstOrNull()
            ?: run {
                val cleanName = decodedId.substringAfterLast('/').substringAfterLast('\\').ifEmpty { "File" }
                val ext = cleanName.substringAfterLast('.', "")
                FileItem(
                    id = decodedId,
                    name = cleanName,
                    extension = ext,
                    path = decodedId,
                    size = 0L,
                    modifiedAt = System.currentTimeMillis(),
                    isDirectory = false,
                    category = FileTypeResolver.resolve(ext),
                    isRemote = false,
                    deviceId = client.connectedDevice.value?.id
                )
            }
    }

    val device = file.deviceId?.let { id -> repo.devices.find { it.id == id } }
        ?: client.connectedDevice.value

    // Auto-detect if file already exists in local storage
    LaunchedEffect(file.name, file.path) {
        val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val homeportDir = File(dlDir, "HOMEPORT")
        val candidate = File(homeportDir, file.name)
        val candidateDirect = File(dlDir, file.name)
        val candidatePath = File(file.path)

        fun isGoodFile(f: File): Boolean {
            if (!f.exists() || f.length() <= 0L || f.name.endsWith(".downloading") || f.name.endsWith(".part")) return false
            if (isPdfFile(f.name)) return isValidPdf(f)
            return true
        }

        if (isGoodFile(candidate)) {
            localFile = candidate
        } else if (isGoodFile(candidateDirect)) {
            localFile = candidateDirect
        } else if (isGoodFile(candidatePath)) {
            localFile = candidatePath
        } else {
            localFile = null
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            Column {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = White90, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable {
                                val existing = localFile
                                if (existing != null && existing.exists()) {
                                    openFileWithSystem(context, existing)
                                } else {
                                    downloadStatus = "File is stored on remote device. Tap Download below."
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open", tint = White60, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 20.dp)
        ) {
            // ── Dynamic In-App Preview Section ─────────────────────────────
            val currentLocalFile = localFile

            if (currentLocalFile != null && isImageFile(file.name)) {
                // Image: Full rich in-app photo preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F1117))
                        .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(16.dp))
                        .clickable { showFullscreenImage = true },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentLocalFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Fullscreen, null, tint = White90, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Zoom", style = MaterialTheme.typography.labelSmall, color = White90)
                        }
                    }
                }
            } else if (currentLocalFile != null && isPdfFile(file.name)) {
                // PDF: Native in-app PDF page renderer
                PdfPreviewCard(
                    file = currentLocalFile,
                    onOpenSystem = { openFileWithSystem(context, currentLocalFile) }
                )
            } else if (currentLocalFile != null && isTextOrCodeFile(file.name)) {
                // Text/Code/MD: In-app scrollable code reader
                TextCodePreviewCard(file = currentLocalFile)
            } else if (currentLocalFile != null && isVideoFile(file.name)) {
                // Video: In-app thumbnail & instant playback
                VideoPreviewCard(
                    file = currentLocalFile,
                    onPlay = { openFileWithSystem(context, currentLocalFile) }
                )
            } else if (currentLocalFile != null && isAudioFile(file.name)) {
                // Audio: In-app audio player
                AudioPlayerCard(file = currentLocalFile)
            } else {
                // Generic / Remote / Archive (7z, zip, docx, etc.): High fidelity icon card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F1117))
                        .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FileTypeIconView(file = file, size = 80.dp)
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SoftEmerald.copy(alpha = 0.15f))
                                .border(0.6.dp, SoftEmerald.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (currentLocalFile != null) "Ready to Open"
                                else "Available on ${device?.name ?: "Remote Peer"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── File Name & Category ───────────────────────────────────────
            Text(
                text = file.name,
                style = MaterialTheme.typography.headlineSmall,
                color = White100,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${file.category.displayName} · ${file.formattedSize}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White60
                )
                if (localFile != null) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SoftEmerald.copy(alpha = 0.15f))
                            .border(0.6.dp, SoftEmerald.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("Downloaded", style = MaterialTheme.typography.labelSmall, color = SoftEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Download Progress Bar ──────────────────────────────────────
            AnimatedVisibility(
                visible = downloadProgress in 0f..0.999f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Downloading file…",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = SoftEmerald
                        )
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = SoftEmerald
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SoftEmerald,
                        trackColor = SoftEmerald.copy(alpha = 0.18f),
                    )
                }
            }

            // ── Status Message Banner (only for completion or failure) ──────
            if (downloadStatus != null && downloadProgress !in 0f..0.999f) {
                Spacer(Modifier.height(14.dp))
                val isErr = downloadStatus!!.startsWith("✗") || downloadStatus!!.contains("failed", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isErr) StatusError.copy(alpha = 0.12f) else SoftEmerald.copy(alpha = 0.12f))
                        .border(
                            0.8.dp,
                            if (isErr) StatusError.copy(alpha = 0.35f) else SoftEmerald.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        downloadStatus!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isErr) StatusError else SoftEmerald
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Primary Action Button (Download or Open) ───────────────────
            val existingLocal = localFile
            if (existingLocal == null || !existingLocal.exists() || existingLocal.length() == 0L) {
                // Not yet downloaded: Show prominent primary Download button
                Button(
                    onClick = {
                        scope.launch {
                            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val homeportDir = File(dlDir, "HOMEPORT").apply { mkdirs() }
                            val dest = File(homeportDir, file.name)
                            downloadStatus = null
                            downloadProgress = 0f

                            DynamicIslandController.updateTransfer(
                                fileName = file.name,
                                progress = 0.01f,
                                bytesTransferred = 0L,
                                totalBytes = file.size,
                                speedFormatted = "Connecting...",
                                isUpload = false,
                                peerName = device?.name ?: "Remote Peer"
                            )

                            val ok = client.downloadFile(
                                fileId = file.id,
                                fileName = file.name,
                                fileSize = file.size,
                                destinationFile = dest,
                                onProgress = { progress ->
                                    downloadProgress = progress
                                    DynamicIslandController.updateTransfer(
                                        fileName = file.name,
                                        progress = progress,
                                        bytesTransferred = (progress * file.size).toLong(),
                                        totalBytes = file.size,
                                        speedFormatted = "Downloading...",
                                        isUpload = false,
                                        peerName = device?.name ?: "Remote Peer"
                                    )
                                }
                            )
                            if (ok) {
                                downloadProgress = 1f
                                localFile = dest
                                scanFileToMediaStore(context, dest)
                                downloadStatus = "✓ Saved to Downloads/HOMEPORT/${file.name}"
                                DynamicIslandController.showSuccess("Downloaded", file.name)
                            } else {
                                downloadProgress = -1f
                                downloadStatus = "✗ Download failed for ${file.name}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SoftEmerald,
                        contentColor = Color(0xFF090A0E)
                    ),
                    enabled = downloadProgress !in 0f..0.999f
                ) {
                    if (downloadProgress in 0f..0.999f) {
                        CircularProgressIndicator(
                            color = Color(0xFF090A0E),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Downloading… ${(downloadProgress * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF090A0E)
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "Download",
                            tint = Color(0xFF090A0E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Download & Preview File",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF090A0E)
                        )
                    }
                }
            } else {
                // Downloaded: Show prominent Open with App button
                Button(
                    onClick = {
                        if (!openFileWithSystem(context, existingLocal)) {
                            downloadStatus = "No application installed to open .${file.extension} files."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SoftEmerald,
                        contentColor = Color(0xFF090A0E)
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open",
                        tint = Color(0xFF090A0E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Open in Default App",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF090A0E)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Quick Actions Row ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Share action
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable {
                                val existing = localFile
                                if (existing != null && existing.exists()) {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", existing
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = getMimeType(file.name)
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            clipData = android.content.ClipData.newRawUri(file.name, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
                                    } catch (e: Exception) {
                                        downloadStatus = "Could not share file."
                                    }
                                } else {
                                    downloadStatus = "Please download the file first."
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Share, "Share", tint = White100, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Share", style = MaterialTheme.typography.labelSmall, color = White60)
                }

                // Copy Path action (clean, replaces duplicate download button)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val clipboard = LocalClipboardManager.current
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable {
                                val pathToCopy = localFile?.absolutePath ?: file.path
                                clipboard.setText(AnnotatedString(pathToCopy))
                                Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ContentCopy, "Copy Path", tint = White100, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Copy Path", style = MaterialTheme.typography.labelSmall, color = White60)
                }

                // Delete action
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete", tint = StatusError, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = StatusError)
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Metadata Card ──────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MetadataRow("Location", file.path)
                    HorizontalDivider(color = SeparatorOpaque.copy(0.3f), modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("Size", file.formattedSize)
                    HorizontalDivider(color = SeparatorOpaque.copy(0.3f), modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("Modified", file.formattedDate)
                    HorizontalDivider(color = SeparatorOpaque.copy(0.3f), modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("Format", if (file.extension.isNotEmpty()) ".${file.extension.uppercase()} (${getMimeType(file.name)})" else "Unknown")
                    if (device != null) {
                        HorizontalDivider(color = SeparatorOpaque.copy(0.3f), modifier = Modifier.padding(vertical = 8.dp))
                        MetadataRow("Device", device.name)
                    }
                    HorizontalDivider(color = SeparatorOpaque.copy(0.3f), modifier = Modifier.padding(vertical = 8.dp))
                    MetadataRow("Local Status", if (localFile != null) "Downloaded on Device" else "Remote Only")
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // Fullscreen Image Dialog
        if (showFullscreenImage && localFile != null) {
            FullscreenImageViewer(file = localFile!!, onDismiss = { showFullscreenImage = false })
        }

        // Delete Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = Color(0xFF161824),
                title = {
                    Text("Delete File", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Are you sure you want to delete \"${file.name}\"? This action cannot be undone.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            scope.launch {
                                downloadStatus = "Deleting ${file.name}…"
                                localFile?.let { try { it.delete() } catch (_: Exception) {} }
                                val ok = client.deleteFile(file.id)
                                if (ok) {
                                    onBack()
                                } else {
                                    downloadStatus = "Failed to delete file"
                                }
                            }
                        }
                    ) {
                        Text("Delete", color = StatusError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
