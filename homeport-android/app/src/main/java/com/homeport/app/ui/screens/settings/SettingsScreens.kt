package com.homeport.app.ui.screens.settings

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
import com.homeport.app.data.mock.MockDataRepository
import com.homeport.app.domain.model.*
import com.homeport.app.ui.components.GlassCard
import com.homeport.app.ui.components.SectionHeader
import com.homeport.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Settings Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenOnboarding: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("deskward_prefs", android.content.Context.MODE_PRIVATE) }
    var showOnboarding by remember {
        mutableStateOf(prefs.getBoolean("pref_show_onboarding", false))
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            Column {
                Spacer(Modifier.statusBarsPadding())
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = White90, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("PREFERENCES", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold), color = White40)
                        Text("Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = White100)
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 14.dp,
                bottom = 32.dp, start = 20.dp, end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Identity
            item {
                SectionText("Device Identity")
                Spacer(Modifier.height(8.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("This Device", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("My Phone", style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Outlined.Edit, "Edit", tint = VoltGreen)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("HP-7F2A-91BC", style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }

            // Settings sections
            val sections = listOf(
                "Storage & Sharing" to listOf(
                    SettingItem("Shared Folders", Icons.Outlined.Folder, "3 folders shared", null),
                    SettingItem("Transfer Settings", Icons.Outlined.SwapVert, "Wi-Fi only", null),
                    SettingItem("Storage Limit", Icons.Outlined.Storage, "No limit", null),
                ),
                "Security" to listOf(
                    SettingItem("Trusted Devices", Icons.Outlined.Shield, "4 devices", null),
                    SettingItem("Require Approval", Icons.Outlined.VerifiedUser, "On", null),
                    SettingItem("Connection Encryption", Icons.Outlined.Lock, "Always on", null),
                ),
                "Notifications" to listOf(
                    SettingItem("Transfer Alerts", Icons.Outlined.Notifications, "Enabled", null),
                    SettingItem("Device Events", Icons.Outlined.DeviceHub, "Enabled", null),
                    @Suppress("DEPRECATION")
                    SettingItem("Sound", Icons.Outlined.VolumeUp, "On", null),
                ),
                "General" to listOf(
                    SettingItem(
                        label = "Show Onboarding Screens",
                        icon = Icons.Outlined.Visibility,
                        value = if (showOnboarding) "Enabled" else "Disabled",
                        badge = null,
                        isSwitch = true,
                        switchState = showOnboarding,
                        onToggle = { enabled ->
                            showOnboarding = enabled
                            prefs.edit().putBoolean("pref_show_onboarding", enabled).apply()
                        }
                    ),
                    SettingItem(
                        label = "Replay Onboarding Tour",
                        icon = Icons.Outlined.Slideshow,
                        value = "4 Slides",
                        badge = null,
                        onClick = onOpenOnboarding
                    ),
                    SettingItem("Appearance", Icons.Outlined.Palette, "Dark", null),
                    SettingItem("Language", Icons.Outlined.Language, "English", null),
                    SettingItem("Start on Boot", Icons.Outlined.PowerSettingsNew, "Off", null),
                ),
                "Help" to listOf(
                    SettingItem("About Deskward", Icons.Outlined.Info, "v1.0.0", null),
                    @Suppress("DEPRECATION")
                    SettingItem("Help & Support", Icons.Outlined.HelpOutline, null, null),
                    SettingItem("Diagnostics", Icons.Outlined.BugReport, null, null),
                )
            )

            sections.forEach { (sectionTitle, items) ->
                item {
                    SectionText(sectionTitle)
                    Spacer(Modifier.height(8.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            items.forEachIndexed { idx, item ->
                                if (idx > 0) HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = SeparatorOpaque.copy(0.2f)
                                )
                                SettingRow(item = item)
                            }
                        }
                    }
                }
            }

            // Danger zone
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.DeleteForever, "Reset", tint = StatusError, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Reset Deskward", style = MaterialTheme.typography.bodyLarge,
                            color = StatusError, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionText(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = TextTertiary, letterSpacing = 1.sp)
}

@Composable
private fun SettingRow(item: SettingItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (item.isSwitch) {
                    item.onToggle?.invoke(!item.switchState)
                } else if (item.onClick != null) {
                    item.onClick.invoke()
                }
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, item.label, tint = TextPrimary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(item.label, style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary, modifier = Modifier.weight(1f))
        if (item.isSwitch) {
            Switch(
                checked = item.switchState,
                onCheckedChange = { item.onToggle?.invoke(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = VoltGreen,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = Carbon
                )
            )
        } else {
            if (item.value != null) {
                Text(item.value, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

private data class SettingItem(
    val label: String,
    val icon: ImageVector,
    val value: String?,
    val badge: String?,
    val isSwitch: Boolean = false,
    val switchState: Boolean = false,
    val onToggle: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Shared Folders Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SharedFoldersScreen(onBack: () -> Unit) {
    val repo = remember { MockDataRepository() }

    Scaffold(
        containerColor = Background,
        topBar = {
            Column {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = White90, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("STORAGE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold), color = White40)
                        Text("Shared Folders", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = White100)
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 8.dp,
                bottom = 32.dp, start = 20.dp, end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(repo.sharedFolders) { folder ->
                var isShared by remember { mutableStateOf(folder.isShared) }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isShared) Icons.Outlined.FolderShared else Icons.Outlined.Folder,
                            null,
                            tint = if (isShared) VoltGreen else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(folder.displayName, style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text(folder.path, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            if (isShared) {
                                Text(
                                    folder.permissions.joinToString(" · ") { it.displayName.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VoltGreen
                                )
                            }
                        }
                        Switch(
                            checked = isShared,
                            onCheckedChange = { isShared = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VoltGreen
                            )
                        )
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.AddCircleOutline, null, tint = VoltGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Folder", style = MaterialTheme.typography.bodyLarge, color = VoltGreen)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permissions Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PermissionsScreen(deviceId: String, onBack: () -> Unit) {
    val repo = remember { MockDataRepository() }
    val device = repo.devices.find { it.id == deviceId } ?: return
    val permStates = remember { Permission.entries.associateWith { mutableStateOf(it in device.permissions) }.toMutableMap() }

    Scaffold(
        containerColor = Background,
        topBar = {
            Column {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = White90, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("SECURITY", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold), color = White40)
                        Text("Permissions · ${device.name}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = White100)
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 8.dp,
                bottom = 32.dp, start = 20.dp, end = 20.dp
            )
        ) {
            item {
                Text(
                    "Control what ${device.name} can do with your files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Permission.entries.forEachIndexed { idx, perm ->
                            if (idx > 0) HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = SeparatorOpaque.copy(0.2f)
                            )
                            val state = permStates[perm] ?: return@forEachIndexed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(perm.displayName, style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text(perm.description, style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary)
                                }
                                Switch(
                                    checked = state.value,
                                    onCheckedChange = { state.value = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = VoltGreen,
                                        uncheckedThumbColor = TextTertiary,
                                        uncheckedTrackColor = Surface2
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
