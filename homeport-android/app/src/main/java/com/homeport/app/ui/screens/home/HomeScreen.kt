package com.homeport.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.homeport.app.data.DeviceIdentityManager
import com.homeport.app.data.mock.MockDataRepository
import com.homeport.app.domain.model.*
import com.homeport.app.network.HomePortClient
import com.homeport.app.network.AndroidMeshServerManager
import com.homeport.app.network.ConnectedClient
import com.homeport.app.ui.screens.devices.RevokeDeviceDialog
import com.homeport.app.ui.theme.*
import java.util.Calendar
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — Obsidian + Volt-Green, Livora/Obtic inspired
// Structure: Greeting → Big Status Pill → Wide horizontal stat strip →
//            Devices horizontal row → Recent files vertical list
// No bento grid. No overused cards. Bold, minimal, premium.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    onDeviceClick: (String) -> Unit,
    onSeeAllDevices: () -> Unit,
    onSeeAllTransfers: () -> Unit,
    onFileClick: (String) -> Unit,
    onBrowseFiles: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onScanQrClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues(),
    bottomBarHeight: androidx.compose.ui.unit.Dp = 100.dp
) {
    val context = LocalContext.current
    val client = remember { HomePortClient.getInstance(context) }
    val livePeer by client.connectedDevice.collectAsState()
    val livePeers by client.connectedDevices.collectAsState()
    val meshServerManager = remember { AndroidMeshServerManager.getInstance(context) }
    val incomingClients by meshServerManager.incomingClients.collectAsState()
    val identityManager = remember { DeviceIdentityManager.getInstance(context) }
    val repo = remember { MockDataRepository() }
    val liveTransfers by client.activeTransfers.collectAsState()

    val allDevices = remember(livePeer, livePeers, incomingClients, repo.devices) {
        val list = mutableListOf<DeviceInfo>()

        // 1. Live outgoing peers (this phone browsing peer)
        for (peer in livePeers) {
            if (list.none { it.id == peer.id }) {
                list.add(peer)
            }
        }

        // 2. Incoming connected clients (Sender mode: remote devices accessing this phone's storage)
        for (inc in incomingClients) {
            if (list.none { it.id == inc.id }) {
                list.add(
                    DeviceInfo(
                        id = inc.id,
                        name = inc.name,
                        platform = inc.platform,
                        type = if (inc.platform.contains("Windows", true) || inc.platform.contains("mac", true)) DeviceType.DESKTOP else DeviceType.PHONE,
                        status = DeviceStatus.ONLINE,
                        storageTotal = 0L,
                        storageUsed = 0L,
                        lastSeen = inc.connectedAt,
                        isTrusted = true,
                        connectionRoute = ConnectionRoute.DIRECT_P2P,
                        permissions = setOf(Permission.READ, Permission.DOWNLOAD, Permission.UPLOAD)
                    )
                )
            }
        }

        // 3. Trusted peers from storage
        val trustedPeers = identityManager.getTrustedPeers()
        for (tp in trustedPeers) {
            if (list.none { it.id == tp.id }) {
                list.add(
                    DeviceInfo(
                        id = tp.id,
                        name = tp.name,
                        platform = tp.platform,
                        type = if (tp.platform.contains("Windows", true) || tp.platform.contains("mac", true)) DeviceType.DESKTOP else DeviceType.PHONE,
                        status = DeviceStatus.OFFLINE,
                        storageTotal = 0L,
                        storageUsed = 0L,
                        lastSeen = tp.lastSeen,
                        isTrusted = true,
                        connectionRoute = ConnectionRoute.NONE,
                        permissions = setOf(Permission.READ, Permission.DOWNLOAD, Permission.UPLOAD)
                    )
                )
            }
        }

        for (d in repo.devices) {
            if (list.none { it.id == d.id }) {
                list.add(d)
            }
        }
        list
    }

    val activeTransferCount: Int = liveTransfers.count { it.status == TransferStatus.ACTIVE }
    val onlinePeerCount = allDevices.count { it.status == DeviceStatus.ONLINE }
    val isConnected = onlinePeerCount > 0

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = bottomBarHeight + 24.dp
            )
        ) {

            // ── Top Bar ────────────────────────────────────────────────────
            item {
                HomeTopBar(
                    activeTransferCount = activeTransferCount,
                    onProfileClick = onProfileClick,
                    onSearchClick = onSearchClick,
                    onScanQrClick = onScanQrClick,
                    onNotificationsClick = onNotificationsClick
                )
            }

            // ── Big Hero Status (Unified Mesh State & Topology Visualizer) ──
            item {
                Spacer(Modifier.height(8.dp))
                HeroStatusSection(
                    isConnected = isConnected,
                    onlinePeers = onlinePeerCount,
                    activePeer = livePeer,
                    incomingClients = incomingClients,
                    onConnectClick = onSeeAllDevices,
                    onDisconnectClient = { clientId ->
                        meshServerManager.disconnectClient(clientId)
                        identityManager.removeTrustedPeer(clientId)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

            // ── Stat Strip — horizontal scrollable ─────────────────────────
            item {
                StatStrip(
                    devices = allDevices,
                    transfers = liveTransfers,
                    onDevicesClick = onSeeAllDevices,
                    onTransfersClick = onSeeAllTransfers
                )
                Spacer(Modifier.height(32.dp))
            }

            // ── Devices Section ────────────────────────────────────────────
            item {
                SectionLabel(
                    title = "Devices",
                    action = if (allDevices.isNotEmpty()) "All" else null,
                    onAction = onSeeAllDevices,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(14.dp))
            }

            if (allDevices.isEmpty()) {
                item {
                    EmptyDevicesCard(
                        onPairClick = onScanQrClick,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(32.dp))
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allDevices) { device ->
                            VoltDeviceCard(
                                device = device,
                                onClick = { onDeviceClick(device.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }

            // ── Recent Files ───────────────────────────────────────────────
            item {
                SectionLabel(
                    title = "Recent Files",
                    action = if (repo.recentFiles.isNotEmpty()) "Browse" else null,
                    onAction = { onBrowseFiles() },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            if (repo.recentFiles.isEmpty()) {
                item {
                    EmptyFilesCard(modifier = Modifier.padding(horizontal = 20.dp))
                }
            } else {
                items(repo.recentFiles.take(5)) { file ->
                    FileRow(
                        file = file,
                        onClick = {
                            client.fileCache[file.id] = file
                            client.fileCache[file.name] = file
                            onFileClick(file.id)
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                    )
                }
            }

            // ── Active Transfers (if any) ──────────────────────────────────
            val actives = liveTransfers.filter { it.status == TransferStatus.ACTIVE }
            if (actives.isNotEmpty()) {
                item { Spacer(Modifier.height(32.dp)) }
                item {
                    SectionLabel(
                        title = "Transferring",
                        action = "See All",
                        onAction = onSeeAllTransfers,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                items(actives.take(2)) { transfer ->
                    ActiveTransferRow(
                        transfer = transfer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 5.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HomeTopBar — Ultra minimal. Greeting left, icon pills right.
// ─────────────────────────────────────────────────────────────────────────────
// HomeTopBar — Symmetrical & Conflict-Free:
// Left: [Profile Shortcut] [Search] | Center: [Dynamic Island slot] | Right: [QR Scanner] [Notifications]
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeTopBar(
    activeTransferCount: Int,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left pair: Profile shortcut + Search
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile symbol / shortcut
            TopIconButton(
                icon = Icons.Outlined.Person,
                tint = White90,
                background = Glass08,
                onClick = onProfileClick
            )

            // Search icon button
            TopIconButton(
                icon = Icons.Outlined.Search,
                tint = White60,
                background = Glass08,
                onClick = onSearchClick
            )
        }

        // Center spacer — keeps the center clear for Dynamic Island popups
        Spacer(Modifier.weight(1f))

        // Right pair: QR scan + Notifications
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // QR scan — volt accent
            TopIconButton(
                icon = Icons.Outlined.QrCodeScanner,
                tint = VoltGreen,
                background = VoltGlassLight,
                onClick = onScanQrClick
            )

            // Notifications — with badge
            Box {
                TopIconButton(
                    icon = Icons.Outlined.Notifications,
                    tint = White60,
                    background = Glass08,
                    onClick = onNotificationsClick
                )
                if (activeTransferCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VoltGreen)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color = Glass08,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(0.8.dp, GlassEdge, RoundedCornerShape(12.dp))
            .clickable {
                try {
                    com.homeport.app.util.SoundManager.getInstance(context).playTap()
                } catch (e: Exception) {
                    // Ignore sound error
                }
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HeroStatusSection — The single most important widget. Big bold typography.
// Full-width card with volt-green glow when connected. Like Livora's big hero.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeroStatusSection(
    isConnected: Boolean,
    onlinePeers: Int,
    activePeer: DeviceInfo?,
    incomingClients: List<ConnectedClient> = emptyList(),
    onConnectClick: () -> Unit,
    onDisconnectClient: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSender = incomingClients.isNotEmpty()
    val isReceiver = activePeer != null && isConnected
    val effectiveConnected = isConnected || isSender
    val effectivePeers = if (isConnected) onlinePeers else incomingClients.size

    val cardShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF15161A),
                        Color(0xFF0F1013)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(0.12f),
                        Color.White.copy(0.04f)
                    )
                ),
                shape = cardShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            // Status badge pill
            ConnStatusPill(
                isConnected = effectiveConnected,
                isSender = isSender,
                isReceiver = isReceiver
            )

            Spacer(Modifier.height(16.dp))

            // Big bold counter + High-Tech Topology Visualizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (effectiveConnected) "$effectivePeers" else "0",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 66.sp,
                            letterSpacing = (-3).sp,
                            lineHeight = 62.sp
                        ),
                        color = if (effectiveConnected) White100 else White20
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (effectiveConnected) {
                            when {
                                isSender && isReceiver -> "dual mesh active"
                                isSender -> if (incomingClients.size == 1) "receiver connected" else "receivers in mesh"
                                else -> if (effectivePeers == 1) "device in mesh" else "devices in mesh"
                            }
                        } else {
                            "no mesh active"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp
                        ),
                        color = White60
                    )
                }

                // Dynamic eye-loving mesh topology visualizer
                MeshTopologyVisualizer(
                    isConnected = effectiveConnected,
                    isSender = isSender,
                    isReceiver = isReceiver,
                    receiverCount = incomingClients.size,
                    modifier = Modifier.size(116.dp, 96.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Bottom: peer info + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isSender && isReceiver -> {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dual-Role Mesh",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = White90,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Serving ${incomingClients.size} · Browsing ${activePeer?.name ?: "Peer"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = White40,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFFD4F938))
                                .clickable(onClick = onConnectClick)
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Manage",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF0C0D10)
                            )
                        }
                    }
                    isSender -> {
                        val firstClient = incomingClients.firstOrNull()
                        val clientName = firstClient?.name ?: "Receiver Phone"
                        val clientIp = firstClient?.socketAddress?.removePrefix("/") ?: "Local P2P"
                        val count = incomingClients.size

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (count == 1) clientName else "$count Receivers Connected",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = White90,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (count == 1) "P2P · $clientIp" else "Active Data Mesh",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = White40,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (count == 1 && firstClient != null) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1517))
                                        .border(0.7.dp, Color(0xFFFF453A).copy(alpha = 0.30f), CircleShape)
                                        .clickable { onDisconnectClient(firstClient.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Disconnect",
                                        tint = Color(0xFFFF6961),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color(0xFFD4F938))
                                    .clickable(onClick = onConnectClick)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Manage",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFF0C0D10)
                                )
                            }
                        }
                    }
                    activePeer != null -> {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activePeer.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = White90,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Receiver Mode · ${activePeer.platform}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = White40,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFFD4F938))
                                .clickable(onClick = onConnectClick)
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Manage",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF0C0D10)
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "Scan QR to link devices",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = White40,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White)
                                .clickable(onClick = onConnectClick)
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Connect →",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF0A0A0C)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnStatusPill(
    isConnected: Boolean,
    isSender: Boolean = false,
    isReceiver: Boolean = false
) {
    val voltColor = Color(0xFFD4F938)
    val pillBg = Color(0xFF16181D)
    val pillBorder = Color.White.copy(alpha = 0.08f)
    val dotColor = if (isConnected) voltColor else Steel
    val textColor = if (isConnected) White90 else White40

    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (isConnected) 1.3f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot_scale"
    )

    val labelText = when {
        !isConnected -> "○ OFFLINE"
        isSender && isReceiver -> "● DUAL MESH ACTIVE"
        isSender -> "● SENDER · SERVING STORAGE"
        else -> "● MESH ACTIVE"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(pillBg)
            .border(0.7.dp, pillBorder, RoundedCornerShape(99.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp * dotScale)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.1.sp
                ),
                color = textColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MeshTopologyVisualizer — High-tech dynamic network topology visualizer.
// Supports:
// 1. Idle/Offline: Precision aerospace radar scan
// 2. 1 Receiver: Fluid harmonic sine-wave laser stream + photon packets
// 3. 2+ Receivers: Star constellation distribution mesh with phase-offset pulses
// 4. Dual-Role: Simultaneous Cyan Inbound stream AND Volt Lime Outbound streams
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MeshTopologyVisualizer(
    isConnected: Boolean,
    isSender: Boolean,
    isReceiver: Boolean,
    receiverCount: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_topology")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val breath by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val voltColor = Color(0xFFD4F938)
    val cyanColor = Color(0xFF38BDF8)
    val neutralTrack = Color.White.copy(alpha = 0.08f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        if (!isConnected && !isSender) {
            // SCENARIO 0: OFFLINE / STANDBY RADAR
            val maxR = minOf(w, h) / 2f - 6.dp.toPx()

            drawCircle(
                color = neutralTrack,
                radius = maxR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = maxR * 0.55f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )

            // Scanning sweep ray
            val sweepAngle = phase * 360f
            val rad = Math.toRadians(sweepAngle.toDouble())
            val sweepX = cx + (maxR * kotlin.math.cos(rad)).toFloat()
            val sweepY = cy + (maxR * kotlin.math.sin(rad)).toFloat()
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(0.25f), Color.Transparent),
                    start = Offset(cx, cy),
                    end = Offset(sweepX, sweepY)
                ),
                start = Offset(cx, cy),
                end = Offset(sweepX, sweepY),
                strokeWidth = 1.5.dp.toPx()
            )

            // Center standby dot
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        } else if (isSender && isReceiver) {
            // SCENARIO 3: DUAL-ROLE MESH (Simultaneous Sender + Receiver)
            // Cyan stream flowing IN from Host C (top-left) -> Center
            // Volt stream flowing OUT from Center -> Receiver Phones (right)
            val hostX = cx - 36.dp.toPx()
            val hostY = cy - 20.dp.toPx()
            val center = Offset(cx, cy)
            val rx1 = cx + 36.dp.toPx()
            val ry1 = cy - 14.dp.toPx()
            val rx2 = cx + 34.dp.toPx()
            val ry2 = cy + 22.dp.toPx()

            // 1. Inbound stream from Host C (Top-Left) -> Center in Cyan
            drawLine(
                color = cyanColor.copy(alpha = 0.20f),
                start = Offset(hostX, hostY),
                end = center,
                strokeWidth = 1.2.dp.toPx()
            )
            val inT = phase
            val inPx = hostX + (cx - hostX) * inT
            val inPy = hostY + (cy - hostY) * inT
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cyanColor, Color.Transparent),
                    center = Offset(inPx, inPy),
                    radius = 8.dp.toPx()
                ),
                radius = 7.dp.toPx(),
                center = Offset(inPx, inPy)
            )
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(inPx, inPy))

            // 2. Outbound streams from Center -> Receivers in Volt
            val receivers = listOf(Offset(rx1, ry1), Offset(rx2, ry2))
            receivers.forEachIndexed { i, rPos ->
                drawLine(
                    color = voltColor.copy(alpha = 0.20f),
                    start = center,
                    end = rPos,
                    strokeWidth = 1.2.dp.toPx()
                )
                val outT = (phase + i * 0.5f) % 1f
                val outPx = cx + (rPos.x - cx) * outT
                val outPy = cy + (rPos.y - cy) * outT
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(voltColor, Color.Transparent),
                        center = Offset(outPx, outPy),
                        radius = 8.dp.toPx()
                    ),
                    radius = 7.dp.toPx(),
                    center = Offset(outPx, outPy)
                )
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(outPx, outPy))

                // Receiver satellite node
                drawCircle(color = Color(0xFF181A20), radius = 6.dp.toPx(), center = rPos)
                drawCircle(color = voltColor.copy(0.6f), radius = 6.dp.toPx(), center = rPos, style = Stroke(1.2.dp.toPx()))
                drawCircle(color = voltColor, radius = 2.dp.toPx(), center = rPos)
            }

            // Remote host node (cyan)
            drawCircle(color = Color(0xFF181A20), radius = 6.dp.toPx(), center = Offset(hostX, hostY))
            drawCircle(color = cyanColor.copy(0.7f), radius = 6.dp.toPx(), center = Offset(hostX, hostY), style = Stroke(1.2.dp.toPx()))
            drawCircle(color = cyanColor, radius = 2.dp.toPx(), center = Offset(hostX, hostY))

            // Dual-Core Center Node (This Device)
            drawCircle(
                color = voltColor.copy(alpha = 0.25f * breath),
                radius = 15.dp.toPx() * breath,
                center = center,
                style = Stroke(1.dp.toPx())
            )
            drawCircle(
                color = cyanColor.copy(alpha = 0.35f),
                radius = 10.dp.toPx(),
                center = center,
                style = Stroke(1.dp.toPx())
            )
            drawCircle(color = Color(0xFF141519), radius = 7.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = center)

        } else if (isSender && receiverCount > 1) {
            // SCENARIO 2: SENDER with MULTIPLE RECEIVERS (2+ Peers)
            val center = Offset(cx - 18.dp.toPx(), cy)
            val count = minOf(receiverCount, 3)
            val angles = when (count) {
                2 -> listOf(-30f, 30f)
                else -> listOf(-42f, 0f, 42f)
            }
            val orbitR = 48.dp.toPx()

            // Subtle orbital guide arc
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(center.x - orbitR, center.y - orbitR),
                size = androidx.compose.ui.geometry.Size(orbitR * 2, orbitR * 2),
                style = Stroke(1.dp.toPx())
            )

            // Center Host Node
            drawCircle(
                color = voltColor.copy(alpha = 0.20f * breath),
                radius = 14.dp.toPx() * breath,
                center = center,
                style = Stroke(1.dp.toPx())
            )
            drawCircle(color = Color(0xFF141519), radius = 8.dp.toPx(), center = center)
            drawCircle(color = voltColor, radius = 8.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = center)

            angles.forEachIndexed { i, angleDeg ->
                val rad = Math.toRadians(angleDeg.toDouble())
                val satX = center.x + (orbitR * kotlin.math.cos(rad)).toFloat()
                val satY = center.y + (orbitR * kotlin.math.sin(rad)).toFloat()
                val satPos = Offset(satX, satY)

                drawLine(
                    color = voltColor.copy(alpha = 0.18f),
                    start = center,
                    end = satPos,
                    strokeWidth = 1.2.dp.toPx()
                )

                val t = (phase + i.toFloat() / count) % 1f
                val px = center.x + (satX - center.x) * t
                val py = center.y + (satY - center.y) * t

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(voltColor, Color.Transparent),
                        center = Offset(px, py),
                        radius = 8.dp.toPx()
                    ),
                    radius = 7.dp.toPx(),
                    center = Offset(px, py)
                )
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(px, py))

                drawCircle(color = Color(0xFF181A20), radius = 6.dp.toPx(), center = satPos)
                drawCircle(color = voltColor.copy(0.7f), radius = 6.dp.toPx(), center = satPos, style = Stroke(1.2.dp.toPx()))
                drawCircle(color = voltColor, radius = 2.dp.toPx(), center = satPos)
            }

        } else {
            // SCENARIO 1: SENDER with 1 RECEIVER (or 1 Connected Peer)
            val leftNode = Offset(cx - 32.dp.toPx(), cy)
            val rightNode = Offset(cx + 32.dp.toPx(), cy)

            drawLine(
                color = voltColor.copy(alpha = 0.16f),
                start = leftNode,
                end = rightNode,
                strokeWidth = 1.5.dp.toPx()
            )

            // Continuous sine-wave harmonic laser oscillation
            val waveSteps = 30
            val wavePath = androidx.compose.ui.graphics.Path()
            wavePath.moveTo(leftNode.x, leftNode.y)
            for (step in 1..waveSteps) {
                val t = step.toFloat() / waveSteps
                val x = leftNode.x + (rightNode.x - leftNode.x) * t
                val envelope = kotlin.math.sin(t * Math.PI).toFloat()
                val y = cy + kotlin.math.sin((t * 4f - phase * 2f) * Math.PI.toFloat()) * (4.5.dp.toPx() * envelope)
                wavePath.lineTo(x, y)
            }
            drawPath(
                path = wavePath,
                color = voltColor.copy(alpha = 0.50f),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // High-speed photon packets gliding along the path
            val packetCount = 2
            for (k in 0 until packetCount) {
                val pt = (phase + k * 0.5f) % 1f
                val px = leftNode.x + (rightNode.x - leftNode.x) * pt
                val envelope = kotlin.math.sin(pt * Math.PI).toFloat()
                val py = cy + kotlin.math.sin((pt * 4f - phase * 2f) * Math.PI.toFloat()) * (4.5.dp.toPx() * envelope)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(voltColor, Color.Transparent),
                        center = Offset(px, py),
                        radius = 8.dp.toPx()
                    ),
                    radius = 7.dp.toPx(),
                    center = Offset(px, py)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.2.dp.toPx(),
                    center = Offset(px, py)
                )
            }

            // Left Node (Host Vault)
            drawCircle(
                color = voltColor.copy(alpha = 0.20f * breath),
                radius = 13.dp.toPx() * breath,
                center = leftNode,
                style = Stroke(1.dp.toPx())
            )
            drawCircle(color = Color(0xFF141519), radius = 7.5.dp.toPx(), center = leftNode)
            drawCircle(color = voltColor, radius = 7.5.dp.toPx(), center = leftNode, style = Stroke(1.5.dp.toPx()))
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = leftNode)

            // Right Node (Receiver)
            val rippleProgress = (phase * 1.5f) % 1f
            drawCircle(
                color = voltColor.copy(alpha = (1f - rippleProgress) * 0.30f),
                radius = 6.dp.toPx() + rippleProgress * 8.dp.toPx(),
                center = rightNode,
                style = Stroke(1.dp.toPx())
            )
            drawCircle(color = Color(0xFF181A20), radius = 6.5.dp.toPx(), center = rightNode)
            drawCircle(color = voltColor.copy(0.7f), radius = 6.5.dp.toPx(), center = rightNode, style = Stroke(1.2.dp.toPx()))
            drawCircle(color = voltColor, radius = 2.2.dp.toPx(), center = rightNode)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatStrip — Horizontal scrolling strip of stat chips (not cards!)
