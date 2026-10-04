package com.homeport.app.ui.screens.more

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.homeport.app.ui.Routes
import com.homeport.app.ui.components.GlassCard
import com.homeport.app.ui.components.IconConfig
import com.homeport.app.ui.components.DynamicIslandController
import com.homeport.app.ui.theme.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.homeport.app.R

@Composable
fun MoreScreen(
    onNavigate: (String) -> Unit,
    onActivity: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSharedFolders: () -> Unit = {},
    onDevices: () -> Unit = {},
    onTransfers: () -> Unit = {},
    paddingValues: PaddingValues = PaddingValues()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            bottom = paddingValues.calculateBottomPadding() + 16.dp,
            start = 20.dp,
            end = 20.dp
        )
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(24.dp))
            Text(
                "More",
                style = MaterialTheme.typography.headlineSmall,
                color = White100,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Profile card ──────────────────────────────────────────────────
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSettings() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(VoltGreen, VoltGreenDim))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "A",
                            color = Color(0xFF0A0A0C),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Aditya",
                            style = MaterialTheme.typography.titleMedium,
                            color = White100,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "My Phone  ·  HP-7F2A-91BC",
                            style = MaterialTheme.typography.bodySmall,
                            color = White40
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = White40)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Dynamic Island Live Activity Showcase ─────────────────────────
        item {
            val scope = rememberCoroutineScope()
            SectionLabel("Live Activities & Island")
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(VoltGlassLight)
                                    .border(0.8.dp, VoltBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = VoltGreen, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Dynamic Island", style = MaterialTheme.typography.titleMedium, color = White100, fontWeight = FontWeight.Bold)
                                Text("Apple-grade Spring Physics & Morphing", style = MaterialTheme.typography.bodySmall, color = White40)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Primary trigger button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VoltGreen)
                            .clickable { DynamicIslandController.runDemoSimulation(scope) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayArrow, null, tint = ActionVoltText, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("▶ Run Island Live Demo", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ActionVoltText)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Quick state trigger chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Carbon)
                                .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    DynamicIslandController.showConnected("DESKTOP-P3AM34E", "Windows 11", "Direct P2P LAN")
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Connected", style = MaterialTheme.typography.labelSmall, color = White90)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Carbon)
                                .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    DynamicIslandController.startTransfer("Video_4K_HDR.mp4", 0.65f, 650_000_000L, 1_000_000_000L, "34.2 MB/s", false, "DESKTOP-P3AM34E")
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Transferring", style = MaterialTheme.typography.labelSmall, color = VoltGreen)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Carbon)
                                .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    DynamicIslandController.showSuccess("Transfer Complete", "1.0 GB Saved")
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Success", style = MaterialTheme.typography.labelSmall, color = SignalGreen)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Carbon)
                                .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    DynamicIslandController.setIdle()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Idle", style = MaterialTheme.typography.labelSmall, color = White40)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Section 1: Browse ─────────────────────────────────────────────
        item {
            SectionLabel("Browse")
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(
                        MoreItem("My Files",      Icons.Outlined.Folder,       { onNavigate(Routes.FILES) }),
                        MoreItem("Devices",       Icons.Outlined.Devices,      { onDevices() }),
                        MoreItem("Transfers",     Icons.Outlined.SwapVert,     { onTransfers() },   "2"),
                        MoreItem("Shared Folders",Icons.Outlined.FolderShared, { onSharedFolders() }),
                        MoreItem("Activity Log",  Icons.Outlined.History,      { onActivity() }),
                        MoreItem("Recycle Bin",   Icons.Outlined.Delete,       { }),
                    ).forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = GlassEdgeSubtle
                        )
                        MoreRow(item = item)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Section 2: Account & Settings ─────────────────────────────────
        item {
            SectionLabel("Account")
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(
                        MoreItem("Settings",      Icons.Outlined.Settings,     { onSettings() }),
                        @Suppress("DEPRECATION")
                        MoreItem("Help & Support", Icons.Outlined.HelpOutline,  { }),
                        MoreItem("About Portal", Icons.Outlined.Info,         { }),
                    ).forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = GlassEdgeSubtle
                        )
                        MoreRow(item = item)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Pro upsell ────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Carbon)
                    .border(
                        0.8.dp,
                        VoltBorder,
                        RoundedCornerShape(20.dp)
                    )
                .clickable { }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VoltGlassLight)
                            .border(0.8.dp, VoltBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null, tint = VoltGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Portal Pro",
                            style = MaterialTheme.typography.titleMedium,
                            color = White100,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Unlimited transfers · Priority relay · Mesh sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = White40
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(VoltGreen)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "Upgrade",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF0A0A0C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = White40,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun MoreRow(item: MoreItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.action)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconShape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(iconShape)
                .background(VoltGlassLight)
                .border(
                    0.8.dp,
                    VoltBorder,
                    iconShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon, item.label,
                tint = VoltGreen,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = White100,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (item.badge != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(VoltGreen)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    item.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Outlined.ChevronRight, null,
            tint = White40,
            modifier = Modifier.size(16.dp)
        )
    }
}

private data class MoreItem(
    val label: String,
    val icon: ImageVector,
    val action: () -> Unit,
    val badge: String? = null,
    val custom3dIconRes: Int? = null
)
