package com.homeport.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.homeport.app.domain.model.*
import com.homeport.app.ui.theme.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.homeport.app.R

// ─────────────────────────────────────────────────────────────────────────────
// AtmosphericBackground — Volumetric ambient lighting (mint-teal accent per HOMEPORT logo)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AtmosphericBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // 0. Base — near-black Obsidian (Livora / Obtic dark)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A0A0C),
                    Color(0xFF0D0D0F),
                    Color(0xFF0F0F12)
                )
            )
        )

        // 1. Volt-green soft radial glow — upper-right accent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFC8FF00).copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.88f, size.height * 0.08f),
                radius = size.width * 0.80f
            ),
            radius = size.width * 0.80f,
            center = Offset(size.width * 0.88f, size.height * 0.08f)
        )

        // 2. Carbon depth lift — subtle warm centre
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1A1A1C).copy(alpha = 0.50f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.40f, size.height * 0.30f),
                radius = size.width * 0.95f
            ),
            radius = size.width * 0.95f,
            center = Offset(size.width * 0.40f, size.height * 0.30f)
        )

        // 3. Volt micro-glow — bottom-left corner hint
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFC8FF00).copy(alpha = 0.025f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.05f, size.height * 0.88f),
                radius = size.width * 0.50f
            ),
            radius = size.width * 0.50f,
            center = Offset(size.width * 0.05f, size.height * 0.88f)
        )
    }
}



// ─────────────────────────────────────────────────────────────────────────────
// GlassCard — foundational luxury glassmorphism surface with top-rim light reflection
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    fillAlpha: Float = 0.08f,
    borderAlpha: Float = 0.18f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseMod = modifier
        .clip(shape)
        .background(Carbon)
        .border(
            width = 0.8.dp,
            color = GlassEdgeSubtle,
            shape = shape
        )

    if (onClick != null) {
        Box(modifier = baseMod.clickable(onClick = onClick), content = content)
    } else {
        Box(modifier = baseMod, content = content)
    }
}

fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.94f,
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
        label = "bounce"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