// Thin, sleek, borderless. More Raycast than bento grid.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatStrip(
    devices: List<DeviceInfo>,
    transfers: List<TransferItem>,
    onDevicesClick: () -> Unit,
    onTransfersClick: () -> Unit
) {
    val onlineDevices = devices.count { it.status == DeviceStatus.ONLINE }
    val activeTransfers = transfers.count { it.status == TransferStatus.ACTIVE }
    val poolTotal = devices.filter { it.status == DeviceStatus.ONLINE }.sumOf { it.storageTotal }
    val poolUsed  = devices.filter { it.status == DeviceStatus.ONLINE }.sumOf { it.storageUsed }
    val freeBytes = (poolTotal - poolUsed).coerceAtLeast(0L)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            StatChip(
                value = "$onlineDevices",
                label = "Online",
                highlight = onlineDevices > 0,
                onClick = onDevicesClick
            )
        }
        item {
            StatChip(
                value = if (activeTransfers > 0) "$activeTransfers" else "—",
                label = "Active xfers",
                highlight = activeTransfers > 0,
                onClick = onTransfersClick
            )
        }
        item {
            StatChip(
                value = if (poolTotal > 0) formatSize(freeBytes) else "—",
                label = "Pool free",
                highlight = false
            )
        }
        item {
            StatChip(
                value = "${devices.size}",
                label = "Trusted",
                highlight = false
            )
        }
    }
}

