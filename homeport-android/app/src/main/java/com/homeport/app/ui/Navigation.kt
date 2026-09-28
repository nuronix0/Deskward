package com.homeport.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.homeport.app.ui.screens.activity.ActivityScreen
import com.homeport.app.ui.screens.devices.DeviceDetailScreen
import com.homeport.app.ui.screens.devices.DevicesScreen
import com.homeport.app.ui.screens.devices.PairDeviceScreen
import com.homeport.app.ui.screens.explorer.FileExplorerScreen
import com.homeport.app.ui.screens.home.HomeScreen
import com.homeport.app.ui.screens.more.MoreScreen
import com.homeport.app.ui.screens.preview.FilePreviewScreen
import com.homeport.app.ui.screens.search.SearchScreen
import com.homeport.app.ui.screens.settings.PermissionsScreen
import com.homeport.app.ui.screens.settings.SettingsScreen
import com.homeport.app.ui.screens.settings.SharedFoldersScreen
import com.homeport.app.ui.screens.storage.StorageScreen
import com.homeport.app.ui.screens.transfers.TransfersScreen
import com.homeport.app.ui.theme.*
import com.homeport.app.network.HomePortClient
import androidx.compose.ui.platform.LocalContext
import com.homeport.app.ui.components.*
import com.homeport.app.ui.screens.devices.PairMode
import com.homeport.app.ui.screens.notifications.NotificationsScreen
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.homeport.app.ui.theme.VoltGreen
import com.homeport.app.ui.theme.TextPrimary
import com.homeport.app.ui.theme.TextSecondary
import androidx.navigation.navArgument
import com.homeport.app.ui.theme.TextTertiary
import com.homeport.app.ui.theme.ObsidianBase
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.homeport.app.domain.model.*
import com.homeport.app.network.AndroidMeshServerManager

// ─── Route constants ─────────────────────────────────────────────────────────
object Routes {
    const val HOME       = "home"
    const val FILES      = "files"
    const val TRANSFERS  = "transfers"
    const val DEVICES    = "devices"
    const val MORE       = "more"

    // Sub-screens
    const val DEVICE_DETAIL  = "device_detail/{deviceId}"
    const val PAIR_DEVICE    = "pair_device"
    const val PAIR_QR        = "pair_device_qr"
    const val FILE_EXPLORER  = "file_explorer/{deviceId}"
    const val SEARCH         = "search/{deviceId}"
    const val FILE_PREVIEW   = "file_preview?fileId={fileId}"
    const val STORAGE        = "storage/{deviceId}"
    const val ACTIVITY       = "activity"
    const val NOTIFICATIONS  = "notifications"
    const val SETTINGS       = "settings"
    const val ONBOARDING     = "onboarding"
    const val SHARED_FOLDERS = "shared_folders"
    const val PERMISSIONS    = "permissions/{deviceId}"

    fun deviceDetail(deviceId: String) = "device_detail/$deviceId"
    fun fileExplorer(deviceId: String) = "file_explorer/$deviceId"
    fun search(deviceId: String) = "search/$deviceId"
    fun filePreview(fileId: String) = "file_preview?fileId=${java.net.URLEncoder.encode(fileId, "UTF-8")}"
    fun storage(deviceId: String) = "storage/$deviceId"
    fun permissions(deviceId: String) = "permissions/$deviceId"
}

// ─── Bottom navigation items ──────────────────────────────────────────────────
private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME,      "Home",      Icons.Filled.Home,         Icons.Outlined.Home),
    BottomNavItem(Routes.FILES,     "Files",     Icons.Filled.Folder,       Icons.Outlined.Folder),
    BottomNavItem(Routes.TRANSFERS, "Transfers", Icons.Filled.SwapVert,     Icons.Outlined.SwapVert),
    BottomNavItem(Routes.DEVICES,   "Devices",   Icons.Filled.Devices,      Icons.Outlined.Devices),
    BottomNavItem(Routes.MORE,      "More",      Icons.Filled.MoreHoriz,    Icons.Outlined.MoreHoriz)
)

