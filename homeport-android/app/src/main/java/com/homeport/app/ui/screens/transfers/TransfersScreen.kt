package com.homeport.app.ui.screens.transfers

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.homeport.app.data.mock.MockDataRepository
import com.homeport.app.domain.model.*
import com.homeport.app.ui.components.*
import com.homeport.app.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import com.homeport.app.network.HomePortClient
import com.homeport.app.ui.screens.home.formatSize
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// TransfersScreen — Obsidian + Volt redesign
// Minimal header, filter strip at top, clean card list
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TransfersScreen(
    onOpenFile: ((String) -> Unit)? = null,
    paddingValues: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val client = remember { HomePortClient.getInstance(context) }
    val liveTransfers by client.activeTransfers.collectAsState()
    val transfers = liveTransfers
    var activeFilter by remember { mutableStateOf(TransferFilter.ALL) }

    val filteredTransfers = remember(activeFilter, transfers) {
        when (activeFilter) {
            TransferFilter.ALL       -> transfers
            TransferFilter.ACTIVE    -> transfers.filter { it.status == TransferStatus.ACTIVE || it.status == TransferStatus.PAUSED }
            TransferFilter.QUEUED    -> transfers.filter { it.status == TransferStatus.QUEUED }
            TransferFilter.COMPLETED -> transfers.filter { it.status == TransferStatus.COMPLETED }
            TransferFilter.FAILED    -> transfers.filter { it.status == TransferStatus.FAILED || it.status == TransferStatus.CANCELLED }
        }
    }

    val totalActive = transfers.count { it.status == TransferStatus.ACTIVE }
    val totalSpeed  = transfers.filter { it.status == TransferStatus.ACTIVE }.sumOf { it.speedBps }
    val totalEta    = transfers.filter { it.status == TransferStatus.ACTIVE && it.etaSeconds != null }
        .maxOfOrNull { it.etaSeconds ?: 0L } ?: 0L

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            )
        ) {

            // ── Header ─────────────────────────────────────────────────────
            item {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TRANSFERS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = 1.8.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = White40
                        )
                        Text(
                            text = if (totalActive > 0) "$totalActive Active" else "History",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 30.sp,
                                letterSpacing = (-1).sp
                            ),
                            color = White100
                        )
                    }

                    // Speed indicator pill
                    if (totalActive > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VoltGlassLight)
                                .border(0.8.dp, VoltBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = VoltGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${formatSize(totalSpeed)}/s",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = VoltGreen
                                )
                            }
                        }
                    }
                }
            }

            // ── Filter Strip with Spotlight LED & Downward Light Beam ──────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    SpotlightFilterBar(
                        items = TransferFilter.entries,
                        selectedItem = activeFilter,
                        onItemSelected = { activeFilter = it },
                        labelProvider = { it.label },
                        countProvider = { filter ->
                            val c = when (filter) {
                                TransferFilter.ALL       -> transfers.size
                                TransferFilter.ACTIVE    -> transfers.count { it.status == TransferStatus.ACTIVE || it.status == TransferStatus.PAUSED }
                                TransferFilter.QUEUED    -> transfers.count { it.status == TransferStatus.QUEUED }
                                TransferFilter.COMPLETED -> transfers.count { it.status == TransferStatus.COMPLETED }
                                TransferFilter.FAILED    -> transfers.count { it.status == TransferStatus.FAILED }
                            }
                            if (c > 0) c else null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Stats summary strip (when active) ─────────────────────────
            if (totalActive > 0) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TransferStatChip(
                            label = "Speed",
                            value = "${formatSize(totalSpeed)}/s",
                            icon = Icons.Outlined.Bolt,
                            modifier = Modifier.weight(1f)
                        )
                        TransferStatChip(
                            label = "ETA",
                            value = formatEtaMinutes(totalEta),
                            icon = Icons.Outlined.Schedule,
                            modifier = Modifier.weight(1f)
                        )
                        TransferStatChip(
                            label = "Files",
                            value = "$totalActive",
                            icon = Icons.Outlined.SwapVert,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // ── Transfer list ──────────────────────────────────────────────
            if (filteredTransfers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Carbon)
                                .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.SwapVert, null, tint = White20, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No transfers",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = White60
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Files you transfer will appear here",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = White40
                        )
                    }
                }
            } else {
                items(filteredTransfers, key = { it.id }) { transfer ->
                    VoltTransferCard(
                        transfer = transfer,
                        onOpenFile = onOpenFile,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                    )
                }
            }

            // ── Bulk action buttons ────────────────────────────────────────
            if (totalActive > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val pauseShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(pauseShape)
                                .background(Carbon)
                                .border(0.8.dp, GlassEdge, pauseShape)
                                .clickable { }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PauseCircle, null, tint = White60, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pause All", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = White60)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(pauseShape)
                                .background(SignalRedGlow)
                                .border(0.8.dp, SignalRed.copy(0.35f), pauseShape)
                                .clickable { client.cancelAllActiveTransfers() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Cancel, null, tint = SignalRed, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cancel All", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = SignalRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VoltTransferCard — progress card with volt accent bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TransferCard(
    transfer: TransferItem,
    onOpenFile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) = VoltTransferCard(transfer = transfer, onOpenFile = onOpenFile, modifier = modifier)

@Composable
private fun VoltTransferCard(
    transfer: TransferItem,
    onOpenFile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isActive = transfer.status == TransferStatus.ACTIVE
    val isCompleted = transfer.status == TransferStatus.COMPLETED
    val isFailed = transfer.status == TransferStatus.FAILED || transfer.status == TransferStatus.CANCELLED

    val accentColor = when {
        isCompleted -> SignalGreen
        isFailed    -> SignalRed
        isActive    -> VoltGreen
        else        -> White40
    }

    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Carbon)
            .border(
                0.8.dp,
                if (isActive) VoltBorder else GlassEdgeSubtle,
                cardShape
            )
            .then(
                if (isCompleted && onOpenFile != null) {
                    Modifier.clickable { onOpenFile(transfer.fileName) }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (transfer.direction == TransferDirection.UPLOAD)
                        Icons.Outlined.Upload else Icons.Outlined.Download,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.fileName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = White90,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = formatSize(transfer.fileSize),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = White40
                    )
                    if (transfer.sourceDeviceId.isNotEmpty()) {
                        Text(
                            text = "· ${transfer.sourceDeviceId}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = White40,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Open shortcut button for completed items
            if (isCompleted && onOpenFile != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(VoltGreen)
                        .clickable { onOpenFile(transfer.fileName) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = "Open",
                            tint = Color(0xFF090A0E),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF090A0E)
                        )
                    }
                }
            }

            // Status badge
            Spacer(Modifier.width(8.dp))
            StatusBadge(status = transfer.status)
        }

        // Progress bar (active/queued only)
        if (isActive || transfer.status == TransferStatus.PAUSED) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (transfer.speedBps > 0) "${formatSize(transfer.speedBps)}/s" else "Paused",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = White40
                )
                Text(
                    text = "${(transfer.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = accentColor
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Zinc)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(transfer.progress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor.copy(0.7f), accentColor)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TransferStatus) {
    val (label, color) = when (status) {
        TransferStatus.ACTIVE    -> "Active" to VoltGreen
        TransferStatus.PAUSED    -> "Paused" to SignalAmber
        TransferStatus.QUEUED    -> "Queued" to White40
        TransferStatus.COMPLETED -> "Done" to SignalGreen
        TransferStatus.FAILED    -> "Failed" to SignalRed
        TransferStatus.CANCELLED -> "Cancelled" to SignalRed
        TransferStatus.VERIFYING -> "Verifying" to SignalAmber
        TransferStatus.RESUMING  -> "Resuming" to VoltGreen
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            ),
            color = color
        )
    }
}

@Composable
private fun VoltFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val chipShape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(
                if (selected) VoltGlassLight else Carbon
            )
            .border(
                0.8.dp,
                if (selected) VoltBorder else GlassEdgeSubtle,
                chipShape
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = if (selected) VoltGreen else White60
            )
            if (count > 0) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = if (selected) VoltGreenDim else White40
                )
            }
        }
    }
}

@Composable
private fun TransferStatChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val chipShape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(chipShape)
            .background(Carbon)
            .border(0.8.dp, GlassEdgeSubtle, chipShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = White40, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                ),
                color = White40
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = VoltGreen
        )
    }
}

private fun formatEtaMinutes(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

private enum class TransferFilter(val label: String) {
    ALL("All"), ACTIVE("Active"), QUEUED("Queued"),
    COMPLETED("Done"), FAILED("Failed")
}