@Composable
private fun StatChip(
    value: String,
    label: String,
    highlight: Boolean,
    onClick: (() -> Unit)? = null
) {
    val chipShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(
                if (highlight)
                    Brush.verticalGradient(listOf(VoltGlassLight, Color(0xFF0D0D0F)))
                else
                    Brush.verticalGradient(listOf(Carbon, Carbon))
            )
            .border(
                0.8.dp,
                if (highlight) VoltBorder else GlassEdgeSubtle,
                chipShape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    letterSpacing = (-1).sp
                ),
                color = if (highlight) VoltGreen else White90
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = if (highlight) VoltGreenDim else White40
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SectionLabel — minimal left label + right text action
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = (-0.3).sp
            ),
            color = White90
        )
        if (action != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = VoltGreen
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = VoltGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VoltDeviceCard — premium dark device card with volt-green active indicator
// Obtic-style widget: square-ish, bold label, icon, status indicator at top
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VoltDeviceCard(
    device: DeviceInfo,
    onClick: () -> Unit
) {
    val isOnline = device.status == DeviceStatus.ONLINE
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(164.dp)
            .clip(cardShape)
            .background(
                if (isOnline)
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF181A14),  // very dark volt-tinted
                            Color(0xFF121212)
                        )
                    )
                else
                    Brush.verticalGradient(listOf(Charcoal, Carbon))
            )
            .border(
                0.8.dp,
                if (isOnline) VoltBorder else GlassEdgeSubtle,
                cardShape
            )
            .clickable(onClick = onClick)
    ) {
        // Volt glow at top-right if online
        if (isOnline) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFC8FF00).copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, 0f),
                        radius = size.width * 0.75f
                    ),
                    radius = size.width * 0.75f,
                    center = Offset(size.width * 0.85f, 0f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: status dot + device type icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) VoltGreen else Steel
                        )
                )

                // Device icon in a small dark pill
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isOnline) VoltGlassLight else Glass04
                        )
                        .border(
                            0.6.dp,
                            if (isOnline) VoltBorder else GlassEdgeSubtle,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (device.type) {
                            DeviceType.DESKTOP -> Icons.Outlined.DesktopWindows
                            DeviceType.LAPTOP  -> Icons.Outlined.Laptop
                            DeviceType.TABLET  -> Icons.Outlined.TabletAndroid
                            else               -> Icons.Outlined.PhoneAndroid
                        },
                        contentDescription = null,
                        tint = if (isOnline) VoltGreen else White40,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom: device name + status text
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = if (isOnline) White100 else White60,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (isOnline) "Connected" else "Offline",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color = if (isOnline) VoltGreenDim else White20
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FileRow — ultra clean file list item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FileRow(
    file: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(Carbon)
            .border(0.6.dp, GlassEdgeSubtle, rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File type icon pill
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fileTypeColor(file.extension).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fileTypeIcon(file.extension),
                contentDescription = null,
                tint = fileTypeColor(file.extension),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = White90,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatSize(file.size)} · ${file.extension.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = White40
            )
        }

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = White20,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActiveTransferRow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ActiveTransferRow(
    transfer: TransferItem,
    modifier: Modifier = Modifier
) {
    val progress = transfer.progress
    val rowShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .clip(rowShape)
            .background(Carbon)
            .border(0.6.dp, GlassEdgeSubtle, rowShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transfer.fileName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = White90,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = VoltGreen
            )
        }
        Spacer(Modifier.height(8.dp))
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Zinc)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VoltGreen)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state cards
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyDevicesCard(onPairClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Carbon)
            .border(0.8.dp, GlassEdgeSubtle, shape)
            .clickable(onClick = onPairClick)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VoltGlassLight)
                    .border(0.8.dp, VoltBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddCircle,
                    contentDescription = null,
                    tint = VoltGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "No devices paired",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = White90
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap to scan QR and pair your first device",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = White40
            )
        }
    }
}

