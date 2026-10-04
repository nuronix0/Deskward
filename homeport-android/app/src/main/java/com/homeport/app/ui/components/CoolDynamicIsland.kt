package com.homeport.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.homeport.app.domain.model.*
import com.homeport.app.domain.model.TransferDirection
import com.homeport.app.domain.model.TransferItem
import com.homeport.app.domain.model.TransferStatus
import com.homeport.app.network.ConnectionStatus
import com.homeport.app.network.HomePortClient
import com.homeport.app.ui.Routes
import com.homeport.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// DYNAMIC ISLAND STATE MACHINE
// ══════════════════════════════════════════════════════════════════════════════

sealed interface DynamicIslandState {
    /** Stealth / minimal dormant notch pill */
    object Idle : DynamicIslandState

    /** Connected to a local peer */
    data class Connected(
        val deviceName: String,
        val platform: String = "Windows",
        val route: String = "Direct P2P",
        val isDesktop: Boolean = true
    ) : DynamicIslandState

    /** Active file transfer with real-time telemetry */
    data class Transferring(
        val fileName: String,
        val progress: Float, // 0.0f..1.0f
        val bytesTransferred: Long = 0L,
        val totalBytes: Long = 0L,
        val speedFormatted: String = "12.4 MB/s",
        val isUpload: Boolean = false,
        val peerName: String = "DESKTOP"
    ) : DynamicIslandState

    /** Snappy success badge on completed transfers */
    data class Success(
        val message: String = "Transfer Complete",
        val detail: String = "File Saved",
        val filePath: String? = null  // Absolute path of received file (null for uploads)
    ) : DynamicIslandState
}

// ══════════════════════════════════════════════════════════════════════════════
// CONTROLLER (Accessible throughout the app)
// ══════════════════════════════════════════════════════════════════════════════

object DynamicIslandController {
    private val _state = MutableStateFlow<DynamicIslandState>(DynamicIslandState.Idle)
    val state: StateFlow<DynamicIslandState> = _state.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _isSmallest = MutableStateFlow(false)
    val isSmallest: StateFlow<Boolean> = _isSmallest.asStateFlow()

    private val _isScrolling = MutableStateFlow(false)
    val isScrolling: StateFlow<Boolean> = _isScrolling.asStateFlow()

    private val _isHomeScreen = MutableStateFlow(true)
    val isHomeScreen: StateFlow<Boolean> = _isHomeScreen.asStateFlow()