// ─── Main app shell with nav graph ───────────────────────────────────────────
@Composable
fun HomePortNavHost(navController: NavHostController) {
    val currentBackStack by navController.currentBackStackEntryAsState()

    val showBottomBar = bottomNavItems.any { item ->
        currentBackStack?.destination?.hierarchy?.any { it.route == item.route } == true
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (kotlin.math.abs(available.y) > 3f || kotlin.math.abs(available.x) > 3f) {
                    DynamicIslandController.onUserScrolled()
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        AtmosphericBackground(modifier = Modifier.fillMaxSize())

        // Main nav content — extends edge to edge
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it / 5 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 5 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 5 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / 5 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200))
            }
        ) {
            val safePopBack: () -> Unit = {
                if (!navController.popBackStack()) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            }

            // ── Primary destinations ──────────────────────────────────────
            composable(Routes.HOME) {
                HomeScreen(
                    onDeviceClick = { deviceId -> navController.navigate(Routes.deviceDetail(deviceId)) },
                    onSeeAllDevices = { navController.navigate(Routes.DEVICES) },
                    onSeeAllTransfers = { navController.navigate(Routes.TRANSFERS) },
                    onFileClick = { fileId ->
                        if (fileId.isNotBlank()) navController.navigate(Routes.filePreview(fileId))
                        else navController.navigate(Routes.FILES)
                    },
                    onBrowseFiles = { navController.navigate(Routes.FILES) },
                    onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSearchClick = { navController.navigate(Routes.search("dev_phone_001")) },
                    onScanQrClick = { navController.navigate(Routes.PAIR_QR) },
                    onProfileClick = { navController.navigate(Routes.SETTINGS) },
                    bottomBarHeight = if (showBottomBar) 100.dp else 0.dp
                )
            }
            composable(Routes.FILES) {
                val context = LocalContext.current
                val client = remember { HomePortClient.getInstance(context) }
                val meshServerManager = remember { AndroidMeshServerManager.getInstance(context) }
                val incomingClients by meshServerManager.incomingClients.collectAsState()
                val connectedDevices by client.connectedDevices.collectAsState()
                val connectedDevice by client.connectedDevice.collectAsState()
                val connectionStatus by client.connectionStatus.collectAsState()

                var selectedDeviceId by remember { mutableStateOf<String?>(null) }

                val effectiveDevices = remember(connectedDevices, incomingClients, connectedDevice) {
                    val list = mutableListOf<DeviceInfo>()
                    for (d in connectedDevices) {
                        if (list.none { it.id == d.id }) list.add(d)
                    }
                    if (connectedDevice != null && list.none { it.id == connectedDevice?.id }) {
                        connectedDevice?.let { list.add(it) }
                    }
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
                    list
                }

                val currentDevice = effectiveDevices.firstOrNull { it.id == selectedDeviceId }
                    ?: effectiveDevices.firstOrNull()

                val isConnected = connectionStatus == com.homeport.app.network.ConnectionStatus.CONNECTED ||
                    effectiveDevices.isNotEmpty()

                val liveDeviceId = if (isConnected) currentDevice?.id else null

                if (liveDeviceId != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (effectiveDevices.size > 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                            ) {
                                SpotlightFilterBar(
                                    items = effectiveDevices,
                                    selectedItem = effectiveDevices.firstOrNull { it.id == liveDeviceId } ?: effectiveDevices.first(),
                                    onItemSelected = { selectedDeviceId = it.id },
                                    labelProvider = { it.name },
                                    isScrollable = effectiveDevices.size > 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        FileExplorerScreen(
                            deviceId = liveDeviceId,
                            onFileClick = { fileId -> navController.navigate(Routes.filePreview(fileId)) },
                            onBack = null,
                            paddingValues = PaddingValues(bottom = if (showBottomBar) 100.dp else 0.dp)
                        )
                    }
                } else {
                    NoDeviceConnectedScreen(
                        onPairDevice = { navController.navigate(Routes.PAIR_DEVICE) },
                        paddingValues = PaddingValues(bottom = if (showBottomBar) 100.dp else 0.dp)
                    )
                }
            }
            composable(Routes.TRANSFERS) {
                TransfersScreen(
                    onOpenFile = { fileName ->
                        navController.navigate(Routes.filePreview(fileName))
                    },
                    paddingValues = PaddingValues(bottom = if (showBottomBar) 100.dp else 0.dp)
                )
            }
            composable(Routes.DEVICES) {
                DevicesScreen(
                    onDeviceClick = { deviceId -> navController.navigate(Routes.deviceDetail(deviceId)) },
                    onAddDevice = { navController.navigate(Routes.PAIR_DEVICE) },
                    onBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    paddingValues = PaddingValues(bottom = if (showBottomBar) 100.dp else 0.dp)
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onNavigate = { route -> navController.navigate(route) },
                    onActivity = { navController.navigate(Routes.ACTIVITY) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onSharedFolders = { navController.navigate(Routes.SHARED_FOLDERS) },
                    onDevices = { navController.navigate(Routes.DEVICES) },
                    onTransfers = { navController.navigate(Routes.TRANSFERS) },
                    paddingValues = PaddingValues(bottom = if (showBottomBar) 100.dp else 0.dp)
                )
            }

            // ── Sub-screens ───────────────────────────────────────────────
            composable(Routes.DEVICE_DETAIL) { backStack ->
                val deviceId = backStack.arguments?.getString("deviceId") ?: ""
                DeviceDetailScreen(
                    deviceId = deviceId,
                    onBack = safePopBack,
                    onBrowse = { navController.navigate(Routes.fileExplorer(deviceId)) },
                    onSearch = { navController.navigate(Routes.search(deviceId)) },
                    onStorage = { navController.navigate(Routes.storage(deviceId)) },
                    onPermissions = { navController.navigate(Routes.permissions(deviceId)) }
                )
            }
            composable(Routes.PAIR_DEVICE) {
                PairDeviceScreen(onBack = safePopBack, initialMode = PairMode.SECURE_CODE)
            }
            composable(Routes.PAIR_QR) {
                PairDeviceScreen(onBack = safePopBack, initialMode = PairMode.QR_SCAN)
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onBack = safePopBack)
            }
            composable(Routes.FILE_EXPLORER) { backStack ->
                val deviceId = backStack.arguments?.getString("deviceId") ?: ""
                FileExplorerScreen(
                    deviceId = deviceId,
                    onFileClick = { fileId -> navController.navigate(Routes.filePreview(fileId)) },
                    onBack = safePopBack
                )
            }
            composable(Routes.SEARCH) { backStack ->
                val deviceId = backStack.arguments?.getString("deviceId") ?: ""
                SearchScreen(
                    deviceId = deviceId,
                    onBack = safePopBack,
                    onFileClick = { fileId -> navController.navigate(Routes.filePreview(fileId)) }
                )
            }
            composable(
                route = Routes.FILE_PREVIEW,
                arguments = listOf(navArgument("fileId") { nullable = true; defaultValue = null })
            ) { backStack ->
                val fileId = backStack.arguments?.getString("fileId") ?: ""
                FilePreviewScreen(
                    fileId = fileId,
                    onBack = safePopBack
                )
            }
            composable(Routes.STORAGE) { backStack ->
                val deviceId = backStack.arguments?.getString("deviceId") ?: ""
                StorageScreen(
                    deviceId = deviceId,
                    onBack = safePopBack
                )
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(onBack = safePopBack)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = safePopBack,
                    onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) }
                )
            }
            composable(Routes.ONBOARDING) {
                com.homeport.app.ui.screens.onboarding.OnboardingScreen(
                    onFinish = safePopBack
                )
            }
            composable(Routes.SHARED_FOLDERS) {
                SharedFoldersScreen(onBack = safePopBack)
            }
            composable(Routes.PERMISSIONS) { backStack ->
                val deviceId = backStack.arguments?.getString("deviceId") ?: ""
                PermissionsScreen(
                    deviceId = deviceId,
                    onBack = safePopBack
                )
            }
        }

        // ── Dynamic Island Overlay (Top-Center Apple Pill) ───────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 12.dp)
                .zIndex(999f)
        ) {
            val context = LocalContext.current
            val client = remember { HomePortClient.getInstance(context) }
            CoolDynamicIslandHost(
                client = client,
                navController = navController
            )
        }

        // Floating bubble nav bar — rendered on top of content (widened horizontally)
        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 20.dp)
                    .navigationBarsPadding()
            ) {
                HomePortBubbleNav(navController)
            }
        }
    }
}