// ─────────────────────────────────────────────────────────────────────────────
// GlassButton — primary action button with specular top rim & ambient glow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    val buttonShape = RoundedCornerShape(16.dp)
    // Primary: Near-white solid surface with black text (Apple-like CTA per spec §18 / Material E)
    // Secondary: Dark smoked glass surface (per spec §19 / Material D)
    val bgModifier = if (isPrimary) {
        Modifier
            .clip(buttonShape)
            .background(Color(0xFFF2F2F4))  // Near-white solid
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White,
                    0.5f to Color.White.copy(0.60f),
                    1.0f to Color.White.copy(0.20f)
                ),
                shape = buttonShape
            )
    } else {
        Modifier
            .clip(buttonShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1C1E).copy(alpha = 0.90f),
                        Color(0xFF111214).copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.18f),
                    0.4f to Color.White.copy(alpha = 0.06f),
                    1.0f to Color.Transparent
                ),
                shape = buttonShape
            )
    }
    // Primary: black text on white. Secondary: white text on dark glass
    val textColor = if (isPrimary) Color(0xFF0A0A0A) else TextPrimary

    Box(
        modifier = modifier
            .height(52.dp)
            .bounceClick(enabled = enabled, onClick = onClick)
            .then(bgModifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GlassSearchBar — sculpted frosted glass capsule with top-rim reflection
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search files, devices, anything…",
    modifier: Modifier = Modifier,
    onSearch: ((String) -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(shape)
            .background(Carbon)
            .border(0.8.dp, GlassEdgeSubtle, shape)
    ) {

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = White60,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = White100),
                cursorBrush = SolidColor(VoltGreen),
                decorationBox = { innerTextField ->
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(color = TextTertiary)
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(TextTertiary)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ConnectionStatusDot — live status indicator
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ConnectionStatusDot(
    status: DeviceStatus,
    size: Dp = 10.dp
) {
    val color = when (status) {
        DeviceStatus.ONLINE       -> StatusOnline
        DeviceStatus.OFFLINE      -> StatusOffline
        DeviceStatus.CONNECTING,
        DeviceStatus.RECONNECTING -> StatusConnecting
        DeviceStatus.BUSY         -> StatusWarning
    }

    val isAnimating = status == DeviceStatus.CONNECTING || status == DeviceStatus.RECONNECTING
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAnimating) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(if (isAnimating) scale else 1f)
            .clip(CircleShape)
            .background(color)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DeviceCard — shows a device with status, storage, and actions
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DeviceCard(
    device: DeviceInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDeleteClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = if (compact) 16.dp else 20.dp,
        onClick = onClick
    ) {
        if (compact) {
            // Compact version for Home screen
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceIconView(device = device, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            color = White100,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = device.platform,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = White40
                        )
                    }
                    ConnectionStatusDot(device.status, size = 9.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Online/status capsule badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (device.status == DeviceStatus.ONLINE) VoltGlassLight
                                else Glass04
                            )
                            .border(
                                0.6.dp,
                                if (device.status == DeviceStatus.ONLINE) VoltBorder
                                else GlassEdgeSubtle,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (device.status == DeviceStatus.ONLINE) VoltGreen else StatusOffline)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = device.status.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = if (device.status == DeviceStatus.ONLINE) VoltGreen else White40
                            )
                        }
                    }

                    if (device.storageTotal > 0) {
                        val freeGb = (device.storageTotal - device.storageUsed) / (1024L * 1024L * 1024L)
                        Text(
                            text = "${freeGb} GB free",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = White60
                        )
                    }
                }
            }
        } else {
            // Full device card for Devices screen
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DeviceIconView(device = device, size = 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = White100
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConnectionStatusDot(device.status, size = 7.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${device.platform} • ${device.status.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (device.status == DeviceStatus.ONLINE) VoltGreen else White40
                            )
                        }
                    }

                    if (onDeleteClick != null) {
                        // Clean native red delete button - NO OVERLAPPING MORE BUTTON
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF241416))
                                .border(0.8.dp, SignalRed.copy(alpha = 0.35f), CircleShape)
                                .clickable(onClick = onDeleteClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove Device",
                                tint = SignalRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Glass08)
                                .border(0.8.dp, GlassEdgeSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = White60,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (device.status == DeviceStatus.ONLINE && device.storageTotal > 0) {
                    Spacer(Modifier.height(16.dp))
                    StorageBar(
                        used = device.storageUsed,
                        total = device.storageTotal
                    )
                } else if (device.status == DeviceStatus.OFFLINE) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Last seen ${formatRelativeTime(device.lastSeen)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = White40
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DeviceIconView — renders device type icon in sleek Obsidian squircle
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DeviceIconView(
    type: DeviceType,
    size: Dp = 44.dp,
    status: DeviceStatus = DeviceStatus.ONLINE,
    platform: String? = null,
    modifier: Modifier = Modifier
) {
    val isOnline = (status == DeviceStatus.ONLINE || status == DeviceStatus.BUSY)
    val iconVector = when (type) {
        DeviceType.PHONE    -> Icons.Outlined.Smartphone
        DeviceType.TABLET   -> Icons.Outlined.TabletMac
        DeviceType.LAPTOP   -> Icons.Outlined.LaptopMac
        DeviceType.DESKTOP,
        DeviceType.COMPUTER -> Icons.Outlined.DesktopWindows
    }
    val cornerRadius = size * 0.28f
    val shape = RoundedCornerShape(cornerRadius)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (isOnline) VoltGlassLight else Glass04)
                .border(
                    width = 0.8.dp,
                    color = if (isOnline) VoltBorder else GlassEdgeSubtle,
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = type.displayName,
                tint = if (isOnline) VoltGreen else White40,
                modifier = Modifier.size(size * 0.52f)
            )

            // Status pip if online
            if (isOnline && size >= 40.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VoltGreen)
                )
            }
        }
    }
}