@Composable
private fun EmptyFilesCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Carbon)
            .border(0.8.dp, GlassEdgeSubtle, shape)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = White20,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No recent files",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = White40
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
fun getGreeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        in 17..20 -> "GOOD EVENING"
        else      -> "GOOD NIGHT"
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.size - 1) {
        value /= 1024.0
        idx++
    }
    return if (value < 10) "%.1f %s".format(value, units[idx])
    else "%.0f %s".format(value, units[idx])
}

private fun fileTypeColor(ext: String): Color = when (ext.lowercase()) {
    "pdf", "doc", "docx", "txt", "md" -> ColorDocument
    "jpg", "jpeg", "png", "gif", "webp", "svg", "heic" -> ColorImage
    "mp4", "mov", "avi", "mkv", "webm" -> ColorVideo
    "mp3", "wav", "flac", "aac", "m4a" -> ColorAudio
    "zip", "rar", "tar", "gz", "7z" -> ColorArchive
    "kt", "java", "py", "js", "ts", "rs", "go", "c", "cpp" -> ColorCode
    "apk", "exe", "dmg", "msi" -> ColorApplication
    else -> ColorUnknown
}

private fun fileTypeIcon(ext: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (ext.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic" -> Icons.Outlined.Image
        "mp4", "mov", "avi", "mkv" -> Icons.Outlined.VideoFile
        "mp3", "wav", "flac", "aac" -> Icons.Outlined.AudioFile
        "zip", "rar", "tar", "gz" -> Icons.Outlined.Archive
        "kt", "java", "py", "js" -> Icons.Outlined.Code
        "apk", "exe" -> Icons.Outlined.Apps
        "pdf" -> Icons.Outlined.PictureAsPdf
        else -> Icons.Outlined.Article
    }