// ─── Premium Obsidian Island Navigation Bar ───────────────────────────────────
@Composable
fun HomePortBubbleNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val context = LocalContext.current
    val client = remember { HomePortClient.getInstance(context) }
    val liveTransfers by client.activeTransfers.collectAsState()
    val activeTransferCount = liveTransfers.count {
        it.status == com.homeport.app.domain.model.TransferStatus.ACTIVE
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        // Glass island background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF161618).copy(alpha = 0.96f),
                            Color(0xFF0E0E10).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 0.7.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        )

        // Nav items
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                val badgeCount = if (item.route == Routes.TRANSFERS) activeTransferCount else item.badgeCount

                VoltNavItem(
                    item = item,
                    selected = selected,
                    badgeCount = badgeCount,
                    onClick = {
                        if (currentDestination?.route != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VoltNavItem(
    item: BottomNavItem,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val itemShape = RoundedCornerShape(16.dp)
    val iconSize by animateDpAsState(
        targetValue = if (selected) 23.dp else 21.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "icon_size"
    )

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(itemShape)
            .background(
                if (selected) VoltGlassLight
                else Color.Transparent
            )
            .then(
                if (selected) Modifier.border(0.7.dp, VoltBorder, itemShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = VoltGreen,
                            modifier = Modifier.offset(x = (-2).dp, y = 2.dp)
                        ) {
                            Text(
                                if (badgeCount > 9) "9+" else badgeCount.toString(),
                                color = Color(0xFF0A0A0C),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = if (selected) VoltGreen else White40,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (selected) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        letterSpacing = 0.sp
                    ),
                    color = VoltGreenDim
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// No Device Connected — shown on Files tab when offline
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NoDeviceConnectedScreen(
    onPairDevice: () -> Unit,
    paddingValues: PaddingValues = PaddingValues()
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBase)
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        AtmosphericBackground(modifier = Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Pulsing icon ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size((100 * pulseScale).dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(VoltGreen.copy(alpha = glowAlpha), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A1C22), Color(0xFF0E0F14))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color.White.copy(0.20f), Color.White.copy(0.04f))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.DeviceHub,
                        null,
                        tint = VoltGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "No Device Connected",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Pair with your desktop or another phone to browse, download, and manage files remotely.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(36.dp))

            // Pair button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(listOf(VoltGreen, Color(0xFF9ADB00)))
                    )
                    .clickable(onClick = onPairDevice)
                    .padding(horizontal = 36.dp, vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Outlined.QrCode,
                        null,
                        tint = Color(0xFF080909),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Pair a Device",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = Color(0xFF080909)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Your files will appear here automatically once connected",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