@Composable
fun DeviceIconView(
    device: DeviceInfo,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) = DeviceIconView(
    type = device.type,
    size = size,
    status = device.status,
    platform = device.platform,
    modifier = modifier
)

// ─────────────────────────────────────────────────────────────────────────────
// FileVisualSpec & Resolver — bespoke individual visual identity per file type
// ─────────────────────────────────────────────────────────────────────────────
data class FileVisualSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color,
    val badgeLabel: String? = null
)

fun resolveFileVisual(file: FileItem): FileVisualSpec {
    if (file.isDirectory) {
        return FileVisualSpec(
            icon = Icons.Outlined.Folder,
            accentColor = Color(0xFFF59E0B), // Warm amber gold
            badgeLabel = null
        )
    }

    val ext = file.extension.lowercase().removePrefix(".")
    return when (ext) {
        // Video formats (mp4, mkv, mov, avi, webm, etc.)
        "mp4", "mkv", "mov", "avi", "webm", "flv", "m4v", "3gp", "ts" -> FileVisualSpec(
            icon = Icons.Outlined.PlayCircle,
            accentColor = Color(0xFFA855F7), // Vivid electric violet
            badgeLabel = ext.uppercase().take(4)
        )
        // Image formats (jpg, png, webp, svg, heic, gif, etc.)
        "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "heic", "raw", "ico" -> FileVisualSpec(
            icon = Icons.Outlined.Image,
            accentColor = Color(0xFF06B6D4), // Sky cyan
            badgeLabel = ext.uppercase().take(4)
        )
        // Archive formats (zip, rar, 7z, tar, gz, etc.)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab" -> FileVisualSpec(
            icon = Icons.Outlined.FolderZip,
            accentColor = Color(0xFFF97316), // Electric amber/orange
            badgeLabel = ext.uppercase().take(3)
        )
        // Markdown (.md)
        "md", "markdown" -> FileVisualSpec(
            icon = Icons.Outlined.Article,
            accentColor = Color(0xFF2DD4BF), // Mint teal
            badgeLabel = "MD"
        )
        // PDF
        "pdf" -> FileVisualSpec(
            icon = Icons.Outlined.PictureAsPdf,
            accentColor = Color(0xFFEF4444), // Crimson red
            badgeLabel = "PDF"
        )
        // Spreadsheets / Data
        "xlsx", "xls", "csv", "tsv", "ods", "numbers" -> FileVisualSpec(
            icon = Icons.Outlined.TableChart,
            accentColor = Color(0xFFC1F800), // Volt lime
            badgeLabel = ext.uppercase().take(3)
        )
        // Documents & Text
        "doc", "docx", "txt", "rtf", "odt", "pages", "log" -> FileVisualSpec(
            icon = Icons.Outlined.Description,
            accentColor = Color(0xFF3B82F6), // Royal blue
            badgeLabel = ext.uppercase().take(4)
        )
        "ppt", "pptx", "key", "odp" -> FileVisualSpec(
            icon = Icons.Outlined.Description,
            accentColor = Color(0xFFFB923C), // Warm orange
            badgeLabel = ext.uppercase().take(3)
        )
        // Audio formats (mp3, wav, flac, aac, etc.)
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma" -> FileVisualSpec(
            icon = Icons.Outlined.GraphicEq,
            accentColor = Color(0xFFEC4899), // Rose fuchsia
            badgeLabel = ext.uppercase().take(4)
        )
        // Code files
        "kt", "kts", "py", "js", "ts", "jsx", "tsx", "html", "htm", "css", "scss",
        "java", "c", "cpp", "h", "hpp", "cs", "go", "rs", "swift", "php", "rb",
        "sh", "bash", "json", "xml", "yaml", "yml", "sql", "gradle", "toml" -> FileVisualSpec(
            icon = Icons.Outlined.Terminal,
            accentColor = VoltGreen, // Volt green
            badgeLabel = ext.uppercase().take(4)
        )
        // Applications & Installers
        "apk", "aab", "xapk" -> FileVisualSpec(
            icon = Icons.Outlined.Android,
            accentColor = VoltGreen,
            badgeLabel = "APK"
        )
        "exe", "msi", "dmg", "pkg", "deb", "rpm", "app" -> FileVisualSpec(
            icon = Icons.Outlined.Widgets,
            accentColor = Color(0xFF818CF8), // Indigo
            badgeLabel = ext.uppercase().take(3)
        )
        // Design files
        "psd", "ai", "fig", "xd", "sketch", "blend" -> FileVisualSpec(
            icon = Icons.Outlined.Palette,
            accentColor = Color(0xFFF43F5E), // Rose
            badgeLabel = ext.uppercase().take(3)
        )
        // 3D Models
        "obj", "fbx", "gltf", "glb", "stl" -> FileVisualSpec(
            icon = Icons.Outlined.ViewInAr,
            accentColor = Color(0xFFA78BFA), // Lavender
            badgeLabel = ext.uppercase().take(3)
        )
        // Database
        "db", "sqlite", "sqlite3", "realm" -> FileVisualSpec(
            icon = Icons.Outlined.Storage,
            accentColor = Color(0xFF14B8A6), // Teal
            badgeLabel = "DB"
        )
        // Fallback by Category
        else -> when (file.category) {
            FileCategory.VIDEO       -> FileVisualSpec(Icons.Outlined.PlayCircle, Color(0xFFA855F7), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.IMAGE       -> FileVisualSpec(Icons.Outlined.Image, Color(0xFF06B6D4), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.AUDIO       -> FileVisualSpec(Icons.Outlined.GraphicEq, Color(0xFFEC4899), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.ARCHIVE     -> FileVisualSpec(Icons.Outlined.FolderZip, Color(0xFFF97316), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.CODE        -> FileVisualSpec(Icons.Outlined.Terminal, VoltGreen, ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.DOCUMENT    -> FileVisualSpec(Icons.Outlined.Description, Color(0xFF3B82F6), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.APPLICATION -> FileVisualSpec(Icons.Outlined.Widgets, Color(0xFF818CF8), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.DATABASE    -> FileVisualSpec(Icons.Outlined.Storage, Color(0xFF14B8A6), "DB")
            FileCategory.DESIGN      -> FileVisualSpec(Icons.Outlined.Palette, Color(0xFFF43F5E), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.MODEL_3D    -> FileVisualSpec(Icons.Outlined.ViewInAr, Color(0xFFA78BFA), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.FONT        -> FileVisualSpec(Icons.Outlined.TextFields, Color(0xFFD97706), "FONT")
            FileCategory.SECURITY    -> FileVisualSpec(Icons.Outlined.VpnKey, Color(0xFFEAB308), "KEY")
            FileCategory.CONFIG      -> FileVisualSpec(Icons.Outlined.Tune, Color(0xFF94A3B8), ext.takeIf { it.isNotEmpty() }?.uppercase()?.take(3))
            FileCategory.SYSTEM      -> FileVisualSpec(Icons.Outlined.Memory, Color(0xFF64748B), "SYS")
            else                     -> FileVisualSpec(Icons.Outlined.InsertDriveFile, Color(0xFF94A3B8), ext.takeIf { it.length in 2..4 }?.uppercase())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FileTypeIconView — Ultra-premium Obsidian badge with individual file visual identity
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FileTypeIconView(
    file: FileItem,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    if (file.isDirectory) {
        FolderIconView(size = size, modifier = modifier)
        return
    }

    val spec = remember(file.name, file.isDirectory, file.category) {
        resolveFileVisual(file)
    }

    val cornerRadius = size * 0.28f
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Ultra-clean Obsidian surface with 12% category tint and crisp hairline border (NO CAUSTIC BLOOM)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            spec.accentColor.copy(alpha = 0.14f),
                            Color(0xFF141518)
                        )
                    )
                )
                .border(
                    width = 0.8.dp,
                    color = spec.accentColor.copy(alpha = 0.24f),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = file.name,
                tint = spec.accentColor,
                modifier = Modifier.size(size * 0.50f)
            )
        }

        // Precision laser-etched extension micro-badge (bottom-right)
        if (spec.badgeLabel != null && size >= 38.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0C0D10))
                    .border(
                        width = 0.6.dp,
                        color = spec.accentColor.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 3.5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = spec.badgeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = (size.value * 0.17f).coerceIn(7.5f, 9.5f).sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = spec.accentColor
                    )
                )
            }
        }
    }
}

@Composable
fun FolderIconView(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val cornerRadius = size * 0.28f
    val shape = RoundedCornerShape(cornerRadius)
    val folderColor = Color(0xFFF59E0B) // Warm amber gold

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            folderColor.copy(alpha = 0.14f),
                            Color(0xFF141518)
                        )
                    )
                )
                .border(
                    width = 0.8.dp,
                    color = folderColor.copy(alpha = 0.24f),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = "Folder",
                tint = folderColor,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FileRow — luxury obsidian file row matching HomeScreen aesthetic
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FileRow(
    file: FileItem,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isSelected) VoltGlassLight else Carbon
            )
            .border(
                width = 0.8.dp,
                color = if (isSelected) VoltBorder else GlassEdgeSubtle,
                shape = shape
            )
            .bounceClick(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileTypeIconView(file = file, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = White100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (file.isDirectory) "Folder" else file.category.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = if (file.isDirectory) Color(0xFFF59E0B) else White40
                    )
                    if (!file.isDirectory) {
                        Text(
                            text = "  •  ",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = White20
                        )
                        Text(
                            text = file.formattedSize,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = White60
                        )
                    }
                    Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = White20
                    )
                    Text(
                        text = file.formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = White40
                    )
                }
            }
            if (onMoreClick != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMoreClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "File actions",
                        tint = White40,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TransferCard — shows transfer progress
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TransferCard(
    transfer: TransferItem,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        // Directional top-rim specular hairline
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.92f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // File icon (small)
                val dummyFile = FileItem(
                    id = transfer.id, name = transfer.fileName,
                    extension = transfer.fileName.substringAfterLast('.', ""),
                    path = "", size = transfer.fileSize, modifiedAt = transfer.startedAt,
                    category = FileTypeResolver.resolve(transfer.fileName), isRemote = true,
                    isDirectory = !transfer.fileName.contains('.')
                )
                FileTypeIconView(file = dummyFile, size = 42.dp)
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.01).sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = transfer.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Status indicator / action
                when (transfer.status) {
                    TransferStatus.ACTIVE -> {
                        Row {
                            if (onPause != null) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E2132).copy(alpha = 0.70f))
                                        .border(0.6.dp, Color.White.copy(0.12f), CircleShape)
                                        .clickable(onClick = onPause),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Pause, "Pause",
                                        tint = TextSecondary, modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    TransferStatus.PAUSED -> {
                        if (onResume != null) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(SoftEmerald.copy(alpha = 0.15f))
                                    .border(0.8.dp, SoftEmerald.copy(0.45f), CircleShape)
                                    .clickable(onClick = onResume),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow, "Resume",
                                    tint = SoftEmerald, modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    TransferStatus.QUEUED -> {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2132).copy(alpha = 0.70f))
                                .border(0.6.dp, Color.White.copy(0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Schedule, "Queued",
                                tint = TextSecondary, modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    TransferStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF30D158).copy(alpha = 0.16f))
                                .border(0.8.dp, Color(0xFF30D158).copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check, "Done",
                                tint = Color(0xFF30D158), modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    TransferStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF453A).copy(alpha = 0.16f))
                                .border(0.8.dp, Color(0xFFFF453A).copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Error, "Failed",
                                tint = Color(0xFFFF453A), modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Progress bar for active/paused
            if (transfer.status == TransferStatus.ACTIVE ||
                transfer.status == TransferStatus.PAUSED ||
                transfer.status == TransferStatus.VERIFYING
            ) {
                Spacer(Modifier.height(14.dp))
                val animatedProgress by animateFloatAsState(
                    targetValue = transfer.progress,
                    animationSpec = tween(300),
                    label = "progress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141617))  // Graphite progress track
                        .border(0.6.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        SoftEmerald.copy(alpha = 0.85f),
                                        SoftEmerald
                                    )
                                )
                            )
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transfer.formattedProgress,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = SoftEmerald
                    )
                    if (transfer.status == TransferStatus.ACTIVE && transfer.speedBps > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = transfer.formattedSpeed,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            transfer.etaSeconds?.let { eta ->
                                Text(" · ", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                                Text(
                                    text = formatEta(eta),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

// ─────────────────────────────────────────────────────────────────────────────
// StorageBar — compact horizontal storage usage bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StorageBar(
    used: Long,
    total: Long,
    modifier: Modifier = Modifier
) {
    val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val barGradient = when {
        fraction > 0.9f -> listOf(Color(0xFFFF453A), Color(0xFFFF9F0A))
        fraction > 0.75f -> listOf(Color(0xFFFF9F0A), Color(0xFFFFD60A))
        else -> listOf(VoltGreen, VoltGreen)
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatSize(used) + " used",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = White60
            )
            Text(
                text = formatSize(total - used) + " free",
                style = MaterialTheme.typography.labelSmall,
                color = White40
            )
        }
        Spacer(Modifier.height(6.dp))
        val barShape = CircleShape
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(barShape)
                .background(Color(0xFF16181B))
                .border(0.6.dp, GlassEdgeSubtle, barShape)
        ) {
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(600),
                label = "storage"
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(barShape)
                    .background(
                        brush = Brush.horizontalGradient(barGradient)
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StorageRing — circular animated storage ring for dashboard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StorageRing(
    used: Long,
    total: Long,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val color = when {
        fraction > 0.9f -> StatusError
        fraction > 0.75f -> StatusWarning
        else -> VoltGreen
    }
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "ring"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.toPx() * 0.1f
            val radius = (size.toPx() - strokeWidth) / 2f
            // Track
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            // Fill
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f)),
                    center = Offset(size.toPx() / 2, size.toPx() / 2)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                color = White100,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "used",
                style = MaterialTheme.typography.labelSmall,
                color = White60
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SectionHeader — section title with optional "See All"
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = White100,
            fontWeight = FontWeight.SemiBold
        )
        if (onSeeAll != null) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = VoltGreen,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BasicTextField (import helper alias)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    cursorBrush: Brush = SolidColor(VoltGreen),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() }
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        decorationBox = decorationBox
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// HeroStatusCard — Cinematic Mesh Network Status Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HeroStatusCard(
    deviceCount: Int,
    activeTransferCount: Int,
    networkSpeedText: String,
    onPairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val cardShape = RoundedCornerShape(26.dp)
    val cardFill = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF191B1D).copy(alpha = 0.92f),
            Color(0xFF111315).copy(alpha = 0.96f),
            Color(0xFF0A0B0C).copy(alpha = 0.99f)
        )
    )
    val cardBorder = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.20f),
        0.30f to Color.White.copy(alpha = 0.08f),
        1.0f to Color.White.copy(alpha = 0.02f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardFill)
            .border(width = 0.8.dp, brush = cardBorder, shape = cardShape)
    ) {
        // Directional top-rim specular hairline
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.92f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Restrained Soft Emerald ambient warmth — top-right
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.TopEnd)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7DD6B0).copy(alpha = glowAlpha * 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.25f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.75f, size.height * 0.25f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            // Header: Status Pill & Encryption Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pillShape = RoundedCornerShape(999.dp)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF0E2018).copy(alpha = 0.90f),
                                    Color(0xFF0B1812).copy(alpha = 0.95f)
                                )
                            )
                        )
                        .border(
                            0.8.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    SoftEmerald.copy(alpha = 0.45f),
                                    SoftEmerald.copy(alpha = 0.20f)
                                )
                            ),
                            pillShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (deviceCount > 0) SoftEmerald else StatusWarning)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (deviceCount > 0) "MESH ACTIVE" else "MESH READY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.1.sp,
                                fontSize = 10.sp,
                                color = SoftEmerald
                            )
                        )
                    }
                }

                Text(
                    text = "E2E ENCRYPTED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // Title & Subtitle with high-contrast spacious typography
            Text(
                text = "Cross-Device Mesh",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    fontSize = 24.sp,
                    color = TextPrimary
                )
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (deviceCount > 0) 
                    "$deviceCount connected peer${if (deviceCount > 1) "s" else ""} · Direct zero-cloud sync"
                else 
                    "Direct zero-cloud sync · Ready to pair devices",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    lineHeight = 20.sp,
                    fontSize = 14.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(22.dp))

            // Single unified row: Essential metrics on left + Pair Device CTA on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text(
                            text = if (activeTransferCount > 0) "$activeTransferCount active" else "Idle",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (activeTransferCount > 0) Color(0xFF30D158) else TextSecondary
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Transfers",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 11.sp)
                        )
                    }

                    Column {
                        Text(
                            text = networkSpeedText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SoftEmerald
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Speed",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextTertiary, fontSize = 11.sp)
                        )
                    }
                }

                // CTA button inline with metrics
                val btnShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .clip(btnShape)
                        .background(Color(0xFFF0F0F2))
                        .border(0.8.dp, Color.White, btnShape)
                        .clickable(onClick = onPairClick)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF0A0A0A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Pair Device",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A0A0A)
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BentoStatTile — 2x2 Bento Stat Tile for Dashboard (with 3D Icon Wells & Caustics)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BentoStatTile(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = HomePortBlue,
    custom3dIconRes: Int? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val cardFill = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E2132).copy(alpha = 0.58f),
            Color(0xFF10121D).copy(alpha = 0.72f)
        )
    )
    val cardBorder = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.28f),
        0.35f to Color.White.copy(alpha = 0.08f),
        1.0f to Color.White.copy(alpha = 0.02f)
    )

    val baseModifier = modifier
        .clip(shape)
        .background(cardFill)
        .border(0.8.dp, cardBorder, shape)

    val finalModifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier

    Box(modifier = finalModifier) {
        // Directional top-rim specular hairline
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.88f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3D Inset Icon Well (directly matching Reference Images 1 & 6)
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Ambient colored glow halo blooming behind
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        iconTint.copy(alpha = 0.40f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    val wellShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(wellShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF222638).copy(alpha = 0.85f),
                                        Color(0xFF10121E).copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .border(
                                width = 0.8.dp,
                                brush = Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = 0.45f),
                                    0.4f to Color.White.copy(alpha = 0.12f),
                                    1.0f to Color.Transparent
                                ),
                                shape = wellShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Caustic light pool along bottom floor of the well
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(0.85f)
                                .height(6.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            iconTint.copy(alpha = 0.60f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        if (IconConfig.USE_CUSTOM_3D_ICONS && custom3dIconRes != null) {
                            Image(
                                painter = painterResource(id = custom3dIconRes),
                                contentDescription = title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextTertiary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        fontSize = 22.sp,
                        color = TextPrimary
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

