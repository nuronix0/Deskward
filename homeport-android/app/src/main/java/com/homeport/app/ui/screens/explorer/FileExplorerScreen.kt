package com.homeport.app.ui.screens.explorer

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.homeport.app.data.mock.MockDataRepository
import com.homeport.app.domain.model.*
import com.homeport.app.ui.components.*
import com.homeport.app.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import com.homeport.app.network.ConnectionStatus
import com.homeport.app.network.HomePortClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    deviceId: String,
    onFileClick: (String) -> Unit,
    onBack: (() -> Unit)?,
    paddingValues: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val client = remember { HomePortClient.getInstance(context) }
    val connectedDevices by client.connectedDevices.collectAsState()
    val connectedDevice by client.connectedDevice.collectAsState()
    val connectionStatus by client.connectionStatus.collectAsState()
    val repo = remember { MockDataRepository() }

    val device = connectedDevices.find { it.id == deviceId }
        ?: if (connectedDevice?.id == deviceId) connectedDevice else repo.devices.find { it.id == deviceId }

    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var searchQuery by remember { mutableStateOf("") }
    var sortSheet by remember { mutableStateOf(false) }
    var sortKey by remember { mutableStateOf(SortKey.DATE) }
    var sortAscending by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>("All") }
    var actionFile by remember { mutableStateOf<FileItem?>(null) }
    var fileToDelete by remember { mutableStateOf<FileItem?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var downloadStatus by remember { mutableStateOf<String?>(null) }

    // Path stack for directory navigation
    var pathStack by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val currentPath = if (pathStack.isEmpty()) "/" else pathStack.last().second
    val breadcrumb = if (pathStack.isEmpty()) "/" else pathStack.joinToString(" / ") { it.first }

    var remoteFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }

    val isLiveDevice = (connectedDevices.any { it.id == deviceId } || connectedDevice?.id == deviceId || deviceId.isEmpty() || deviceId.isNotEmpty()) &&
        (connectionStatus == ConnectionStatus.CONNECTED || connectedDevices.isNotEmpty() || connectedDevice != null)

    LaunchedEffect(currentPath, isLiveDevice, refreshTrigger) {
        if (isLiveDevice) {
            isLoadingRemote = true
            val res = client.listDirectory(currentPath, deviceId = deviceId)
            res.forEach { file ->
                client.fileCache[file.id] = file
                client.fileCache[file.name] = file
            }
            remoteFiles = res
            isLoadingRemote = false
        }
    }

    val rawFiles = if (isLiveDevice) remoteFiles
                   else emptyList()

    // Filter by query and category chip
    val filteredFiles = remember(searchQuery, selectedCategoryFilter, sortKey, sortAscending, rawFiles) {
        var list = rawFiles

        if (searchQuery.isNotEmpty()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        when (selectedCategoryFilter) {
            "Docs"     -> list = list.filter { it.category == FileCategory.DOCUMENT }
            "Media"    -> list = list.filter { it.category == FileCategory.IMAGE || it.category == FileCategory.VIDEO || it.category == FileCategory.AUDIO }
            "Code"     -> list = list.filter { it.category == FileCategory.CODE }
            "Archives" -> list = list.filter { it.category == FileCategory.ARCHIVE }
            "Folders"  -> list = list.filter { it.isDirectory }
        }

        // Sort: folders always first, then apply sortKey
        list.sortedWith { a, b ->
            if (a.isDirectory && !b.isDirectory) -1
            else if (!a.isDirectory && b.isDirectory) 1
            else {
                val comparison = when (sortKey) {
                    SortKey.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                    SortKey.DATE -> a.modifiedAt.compareTo(b.modifiedAt)
                    SortKey.SIZE -> a.size.compareTo(b.size)
                    SortKey.TYPE -> a.category.name.compareTo(b.category.name)
                }
                if (sortAscending) comparison else -comparison
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background)
                ) {
                    if (onBack != null) Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (onBack == null) it.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else it }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null || pathStack.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Carbon)
                                    .border(0.8.dp, GlassEdgeSubtle, CircleShape)
                                    .clickable {
                                        if (pathStack.isNotEmpty()) {
                                            pathStack = pathStack.dropLast(1)
                                        } else {
                                            onBack?.invoke()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowBackIosNew,
                                    "Back",
                                    tint = White90,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device?.name ?: "Files",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = White100,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (device != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VoltGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        " · $breadcrumb",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = White40,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // View toggle & sort actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val toggleShape = RoundedCornerShape(12.dp)
                            Box(
                                modifier = Modifier
                                    .clip(toggleShape)
                                    .background(Carbon)
                                    .border(0.8.dp, GlassEdgeSubtle, toggleShape)
                                    .padding(2.5.dp)
                            ) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (viewMode == ViewMode.LIST)
                                                    Modifier.background(VoltGreen)
                                                else Modifier
                                            )
                                            .clickable { viewMode = ViewMode.LIST },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Icon(
                                            Icons.Outlined.ViewList,
                                            "List",
                                            tint = if (viewMode == ViewMode.LIST) Color(0xFF0A0A0C) else White40,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (viewMode == ViewMode.GRID)
                                                    Modifier.background(VoltGreen)
                                                else Modifier
                                            )
                                            .clickable { viewMode = ViewMode.GRID },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.GridView,
                                            "Grid",
                                            tint = if (viewMode == ViewMode.GRID) Color(0xFF0A0A0C) else White40,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(35.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Carbon)
                                    .border(0.8.dp, GlassEdgeSubtle, RoundedCornerShape(10.dp))
                                    .clickable { sortSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.SwapVert,
                                    "Sort",
                                    tint = if (sortSheet) VoltGreen else White60,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Inline Search Bar
                    GlassSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search files in $breadcrumb…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )

                    // Category Filter Chips
                    val categories = listOf("All", "Docs", "Media", "Code", "Archives", "Folders")
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategoryFilter == cat
                            val chipShape = RoundedCornerShape(20.dp)

                            Box(
                                modifier = Modifier
                                    .clip(chipShape)
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(VoltGreen)
                                        } else {
                                            Modifier
                                                .background(Carbon)
                                                .border(0.8.dp, GlassEdgeSubtle, chipShape)
                                        }
                                    )
                                    .clickable { selectedCategoryFilter = cat }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color(0xFF0A0A0C) else White60
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = GlassEdgeSubtle, thickness = 0.6.dp)
                }
            },
            floatingActionButton = {
                if (!isLiveDevice) {
                    Box(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = paddingValues.calculateBottomPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(VoltGreen)
                                .clickable { /* New folder or upload */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                "New folder or file",
                                tint = Color(0xFF0A0A0C),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        ) { inner ->
            if (isLoadingRemote) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = HomePortBlue, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Loading directory…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            } else if (!isLiveDevice && deviceId.isNotEmpty()) {
                // Device was specified but we're not connected — show disconnect state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1C22).copy(alpha = 0.80f))
                                .border(
                                    0.8.dp,
                                    Brush.verticalGradient(listOf(Color.White.copy(0.18f), Color.White.copy(0.04f))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.WifiOff, null, tint = Color(0xFFFF453A), modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Connection Lost",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("The device disconnected. Go to Devices tab to reconnect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredFiles.isEmpty()) {
                EmptyFolderState(
                    query = searchQuery,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                )
            } else {
                when (viewMode) {
                    ViewMode.LIST -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = inner.calculateTopPadding() + 10.dp,
                                bottom = paddingValues.calculateBottomPadding() + 88.dp,
                                start = 16.dp,
                                end = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredFiles, key = { it.id }) { file ->
                                FileRow(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            pathStack = pathStack + Pair(file.name, file.id)
                                        } else {
                                            client.fileCache[file.id] = file
                                            client.fileCache[file.name] = file
                                            onFileClick(file.id)
                                        }
                                    },
                                    onMoreClick = { actionFile = file }
                                )
                            }
                        }
                    }
                    ViewMode.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = inner.calculateTopPadding() + 10.dp,
                                bottom = paddingValues.calculateBottomPadding() + 88.dp,
                                start = 16.dp,
                                end = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredFiles, key = { it.id }) { file ->
                                FileGridItem(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            pathStack = pathStack + Pair(file.name, file.id)
                                        } else {
                                            client.fileCache[file.id] = file
                                            client.fileCache[file.name] = file
                                            onFileClick(file.id)
                                        }
                                    },
                                    onMoreClick = { actionFile = file }
                                )
                            }
                        }
                    }
                }
            }

            // ── Sort Bottom Sheet ─────────────────────────────────────────────────
            if (sortSheet) {
                ModalBottomSheet(
                    onDismissRequest = { sortSheet = false },
                    containerColor = Carbon,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 36.dp)
                    ) {
                        Text(
                            "Sort By",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = White100
                        )
                        Spacer(Modifier.height(16.dp))

                        listOf(
                            SortKey.DATE to "Date Modified",
                            SortKey.NAME to "Name (A-Z)",
                            SortKey.SIZE to "Size",
                            SortKey.TYPE to "Category / Type"
                        ).forEach { (key, label) ->
                            val isSelected = sortKey == key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected) Modifier.background(VoltGlassLight).border(0.8.dp, VoltBorder, RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        if (sortKey == key) sortAscending = !sortAscending
                                        else {
                                            sortKey = key
                                            sortAscending = (key == SortKey.NAME)
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) VoltGreen else White90
                                )
                                if (isSelected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (sortAscending) "↑ Asc" else "↓ Desc",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VoltGreen
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Check, null, tint = VoltGreen, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── File Actions Bottom Sheet ─────────────────────────────────────────
            actionFile?.let { file ->
                ModalBottomSheet(
                    onDismissRequest = { actionFile = null },
                    containerColor = Carbon,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 36.dp)
                    ) {
                        // Header info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileTypeIconView(file = file, size = 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = White100,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "${if (file.isDirectory) "Folder" else file.category.displayName} • ${file.formattedSize} • ${file.formattedDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = White40
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = GlassEdgeSubtle, thickness = 0.6.dp)
                        Spacer(Modifier.height(14.dp))

                        // Actions
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!file.isDirectory) {
                                FileActionItem(icon = Icons.Outlined.Download, title = "Download to Device") {
                                    val target = actionFile
                                    actionFile = null
                                    if (target != null) {
                                        scope.launch {
                                            downloadStatus = "Downloading ${target.name}..."
                                            val dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                            val homeportDir = java.io.File(dlDir, "HOMEPORT").apply { mkdirs() }
                                            val dest = java.io.File(homeportDir, target.name)
                                            val ok = client.downloadFile(
                                                fileId = target.id,
                                                fileName = target.name,
                                                fileSize = target.size,
                                                destinationFile = dest
                                            )
                                            downloadStatus = if (ok) "Saved to Downloads/HOMEPORT/${target.name}"
                                                             else "Download failed for ${target.name}"
                                        }
                                    }
                                }
                            }
                            FileActionItem(icon = Icons.Outlined.Visibility, title = "Open & Preview") {
                                val target = file
                                actionFile = null
                                client.fileCache[target.id] = target
                                client.fileCache[target.name] = target
                                onFileClick(target.id)
                            }
                            FileActionItem(icon = Icons.Outlined.Share, title = "Send to Paired Device") {
                                actionFile = null
                            }
                            FileActionItem(icon = Icons.Outlined.ContentCopy, title = "Copy Path") {
                                actionFile = null
                            }
                            FileActionItem(
                                icon = Icons.Outlined.DeleteOutline,
                                title = "Delete File",
                                isDestructive = true
                            ) {
                                val target = file
                                actionFile = null
                                fileToDelete = target
                            }
                        }
                    }
                }
            }

            fileToDelete?.let { target ->
                AlertDialog(
                    onDismissRequest = { fileToDelete = null },
                    containerColor = Carbon,
                    title = {
                        Text(
                            text = if (target.isDirectory) "Delete Folder" else "Delete File",
                            color = White100,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to delete \"${target.name}\"? This action cannot be undone.",
                            color = White60
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val fileId = target.id
                                fileToDelete = null
                                scope.launch {
                                    downloadStatus = "Deleting ${target.name}..."
                                    val ok = client.deleteFile(fileId)
                                    if (ok) {
                                        downloadStatus = "Deleted ${target.name}"
                                        refreshTrigger++
                                    } else {
                                        downloadStatus = "Failed to delete ${target.name}"
                                    }
                                }
                            }
                        ) {
                            Text("Delete", color = SignalRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToDelete = null }) {
                            Text("Cancel", color = White40)
                        }
                    }
                )
            }
        }

        downloadStatus?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = paddingValues.calculateBottomPadding() + 80.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Carbon)
                    .border(0.8.dp, VoltBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = White100,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "OK",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = VoltGreen,
                        modifier = Modifier
                            .clickable { downloadStatus = null }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Carbon)
            .border(0.8.dp, GlassEdgeSubtle, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isDestructive) SignalRed.copy(alpha = 0.14f)
                    else VoltGlassLight
                )
                .border(
                    0.8.dp,
                    if (isDestructive) SignalRed.copy(alpha = 0.35f)
                    else VoltBorder,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) SignalRed else VoltGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isDestructive) SignalRed else White100
        )
    }
}

@Composable
private fun FileGridItem(
    file: FileItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Carbon)
            .border(width = 0.8.dp, color = GlassEdgeSubtle, shape = shape)
            .bounceClick(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMoreClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Actions",
                        tint = White40,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            FileTypeIconView(file = file, size = 48.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = White100,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (file.isDirectory) "Folder" else file.formattedSize,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = if (file.isDirectory) Color(0xFFF59E0B) else White40,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyFolderState(query: String = "", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Carbon)
                .border(0.8.dp, GlassEdgeSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                null,
                tint = White40,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (query.isNotEmpty()) "No files matching \"$query\"" else "This folder is empty",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = White100
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (query.isNotEmpty()) "Try searching for a different keyword" else "No files or documents found in this directory",
            style = MaterialTheme.typography.bodySmall,
            color = White40
        )
    }
}

private enum class ViewMode { LIST, GRID }
private enum class SortKey { NAME, DATE, SIZE, TYPE }