    private val controllerScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob())
    private var autoCollapseJob: Job? = null
    private var scrollResetJob: Job? = null
    private var demoJob: Job? = null

    private fun scheduleAutoCollapse(delayMs: Long = 1800L) {
        autoCollapseJob?.cancel()
        if (delayMs <= 0L) return
        autoCollapseJob = controllerScope.launch {
            delay(delayMs)
            _isExpanded.value = false
        }
    }

    /**
     * Set active route to determine whether to use standard compact pill (Home) or smallest circular icon (other screens).
     */
    fun setScreen(route: String?) {
        val isHome = route == null || route == Routes.HOME
        _isHomeScreen.value = isHome
        if (!isHome) {
            _isSmallest.value = true
        } else {
            _isSmallest.value = false
        }
    }

    /**
     * Active user scroll signal: collapses if expanded, and fades island out so it is not visible while scrolling.
     */
    fun onUserScrolled() {
        if (_isExpanded.value) {
            _isExpanded.value = false
        }
        _isScrolling.value = true
        _isSmallest.value = true
        scrollResetJob?.cancel()
        scrollResetJob = controllerScope.launch {
            delay(1200)
            _isScrolling.value = false
            if (_isHomeScreen.value) {
                _isSmallest.value = false
            }
        }
    }

    fun setSmallest(smallest: Boolean) {
        _isSmallest.value = smallest
    }

    /**
     * When any device connects, island pops up in full size for 1.8s then auto-collapses to compact pill.
     */
    fun showConnected(
        deviceName: String,
        platform: String = "Windows",
        route: String = "Direct P2P",
        isDesktop: Boolean = true,
        autoCollapseMs: Long = 1800L
    ) {
        _state.value = DynamicIslandState.Connected(deviceName, platform, route, isDesktop)
        _isExpanded.value = true
        scheduleAutoCollapse(autoCollapseMs)
    }

    /**
     * Sets Connected state quietly in place (compact/smallest) without popping up the full expanded card.
     */
    fun setConnected(
        deviceName: String,
        platform: String = "Windows",
        route: String = "Direct P2P",
        isDesktop: Boolean = true
    ) {
        autoCollapseJob?.cancel()
        _state.value = DynamicIslandState.Connected(deviceName, platform, route, isDesktop)
        _isExpanded.value = false
    }

    /**
     * Trigger a new transfer notification: pops up in full size for 2.0s then collapses to compact progress pill.
     */
    fun startTransfer(
        fileName: String,
        progress: Float,
        bytesTransferred: Long = 0L,
        totalBytes: Long = 0L,
        speedFormatted: String = "",
        isUpload: Boolean = false,
        peerName: String = "DESKTOP",
        autoCollapseMs: Long = 2000L
    ) {
        _state.value = DynamicIslandState.Transferring(
            fileName = fileName,
            progress = progress.coerceIn(0f, 1f),
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            speedFormatted = speedFormatted,
            isUpload = isUpload,
            peerName = peerName
        )
        _isExpanded.value = true
        scheduleAutoCollapse(autoCollapseMs)
    }

    /**
     * Streaming update for active transfer progress
     */
    fun updateTransfer(
        fileName: String,
        progress: Float,
        bytesTransferred: Long = 0L,
        totalBytes: Long = 0L,
        speedFormatted: String = "",
        isUpload: Boolean = false,
        peerName: String = "DESKTOP",
        autoCollapseMs: Long = 2000L
    ) {
        val wasTransferring = _state.value is DynamicIslandState.Transferring
        _state.value = DynamicIslandState.Transferring(
            fileName = fileName,
            progress = progress.coerceIn(0f, 1f),
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            speedFormatted = speedFormatted,
            isUpload = isUpload,
            peerName = peerName
        )
        if (!wasTransferring) {
            _isExpanded.value = true
            scheduleAutoCollapse(autoCollapseMs)
        }
    }

    /**
     * When a transfer completes, island pops up in full size for 2.0s then auto-collapses to compact pill.
     */
    fun showSuccess(
        message: String = "Transfer Complete",
        detail: String = "",
        filePath: String? = null,
        autoCollapseMs: Long = 2000L
    ) {
        _state.value = DynamicIslandState.Success(message, detail, filePath)
        _isExpanded.value = true
        scheduleAutoCollapse(autoCollapseMs)
    }

    fun setIdle() {
        autoCollapseJob?.cancel()
        _state.value = DynamicIslandState.Idle
        _isExpanded.value = false
    }

    fun toggleExpand() {
        autoCollapseJob?.cancel()
        _isExpanded.value = !_isExpanded.value
    }

    fun expand() {
        autoCollapseJob?.cancel()
        _isExpanded.value = true
    }

    fun collapse() {
        autoCollapseJob?.cancel()
        _isExpanded.value = false
    }

    /** Interactive simulation showcasing Apple-style popups across all lifecycle states */
    fun runDemoSimulation(scope: CoroutineScope) {
        demoJob?.cancel()
        demoJob = scope.launch {
            // 1. Device Connected: pops up in FULL size for 1.8s, then smoothly collapses to compact pill
            showConnected(
                deviceName = "DESKTOP-P3AM34E",
                platform = "Windows 11",
                route = "Direct P2P LAN",
                isDesktop = true,
                autoCollapseMs = 1800L
            )
            delay(3200)

            // 2. Transfer Starts: pops up in FULL size for 2.0s, then collapses to compact progress pill
            val totalBytes = 148_500_000L
            val steps = 36
            for (i in 1..steps) {
                val progress = i / steps.toFloat()
                val currentBytes = (totalBytes * progress).toLong()
                val speed = 28.4f + (Math.sin(i.toDouble() * 0.5) * 4.5).toFloat()
                updateTransfer(
                    fileName = "Video_4K_HDR.mp4",
                    progress = progress,
                    bytesTransferred = currentBytes,
                    totalBytes = totalBytes,
                    speedFormatted = String.format(java.util.Locale.US, "%.1f MB/s", speed),
                    isUpload = false,
                    peerName = "DESKTOP-P3AM34E",
                    autoCollapseMs = 2000L
                )
                delay(85)
            }

            // 3. Transfer Complete: pops up in FULL size for 2.0s, then collapses to compact pill
            showSuccess(
                message = "Transfer Complete",
                detail = "1.0 GB Saved",
                autoCollapseMs = 2000L
            )
            delay(3400)

            // 4. Return to connected compact pill
            showConnected(
                deviceName = "DESKTOP-P3AM34E",
                platform = "Windows 11",
                route = "Direct P2P LAN",
                isDesktop = true,
                autoCollapseMs = 0L
            )
            collapse()
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// COOL DYNAMIC ISLAND COMPOSABLE
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CoolDynamicIsland(
    state: DynamicIslandState,
    isExpanded: Boolean,
    isSmallest: Boolean = false,
    isScrolling: Boolean = false,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // ── Tactile Spring Physics Specs ─────────────────────────────────────────
    // Damping ~ 0.72f, Stiffness ~ 520f produces the signature Apple liquid-spring settle
    val islandSpringDp = remember {
        spring<Dp>(
            dampingRatio = 0.72f,
            stiffness = 520f
        )
    }

    // ── Dimensions Matrix (Smallest vs Compact vs Expanded) ───────────────────
    val (targetWidth, targetHeight, targetCorner) = remember(state, isExpanded, isSmallest) {
        when (state) {
            is DynamicIslandState.Idle -> {
                Triple(0.dp, 0.dp, 0.dp)
            }
            is DynamicIslandState.Connected -> {
                if (isExpanded) Triple(358.dp, 104.dp, 28.dp)
                else if (isSmallest) Triple(38.dp, 38.dp, 19.dp)
                else Triple(152.dp, 36.dp, 18.dp)
            }
            is DynamicIslandState.Transferring -> {
                if (isExpanded) Triple(358.dp, 144.dp, 28.dp)
                else if (isSmallest) Triple(38.dp, 38.dp, 19.dp)
                else Triple(156.dp, 36.dp, 18.dp)
            }
            is DynamicIslandState.Success -> {
                if (isExpanded) Triple(358.dp, 104.dp, 28.dp)
                else if (isSmallest) Triple(38.dp, 38.dp, 19.dp)
                else Triple(196.dp, 36.dp, 18.dp)
            }
        }
    }

    val animatedWidth by animateDpAsState(targetWidth, islandSpringDp, label = "islandWidth")
    val animatedHeight by animateDpAsState(targetHeight, islandSpringDp, label = "islandHeight")
    val animatedCorner by animateDpAsState(targetCorner, islandSpringDp, label = "islandCorner")

    // If completely idle/disconnected and spring settled, do not render or intercept touches
    if (state is DynamicIslandState.Idle && animatedHeight <= 1.5.dp) {
        return
    }

    val islandAlpha by animateFloatAsState(
        targetValue = if (state is DynamicIslandState.Idle || isScrolling) 0f else 1f,
        animationSpec = tween(140),
        label = "islandAlpha"
    )

    // ── Tactile Impulse Scale Bounce (Liquid Mercury Pop) ────────────────────
    val scaleAnim = remember { Animatable(1f) }
    LaunchedEffect(state::class, isExpanded, isSmallest) {
        if (state !is DynamicIslandState.Idle) {
            triggerHaptic(context, haptic)
            scaleAnim.animateTo(0.94f, animationSpec = tween(40, easing = FastOutSlowInEasing))
            scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f))
        }
    }

    // ── Vertical Drag Gesture with Spring Resistance ─────────────────────────
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "dragSpring"
    )

    val currentShape = RoundedCornerShape(animatedCorner)

    // Dynamic Island container — Pure Apple Pitch Black with subtle hairline edge
    Box(
        modifier = modifier
            .offset(y = (animatedDragY / 2.5f).dp)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                alpha = islandAlpha
            }
            .width(animatedWidth)
            .height(animatedHeight)
            .shadow(
                elevation = if (isExpanded) 18.dp else 4.dp,
                shape = currentShape,
                ambientColor = Color.Black.copy(alpha = 0.95f),
                spotColor = Color.Black.copy(alpha = 0.9f)
            )
            .clip(currentShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000000),
                        Color(0xFF0A0A0C)
                    )
                )
            )
            .border(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = currentShape
            )
            .pointerInput(state, isExpanded, isSmallest) {
                detectTapGestures(
                    onTap = {
                        if (state !is DynamicIslandState.Idle) {
                            try {
                                val sm = com.homeport.app.util.SoundManager.getInstance(context)
                                if (isExpanded) sm.playCollapse() else sm.playExpand()
                            } catch (_: Exception) {}
                            onToggleExpand()
                        }
                    },
                    onLongPress = {
                        triggerHaptic(context, haptic)
                        if (state !is DynamicIslandState.Idle) {
                            try {
                                val sm = com.homeport.app.util.SoundManager.getInstance(context)
                                if (isExpanded) sm.playCollapse() else sm.playExpand()
                            } catch (_: Exception) {}
                            onToggleExpand()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY = (dragOffsetY + dragAmount * 0.45f).coerceIn(-40f, 60f)
                    },
                    onDragEnd = {
                        if (dragOffsetY < -20f) {
                            // Swiped up -> collapse or dismiss
                            if (isExpanded) onToggleExpand() else onDismiss()
                        } else if (dragOffsetY > 25f && !isExpanded && state !is DynamicIslandState.Idle) {
                            // Swiped down -> expand
                            onToggleExpand()
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f }
                )
            },
        contentAlignment = Alignment.Center
    ) {

        // Animated Content Transition across states — text and content bloom out from the center
        AnimatedContent(
            targetState = Triple(state, isExpanded, isSmallest),
            contentKey = { (s, exp, sm) ->
                val kind = when (s) {
                    is DynamicIslandState.Idle -> "Idle"
                    is DynamicIslandState.Connected -> "Connected"
                    is DynamicIslandState.Transferring -> "Transferring"
                    is DynamicIslandState.Success -> "Success"
                }
                "$kind-$exp-$sm"
            },
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                 scaleIn(
                     initialScale = 0.65f,
                     transformOrigin = TransformOrigin.Center,
                     animationSpec = spring(dampingRatio = 0.70f, stiffness = 450f)
                 ))
                .togetherWith(
                    fadeOut(animationSpec = tween(100, easing = FastOutLinearInEasing)) +
                    scaleOut(
                        targetScale = 0.78f,
                        transformOrigin = TransformOrigin.Center,
                        animationSpec = tween(100)
                    )
                ).using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.Center,
            label = "islandContent"
        ) { (currentState, expanded, smallest) ->
            when (currentState) {
                is DynamicIslandState.Idle -> {
                    IdleNotchContent()
                }
                is DynamicIslandState.Connected -> {
                    if (expanded) {
                        ExpandedConnectedContent(
                            state = currentState,
                            onActionClick = onActionClick,
                            onCollapse = onToggleExpand
                        )
                    } else if (smallest) {
                        SmallestConnectedContent(state = currentState)
                    } else {
                        CompactConnectedContent(state = currentState)
                    }
                }
                is DynamicIslandState.Transferring -> {
                    if (expanded) {
                        ExpandedTransferContent(
                            state = currentState,
                            onActionClick = onActionClick,
                            onCollapse = onToggleExpand
                        )
                    } else if (smallest) {
                        SmallestTransferContent(state = currentState)
                    } else {
                        CompactTransferContent(state = currentState)
                    }
                }
                is DynamicIslandState.Success -> {
                    if (expanded) {
                        ExpandedSuccessContent(
                            state = currentState,
                            onActionClick = onActionClick,
                            onCollapse = onToggleExpand
                        )
                    } else if (smallest) {
                        SmallestSuccessContent(state = currentState)
                    } else {
                        CompactSuccessContent(state = currentState)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// COMPACT CONTENT SUB-COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

/** Idle minimal notch indicator (completely invisible when not connected) */
@Composable
private fun IdleNotchContent() {
    Spacer(Modifier.size(0.dp))
}

/** Compact Connected Pill — hardware-grade with platform eyebrow */
@Composable
private fun CompactConnectedContent(state: DynamicIslandState.Connected) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Pulsing green live beacon
        PulsingBeaconDot(size = 5.dp, pulseColor = SignalGreen)

        // Mid-Center: Device icon + Device name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 7.dp)
        ) {
            // Frosted device icon micro-pill
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(VoltGlassLight)
                    .border(0.5.dp, VoltBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isDesktop) Icons.Outlined.Laptop else Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = VoltGreen,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = state.deviceName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = White100,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right: Live shield icon
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = VoltGreen.copy(alpha = 0.8f),
            modifier = Modifier.size(11.dp)
        )
    }
}

/** Compact Transfer Pill */
@Composable
private fun CompactTransferContent(state: DynamicIslandState.Transferring) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Mini circular progress ring with animated arrow inside
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressRing(
                progress = state.progress,
                size = 22.dp,
                strokeWidth = 2.2.dp,
                color = VoltGreen,
                trackColor = Color(0xFF222428)
            )
            Icon(
                imageVector = if (state.isUpload) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = VoltGreen,
                modifier = Modifier.size(10.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        // Center: Live network waveform + Speed
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            LiveWaveformBars(barCount = 3, color = VoltGreen)
            Spacer(Modifier.width(5.dp))
            Text(
                text = state.speedFormatted.ifEmpty { "${(state.progress * 100).toInt()}%" },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = White90,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(6.dp))

        // Right: Percentage chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(VoltGlassLight)
                .border(0.6.dp, VoltBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = VoltGreen
            )
        }
    }
}

/** Compact Success Pill — volt check + detail (never wraps) */
@Composable
private fun CompactSuccessContent(state: DynamicIslandState.Success) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Check in volt pill + label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(listOf(VoltGreen, Color(0xFFE8FF60)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF0A0A0C),
                    modifier = Modifier.size(11.dp)
                )
            }

            Spacer(Modifier.width(7.dp))

            Text(
                text = "Done",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.1.sp
                ),
                color = White100,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // Right: detail text (e.g. "1.0 GB Saved")
        Text(
            text = state.detail.ifEmpty { "Saved" },
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp
            ),
            color = VoltGreen,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SMALLEST CIRCULAR ICON CONTENT SUB-COMPONENTS (38dp capsule for sub-screens & scrolling)
