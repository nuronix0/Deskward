package com.homeport.app.ui.screens.storage

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.homeport.app.data.mock.MockDataRepository
import com.homeport.app.domain.model.*
import com.homeport.app.ui.components.*
import com.homeport.app.ui.theme.*

@Composable
fun StorageScreen(deviceId: String, onBack: () -> Unit) {
    val repo = remember { MockDataRepository() }
    val device = repo.devices.find { it.id == deviceId } ?: return

    Scaffold(
        containerColor = Background,
        topBar = {
            Column(modifier = Modifier.background(Background)) {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Spacer(Modifier.width(12.dp))
                    Text("Storage · ${device.name}",
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.5).sp),
                        color = White100, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Background),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 8.dp,
                bottom = 32.dp, start = 20.dp, end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage ring hero
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StorageRing(
                            used = device.storageUsed,
                            total = device.storageTotal,
                            size = 110.dp
                        )
                        Spacer(Modifier.width(24.dp))
                        Column {
                            StorageStat("Total", formatSize(device.storageTotal))
                            Spacer(Modifier.height(8.dp))
                            StorageStat("Used", formatSize(device.storageUsed))
                            Spacer(Modifier.height(8.dp))
                            StorageStat("Free", formatSize(device.storageFree))
                        }
                    }
                }
            }

            // Categories
            item {
                SectionHeader("Categories", modifier = Modifier)
                Spacer(Modifier.height(10.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val total = repo.storageCategories.sumOf { it.second }
                        repo.storageCategories.forEachIndexed { idx, (name, size) ->
                            if (idx > 0) HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = GlassEdgeSubtle
                            )
                            StorageCategoryRow(name = name, size = size, total = total)
                        }
                    }
                }
            }

            // Quick actions
            item {
                SectionHeader("Manage", modifier = Modifier)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("Largest Files", Icons.Outlined.FileDownload, {}),
                        Triple("Duplicate Files", Icons.Outlined.ContentCopy, {}),
                        Triple("Recently Added", Icons.Outlined.NewReleases, {})
                    ).forEach { (label, icon, action) ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = action) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(VoltGlassLight)
                                        .border(0.8.dp, VoltBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, label, tint = VoltGreen, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge,
                                    color = White100, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Icon(Icons.Outlined.ChevronRight, null, tint = White40)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = White40)
        Text(value, style = MaterialTheme.typography.titleMedium,
            color = White100, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StorageCategoryRow(name: String, size: Long, total: Long) {
    val fraction = if (total > 0) size.toFloat() / total else 0f
    val (categoryColor, categoryIcon) = when (name) {
        "Documents" -> Pair(Color(0xFF3B82F6), Icons.Outlined.Description)
        "Videos"    -> Pair(Color(0xFFA855F7), Icons.Outlined.PlayCircle)
        "Pictures"  -> Pair(Color(0xFF06B6D4), Icons.Outlined.Image)
        "Music"     -> Pair(Color(0xFFEC4899), Icons.Outlined.GraphicEq)
        "Projects"  -> Pair(VoltGreen, Icons.Outlined.Terminal)
        else        -> Pair(Color(0xFFF59E0B), Icons.Outlined.Folder)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(categoryColor.copy(0.12f))
                .border(0.8.dp, categoryColor.copy(0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = name,
                tint = categoryColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium,
                    color = White100, fontWeight = FontWeight.Medium)
                Text(formatSize(size), style = MaterialTheme.typography.bodySmall, color = White40)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFF1E2024))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
            }
        }
    }
}
