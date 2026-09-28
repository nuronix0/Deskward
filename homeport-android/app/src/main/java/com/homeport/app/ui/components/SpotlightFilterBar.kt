package com.homeport.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeport.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Obsidian Spotlight Filter Bar
 *
 * An executive, cyber-refined segmented filter capsule inspired by high-end dark hardware.
 * Features a glowing micro-LED emitter positioned at the top rim above the active tab,
 * casting a downward conical spotlight beam across the selected option.
 * Glides smoothly between selections using Apple-style spring physics.
 */
@Composable
fun <T> SpotlightFilterBar(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String,
    countProvider: ((T) -> Int?)? = null,
    iconProvider: ((T) -> ImageVector?)? = null,
    spotlightColor: Color = VoltGreen,
    isScrollable: Boolean = false,
    barHeight: Dp = 44.dp
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scrollState = if (isScrollable) rememberScrollState() else null

    // Track layout positions (x-offset and width) of each tab inside the capsule
    val tabPositions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val (targetX, targetWidth) = tabPositions[selectedIndex] ?: Pair(0f, 0f)

    // Smooth liquid spring transition for the spotlight beam & LED emitter
    val animatedX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "spotlightX"
    )
    val animatedWidth by animateFloatAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "spotlightWidth"
    )

    // Auto-scroll selected item into view if scrollable
    LaunchedEffect(selectedIndex, targetX, targetWidth) {
        if (isScrollable && scrollState != null && targetWidth > 0f) {
            val centerTarget = (targetX + targetWidth / 2f) - (scrollState.viewportSize / 2f)
            scrollState.animateScrollTo(centerTarget.toInt().coerceAtLeast(0))
        }
    }

    val capsuleShape = RoundedCornerShape(barHeight / 2)

    // Outer Obsidian Capsule
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(capsuleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF151619),
                        Color(0xFF0C0D0F)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = capsuleShape
            )
    ) {
        // Inner Container for tabs & spotlight canvas
        val contentModifier = if (isScrollable && scrollState != null) {
            Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp)
        } else {
            Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp)
        }

        Box(modifier = contentModifier) {
            // ── Downward Conical Spotlight Canvas & Top LED Emitter ──
            if (animatedWidth > 0f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val centerX = animatedX + animatedWidth / 2f
                    val topWidth = 22.dp.toPx()
                    val bottomWidth = animatedWidth * 1.08f
                    val heightPx = size.height

                    // 1. Subtle ambient backplate pill behind active selection
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.035f),
                        topLeft = Offset(animatedX + 3.dp.toPx(), 4.dp.toPx()),
                        size = Size(animatedWidth - 6.dp.toPx(), heightPx - 8.dp.toPx()),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.02f)
                            )
                        ),
                        topLeft = Offset(animatedX + 3.dp.toPx(), 4.dp.toPx()),
                        size = Size(animatedWidth - 6.dp.toPx(), heightPx - 8.dp.toPx()),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.75.dp.toPx())
                    )

                    // 2. Soft Downward Ambient Spotlight Diffusion (Smooth radial falloff, no harsh polygon edges)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, 0f),
                            radius = heightPx * 1.35f
                        ),
                        radius = heightPx * 1.35f,
                        center = Offset(centerX, 0f)
                    )

                    // 3. Downward Soft Light Wash
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = heightPx * 0.95f
                        ),
                        topLeft = Offset(centerX - animatedWidth * 0.40f, 0f),
                        size = Size(animatedWidth * 0.80f, heightPx),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )

                    // 4. Clean, High-End Micro-LED Slit at top rim
                    val ledWidth = 16.dp.toPx()
                    val ledHeight = 1.8.dp.toPx()

                    // LED Soft Bloom
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(centerX - (ledWidth + 4.dp.toPx()) / 2f, 0f),
                        size = Size(ledWidth + 4.dp.toPx(), ledHeight + 1.2.dp.toPx()),
                        cornerRadius = CornerRadius(1.2.dp.toPx())
                    )

                    // LED Crisp White Slit
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.95f),
                        topLeft = Offset(centerX - ledWidth / 2f, 0f),
                        size = Size(ledWidth, ledHeight),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
            }

            // ── Row of Interactive Filter Tabs ──
            Row(
                modifier = if (isScrollable) Modifier.wrapContentWidth() else Modifier.fillMaxSize(),
                horizontalArrangement = if (isScrollable) Arrangement.spacedBy(4.dp) else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val label = labelProvider(item)
                    val count = countProvider?.invoke(item)
                    val icon = iconProvider?.invoke(item)

                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) White100 else White40,
                        animationSpec = tween(160),
                        label = "textColor"
                    )

                    val itemModifier = if (isScrollable) {
                        Modifier
                            .padding(vertical = 4.dp)
                            .onGloballyPositioned { coords ->
                                tabPositions[index] = Pair(coords.positionInParent().x, coords.size.width.toFloat())
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerFilterHaptic(context, haptic)
                                onItemSelected(item)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coords ->
                                tabPositions[index] = Pair(coords.positionInParent().x, coords.size.width.toFloat())
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerFilterHaptic(context, haptic)
                                onItemSelected(item)
                            }
                    }

                    Box(
                        modifier = itemModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) spotlightColor else White40,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            }

                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    letterSpacing = if (isSelected) 0.3.sp else 0.1.sp
                                ),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (count != null && count > 0) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(
                                            if (isSelected) spotlightColor.copy(alpha = 0.22f)
                                            else Color.White.copy(alpha = 0.08f)
                                        )
                                        .padding(horizontal = 5.5.dp, vertical = 1.5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp
                                        ),
                                        color = if (isSelected) spotlightColor else White40
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun triggerFilterHaptic(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    } catch (_: Exception) {}
}