// ══════════════════════════════════════════════════════════════════════════════

/** Smallest Circular Icon for Connected State */
@Composable
private fun SmallestConnectedContent(state: DynamicIslandState.Connected) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (state.isDesktop) Icons.Outlined.Laptop else Icons.Outlined.Smartphone,
            contentDescription = null,
            tint = VoltGreen,
            modifier = Modifier.size(17.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 5.dp)
        ) {
            PulsingBeaconDot(size = 4.dp, pulseColor = SignalGreen)
        }
    }
}

/** Smallest Circular Icon for Transferring State (Live arc progress + direction arrow) */
@Composable
private fun SmallestTransferContent(state: DynamicIslandState.Transferring) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressRing(
            progress = state.progress,
            size = 28.dp,
            strokeWidth = 2.4.dp,
            color = VoltGreen,
            trackColor = Color(0xFF222428)
        )
        Icon(
            imageVector = if (state.isUpload) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
            contentDescription = null,
            tint = VoltGreen,
            modifier = Modifier.size(12.dp)
        )
    }
}

/** Smallest Circular Icon for Success State (Crisp check circle) */
@Composable
private fun SmallestSuccessContent(state: DynamicIslandState.Success) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(VoltGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = ActionVoltText,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// EXPANDED LIVE ACTIVITY CONTENT SUB-COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

/** Expanded Live Activity Card for File Transfer */
@Composable
private fun ExpandedTransferContent(
    state: DynamicIslandState.Transferring,
    onActionClick: (() -> Unit)?,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row: Peer details & Speed chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(VoltGlassLight)
                        .border(0.6.dp, VoltBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isUpload) Icons.Outlined.Upload else Icons.Outlined.Download,
                        contentDescription = null,
                        tint = VoltGreen,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (state.isUpload) "SENDING TO" else "RECEIVING FROM",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                        color = White40
                    )
                    Text(
                        text = state.peerName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = White90
                    )
                }
            }

            // Speed pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VoltGlassLight)
                    .border(0.6.dp, VoltBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveWaveformBars(barCount = 4, color = VoltGreen)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.speedFormatted.ifEmpty { "Transferring..." },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = VoltGreen
                    )
                }
            }
        }

        // File info & stats
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = state.fileName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = White100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = VoltGreen
                )
            }

            Spacer(Modifier.height(8.dp))

            // Smooth spring-animated progress bar
            val animatedProgress by animateFloatAsState(
                targetValue = state.progress,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
                label = "smoothBar"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF202226))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(VoltGreenDim, VoltGreen)
                            )
                        )
                )
            }
        }

        // Bottom actions row (No minimize button, spacious and clean)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.totalBytes > 0) {
                    "${formatFileSize(state.bytesTransferred)} / ${formatFileSize(state.totalBytes)}"
                } else {
                    "Direct P2P Stream"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = White60
            )

            // Action / Details button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoltGreen)
                    .clickable {
                        try {
                            com.homeport.app.util.SoundManager.getInstance(context).playTap()
                        } catch (_: Exception) {}
                        onActionClick?.invoke()
                    }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    color = ActionVoltText
                )
            }
        }
    }
}

