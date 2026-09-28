package com.homeport.app.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.platform.LocalContext
import com.homeport.app.network.HomePortClient
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    deviceId: String,
    onBack: () -> Unit,
    onFileClick: (String) -> Unit
) {
    val context = LocalContext.current
    val client = remember { HomePortClient.getInstance(context) }
    val connectedDevice by client.connectedDevice.collectAsState()
    val repo = remember { MockDataRepository() }
    val device = if (connectedDevice?.id == deviceId) connectedDevice else repo.devices.find { it.id == deviceId }

    var query by remember { mutableStateOf("") }
    var activeScope by remember { mutableStateOf(SearchScope.ALL) }
    var isSearching by remember { mutableStateOf(false) }
    var liveResults by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    // Bug #9 fix: Add 300ms debounce to avoid flooding server on every keystroke
    LaunchedEffect(query) {
        if (query.length >= 2) {
            delay(300L) // debounce
            if (connectedDevice != null) {
                isSearching = true
                liveResults = client.searchFiles(query)
                isSearching = false
            }
        } else {
            liveResults = emptyList()
        }
    }

    // Bug #20 fix: Apply scope filter to results
    val results = remember(query, liveResults, activeScope) {
        val rawResults = if (query.length >= 2) {
            if (liveResults.isNotEmpty()) liveResults
            else repo.searchResults.filter { it.name.contains(query, ignoreCase = true) }
        } else emptyList()

        // Apply scope filter
        when (activeScope) {
            SearchScope.ALL -> rawResults
            SearchScope.FILES -> rawResults.filter { !it.isDirectory }
            SearchScope.FOLDERS -> rawResults.filter { it.isDirectory }
            SearchScope.DEVICES -> rawResults // No device filtering applicable here
        }
    }

    val recentSearches = listOf("project", "vacation", "report pdf", "MainActivity.kt")
    val suggestions = listOf("project", "project report", "project files", "project plan")

    Scaffold(
        containerColor = Background,
        topBar = {
            Column {
                Spacer(Modifier.statusBarsPadding())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Carbon)
                            .border(0.8.dp, GlassEdgeSubtle, androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.ArrowBackIosNew, "Back", tint = White90, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    GlassSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search ${device?.name ?: "files"}…",
                        modifier = Modifier.weight(1f)
                    )
                    // Bug #19 fix: removed dead filter button (was empty clickable)
                }

                // Scope Spotlight Filter Bar with Top LED & Downward Light Beam
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    SpotlightFilterBar(
                        items = SearchScope.entries,
                        selectedItem = activeScope,
                        onItemSelected = { activeScope = it },
                        labelProvider = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + 8.dp,
                bottom = 80.dp
            )
        ) {
            when {
                query.isEmpty() -> {
                    // Recent searches
                    item {
                        SectionHeader("Recent", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(10.dp))
                    }
                    items(recentSearches) { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { query = recent }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.History, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(recent, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.NorthWest, null, tint = TextQuaternary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                query.length < 2 -> {
                    // Suggestions
                    item {
                        SectionHeader("Suggestions", modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(10.dp))
                    }
                    items(suggestions.filter { it.startsWith(query, ignoreCase = true) }) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { query = s }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Search, null, tint = HomePortBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(s, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
                isSearching -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = HomePortBlue, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Searching…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        }
                    }
                }
                results.isEmpty() -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.SearchOff, null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No results for \"$query\"", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                            Text("Try different keywords or filters", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        }
                    }
                }
                else -> {
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Results (${results.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    items(results, key = { it.id }) { file ->
                        SearchResultRow(file = file, onClick = {
                            client.fileCache[file.id] = file
                            client.fileCache[file.name] = file
                            onFileClick(file.id)
                        })
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 74.dp, end = 20.dp),
                            color = SeparatorOpaque.copy(0.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(file: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeIconView(file = file, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium,
                color = White100, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text("${file.path} · ${file.formattedDate}",
                style = MaterialTheme.typography.labelSmall, color = White40, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.MoreVert, null, tint = White40, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ScopeChip(scope: SearchScope, selected: Boolean, onSelect: () -> Unit) {
    val chipShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(if (selected) VoltGreen else Carbon)
            .border(
                0.8.dp,
                if (selected) VoltBorder else GlassEdgeSubtle,
                chipShape
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 15.dp, vertical = 7.dp)
    ) {
        Text(
            text = scope.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color(0xFF0A0A0C) else White60,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private enum class SearchScope(val label: String) {
    ALL("All"), FILES("Files"), FOLDERS("Folders"), DEVICES("Devices")
}