/** Expanded Live Activity Card for Connected Device — Hardware Handshake Confirmed */
@Composable
private fun ExpandedConnectedContent(
    state: DynamicIslandState.Connected,
    onActionClick: (() -> Unit)?,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current

    // Ambient volt glow pulsing in background
    val infiniteTransition = rememberInfiniteTransition(label = "connected_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Atmospheric ambient glow behind icon
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC1F800).copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.5f),
                    radius = size.height * 1.1f
                ),
                radius = size.height * 1.1f,
                center = Offset(size.width * 0.18f, size.height * 0.5f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Specular device icon badge
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E2116),
                                Color(0xFF131412)
                            )
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            0.0f to VoltGreen.copy(alpha = 0.45f),
                            0.6f to VoltGreen.copy(alpha = 0.10f),
                            1.0f to Color.Transparent
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isDesktop) Icons.Outlined.Laptop else Icons.Outlined.Smartphone,
                    contentDescription = null,
                    tint = VoltGreen,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Center: Device info column
            Column(modifier = Modifier.weight(1f)) {
                // Monospace platform eyebrow (Warp-style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PulsingBeaconDot(size = 5.dp, pulseColor = SignalGreen)
                    Text(
                        text = state.platform.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = VoltGreen.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = state.deviceName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = White100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                // Route metadata in monospace
                Text(
                    text = state.route,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color = White40,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            // Right: Volt CTA pill (gradient)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(VoltGreen, Color(0xFFE8FF60))
                        )
                    )
                    .clickable {
                        try {
                            com.homeport.app.util.SoundManager.getInstance(context).playTap()
                        } catch (_: Exception) {}
                        onActionClick?.invoke() ?: onCollapse()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = Color(0xFF0A0A0C)
                )
            }
        }
    }
}

/** Expanded Success Content — transfer completion with premium atmospheric treatment */
@Composable
private fun ExpandedSuccessContent(
    state: DynamicIslandState.Success,
    onActionClick: (() -> Unit)?,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current

    // Radial glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "success_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "success_glow_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Volt glow behind check icon
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC1F800).copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.5f),
                    radius = size.height * 1.0f
                ),
                radius = size.height * 1.0f,
                center = Offset(size.width * 0.18f, size.height * 0.5f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: check icon in frosted glass pill
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E2116),
                                Color(0xFF131412)
                            )
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            0.0f to VoltGreen.copy(alpha = 0.50f),
                            0.7f to VoltGreen.copy(alpha = 0.12f),
                            1.0f to Color.Transparent
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = VoltGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Center: message + detail
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRANSFER COMPLETE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = VoltGreen.copy(alpha = 0.7f),
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = White100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.detail.ifEmpty { "Air-gapped transfer completed" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color = White40,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Right: action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                // "Open" — only shown when we have a file path (received file)
                if (state.filePath != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(VoltGreen, Color(0xFFE8FF60))
                                )
                            )
                            .clickable {
                                try {
                                    com.homeport.app.util.SoundManager.getInstance(context).playTap()
                                } catch (_: Exception) {}
                                // Open the received file with the system viewer
                                try {
                                    val file = java.io.File(state.filePath)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val mime = context.contentResolver.getType(uri) ?: "*/*"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                        .setDataAndType(uri, mime)
                                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
                                } catch (e: Exception) {
                                    android.util.Log.w("Island", "Cannot open file: ${e.message}")
                                    onActionClick?.invoke()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF0A0A0C)
                        )
                    }
                }

                // "Done" — always present, collapses the island
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoltGlassLight)
                        .border(0.8.dp, VoltBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            try {
                                com.homeport.app.util.SoundManager.getInstance(context).playCollapse()
                            } catch (_: Exception) {}
                            onCollapse()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = VoltGreen
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// MICRO-ANIMATION UTILITIES (Equalizer, Pulse Beacon, Arc Ring)
// ══════════════════════════════════════════════════════════════════════════════

/** Pulsing Concentric Beacon Dot */
@Composable
fun PulsingBeaconDot(
    size: Dp = 6.dp,
    pulseColor: Color = VoltGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beaconPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        // Outer animated ring
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .clip(CircleShape)
                .background(pulseColor)
        )
        // Solid core dot
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(pulseColor)
        )
    }
}

/** Animated Live Waveform Equalizer Bars */
@Composable
fun LiveWaveformBars(
    barCount: Int = 3,
    color: Color = VoltGreen
) {
    val transition = rememberInfiniteTransition(label = "waveform")

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val heightFraction by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(380 + (index * 130), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height((3.dp + (8.dp * heightFraction)))
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Custom Canvas Circular Progress Ring with ambient glow */
@Composable
fun CircularProgressRing(
    progress: Float,
    size: Dp = 26.dp,
    strokeWidth: Dp = 2.5.dp,
    color: Color = VoltGreen,
    trackColor: Color = Color(0xFF222428)
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "arcProgress"
    )

    Canvas(modifier = Modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val diameter = size.toPx() - strokePx
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)
        val arcSize = Size(diameter, diameter)

        // Background Track
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Active Arc
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DYNAMIC ISLAND HOST (Observes HomePortClient & Auto-Syncs)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CoolDynamicIslandHost(
    client: HomePortClient,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controllerState by DynamicIslandController.state.collectAsState()
    val controllerExpanded by DynamicIslandController.isExpanded.collectAsState()
    val controllerSmallest by DynamicIslandController.isSmallest.collectAsState()
    val controllerScrolling by DynamicIslandController.isScrolling.collectAsState()

    // Observe current navigation destination to sync smallest circular icon on non-home screens
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        DynamicIslandController.setScreen(currentRoute)
    }

    // Observe live real network state from HomePortClient and AndroidMeshServerManager
    val meshServerManager = remember { com.homeport.app.network.AndroidMeshServerManager.getInstance(context) }
    val incomingClients by meshServerManager.incomingClients.collectAsState()
    val connectedDevices by client.connectedDevices.collectAsState()
    val connectedDevice by client.connectedDevice.collectAsState()
    val activeTransfers by client.activeTransfers.collectAsState()
    val connectionStatus by client.connectionStatus.collectAsState()

    val effectivePrimaryDevice = remember(connectedDevices, incomingClients, connectedDevice) {
        connectedDevices.firstOrNull()
            ?: incomingClients.firstOrNull()?.let {
                DeviceInfo(
                    id = it.id,
                    name = it.name,
                    platform = it.platform,
                    type = if (it.platform.contains("Windows", true) || it.platform.contains("mac", true)) DeviceType.DESKTOP else DeviceType.PHONE,
                    status = DeviceStatus.ONLINE,
                    storageTotal = 0L,
                    storageUsed = 0L,
                    lastSeen = it.connectedAt,
                    isTrusted = true,
                    connectionRoute = ConnectionRoute.DIRECT_P2P,
                    permissions = setOf(Permission.READ, Permission.DOWNLOAD, Permission.UPLOAD)
                )
            }
            ?: connectedDevice
    }

    val isEffectivelyConnected = connectionStatus == ConnectionStatus.CONNECTED ||
        connectedDevices.isNotEmpty() ||
        incomingClients.isNotEmpty() ||
        connectedDevice != null

    // Determine primary active transfer
    val liveTransfer = activeTransfers.firstOrNull {
        it.status == TransferStatus.ACTIVE
    }

    // Auto-update Dynamic Island state based on network traffic
    LaunchedEffect(liveTransfer, connectedDevices, incomingClients, connectionStatus) {
        if (liveTransfer != null) {
            val progress = if (liveTransfer.fileSize > 0) {
                liveTransfer.bytesTransferred.toFloat() / liveTransfer.fileSize
            } else 0f

            DynamicIslandController.updateTransfer(
                fileName = liveTransfer.fileName,
                progress = progress,
                bytesTransferred = liveTransfer.bytesTransferred,
                totalBytes = liveTransfer.fileSize,
                speedFormatted = if (liveTransfer.speedBps > 0) "${formatFileSize(liveTransfer.speedBps)}/s" else "Connecting...",
                isUpload = liveTransfer.direction == TransferDirection.UPLOAD,
                peerName = effectivePrimaryDevice?.name ?: "Connected Peer"
            )
        } else {
            // Check if a transfer just completed
            val recentCompleted = activeTransfers.firstOrNull { it.status == TransferStatus.COMPLETED }
            if (recentCompleted != null && controllerState is DynamicIslandState.Transferring) {
                DynamicIslandController.showSuccess(
                    message = "Transfer Complete",
                    detail = formatFileSize(recentCompleted.fileSize),
                    filePath = if (recentCompleted.direction == TransferDirection.UPLOAD) null
                               else recentCompleted.localFilePath,
                    autoCollapseMs = 1800L
                )
                delay(4000L)
                // 3-5 seconds after completion, if no new transfer active, revert process icon quietly to normal connected icon
                val isTransferStillActive = client.activeTransfers.value.any { it.status == TransferStatus.ACTIVE }
                if (!isTransferStillActive) {
                    val primary = effectivePrimaryDevice
                    if (primary != null && isEffectivelyConnected) {
                        DynamicIslandController.setConnected(
                            deviceName = primary.name,
                            platform = primary.platform,
                            isDesktop = primary.type == DeviceType.DESKTOP
                        )
                    } else {
                        DynamicIslandController.setIdle()
                    }
                }
            } else if (controllerState is DynamicIslandState.Idle || controllerState is DynamicIslandState.Connected) {
                val primaryDevice = effectivePrimaryDevice
                if (primaryDevice != null && isEffectivelyConnected) {
                    if (controllerState !is DynamicIslandState.Connected) {
                        DynamicIslandController.showConnected(
                            deviceName = primaryDevice.name,
                            platform = primaryDevice.platform,
                            isDesktop = primaryDevice.type == DeviceType.DESKTOP
                        )
                    }
                } else if (controllerState !is DynamicIslandState.Idle) {
                    DynamicIslandController.setIdle()
                }
            }
        }
    }

    val soundManager = remember { com.homeport.app.util.SoundManager.getInstance(context) }
    var previousState by remember { mutableStateOf<DynamicIslandState>(DynamicIslandState.Idle) }

    // Play tactile sound effects on island lifecycle events
    LaunchedEffect(controllerState) {
        val prev = previousState
        val current = controllerState
        if (prev != current) {
            when {
                current is DynamicIslandState.Connected && prev !is DynamicIslandState.Connected && prev !is DynamicIslandState.Success -> {
                    soundManager.playConnect()
                }
                prev is DynamicIslandState.Connected && current is DynamicIslandState.Idle -> {
                    soundManager.playDisconnect()
                }
                prev !is DynamicIslandState.Transferring && current is DynamicIslandState.Transferring -> {
                    soundManager.playTransferStart()
                }
                current is DynamicIslandState.Success && prev !is DynamicIslandState.Success -> {
                    soundManager.playSuccess()
                }
            }
            previousState = current
        }
    }

    CoolDynamicIsland(
        state = controllerState,
        isExpanded = controllerExpanded,
        isSmallest = controllerSmallest,
        isScrolling = controllerScrolling,
        onToggleExpand = {
            if (controllerExpanded) {
                soundManager.playCollapse()
            } else {
                soundManager.playExpand()
            }
            DynamicIslandController.toggleExpand()
        },
        onDismiss = {
            soundManager.playCollapse()
            DynamicIslandController.collapse()
        },
        onActionClick = {
            soundManager.playTap()
            when (controllerState) {
                is DynamicIslandState.Transferring -> {
                    navController.navigate(Routes.TRANSFERS) {
                        launchSingleTop = true
                    }
                    DynamicIslandController.collapse()
                }
                is DynamicIslandState.Connected -> {
                    val dev = connectedDevices.firstOrNull()
                    if (dev != null) {
                        navController.navigate(Routes.fileExplorer(dev.id)) {
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate(Routes.DEVICES) {
                            launchSingleTop = true
                        }
                    }
                    DynamicIslandController.collapse()
                }
                is DynamicIslandState.Success -> {
                    navController.navigate(Routes.TRANSFERS) {
                        launchSingleTop = true
                    }
                    DynamicIslandController.collapse()
                }
                else -> {}
            }
        },
        modifier = modifier
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// HELPER UTILITIES
// ══════════════════════════════════════════════════════════════════════════════

private fun triggerHaptic(context: Context, composeHaptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    } catch (_: Exception) {}
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
}
