package com.homeport.app.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.homeport.app.R
import com.homeport.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// HOMEPORT — Revolutionary iOS/Apple-Tier Onboarding Experience
// Inspired by: Reference designs (warm gray ambient background, rich floating
// interactive cards, left-aligned bold typography, pure white CTA button).
// ══════════════════════════════════════════════════════════════════════════════

data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val tag: String,
    val slideType: SlideType
)

enum class SlideType {
    DEVICE_MESH,
    SPEED_TRANSFER,
    PRIVACY_VAULT,
    QR_PAIRING
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPageData(
                title = "Two screens.\nOne seamless bridge.",
                subtitle = "Skip the cables and cloud upload limits. Open and manage photos, videos, and multi-gigabyte archives across your hardware in real-time.",
                tag = "SEAMLESS HARDWARE BRIDGE",
                slideType = SlideType.DEVICE_MESH
            ),
            OnboardingPageData(
                title = "Gigabytes in seconds,\nnot hours.",
                subtitle = "Transfer massive 4K videos and entire folders at 100+ MB/s over local Wi-Fi. Zero internet or mobile data required.",
                tag = "100+ MB/s LOCAL SPEED",
                slideType = SlideType.SPEED_TRANSFER
            ),
            OnboardingPageData(
                title = "No passwords.\nNo cloud signup.",
                subtitle = "Hardware-to-hardware cryptographic pairing. Your devices verify each other locally with complete privacy.",
                tag = "ZERO ACCOUNTS NEEDED",
                slideType = SlideType.QR_PAIRING
            ),
            OnboardingPageData(
                title = "Air-Gapped &\nPrivate.",
                subtitle = "Your data never touches a cloud server. Everything is completely encrypted end-to-end between your own devices.",
                tag = "AIR-GAPPED PRIVACY",
                slideType = SlideType.PRIVACY_VAULT
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Pure pitch black as requested
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top Bar: Logo Mark + Skip Button ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.deskward_logo),
                        contentDescription = "DESKWARD",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "DESKWARD",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            fontSize = 13.sp
                        ),
                        color = TextPrimary
                    )
                }

                // Skip button pill
                AnimatedVisibility(
                    visible = pagerState.currentPage < pages.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFF1E2024))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(99.dp))
                            .clickable(onClick = onFinish)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = TextSecondary
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // ── Pager Area (Visual Mockup + Typography) ───────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val pageData = pages[pageIndex]
                OnboardingPageContent(data = pageData)
            }

            // ── Bottom Section: Page Indicators + Pure White CTA Button ───────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp, top = 8.dp)
            ) {
                // Page indicator pills
                Row(
                    modifier = Modifier.padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 7.dp,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                            label = "indicator_w"
                        )
                        Box(
                            modifier = Modifier
                                .width(width)
                                .height(5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (isSelected) Color.White
                                    else Color.White.copy(alpha = 0.22f)
                                )
                        )
                    }
                }

                // PURE WHITE ACTION BUTTON — as requested: "white button increase premium ness"
                val isLast = pagerState.currentPage == pages.size - 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White)
                        .clickable {
                            if (isLast) {
                                onFinish()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = Color(0xFF0C0D10)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF0C0D10),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Encrypted local mesh · No cloud account required",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// OnboardingPageContent — Visual Stage + Left-Aligned Headline Typography
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OnboardingPageContent(data: OnboardingPageData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Visual Mockup Stage (Upper 60%) ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when (data.slideType) {
                SlideType.DEVICE_MESH -> DeviceMeshVisualMockup()
                SlideType.SPEED_TRANSFER -> SpeedTransferVisualMockup()
                SlideType.PRIVACY_VAULT -> PrivacyVaultVisualMockup()
                SlideType.QR_PAIRING -> QrPairingVisualMockup()
            }
        }

        // ── Typography Stage (Bottom 40%) ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Small badge pill (Volt Green)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF141917))
                    .border(1.dp, Color(0xFFC1F800).copy(alpha = 0.35f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC1F800))
                    )
                    Text(
                        text = data.tag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFFC1F800)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Bold headline typography (like reference)
            Text(
                text = data.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    letterSpacing = (-1.0).sp
                ),
                color = TextPrimary
            )

            Spacer(Modifier.height(10.dp))

            // Clean description
            Text(
                text = data.subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                ),
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Visual Mockup 1: Device Mesh Cards (Layered realistic device pills)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DeviceMeshVisualMockup() {
    val infiniteTransition = rememberInfiniteTransition(label = "unified_devices_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the 3D visual (soft, subtle Volt Green)
        Canvas(modifier = Modifier.size(280.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC1F800).copy(alpha = 0.16f),
                        Color(0xFFC1F800).copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width / 2
                ),
                radius = size.width / 2
            )
        }

        // Hero 3D visual render (large and prominent)
        Image(
            painter = painterResource(id = R.drawable.onboarding_unified_devices),
            contentDescription = "Two screens. One seamless bridge.",
            modifier = Modifier
                .width(320.dp)
                .offset(x = 0.dp, y = floatOffset.dp),
            contentScale = ContentScale.Fit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Visual Mockup 2: Speed Transfer (Live transfer card with high MB/s rate)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SpeedTransferVisualMockup() {
    val infiniteTransition = rememberInfiniteTransition(label = "speed_transfer_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_speed"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(335.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the 3D visual (soft, subtle Volt Green)
        Canvas(modifier = Modifier.size(290.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC1F800).copy(alpha = 0.16f),
                        Color(0xFFC1F800).copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width / 2
                ),
                radius = size.width / 2
            )
        }

        // Hero 3D visual render — slightly bigger (315dp height)
        Image(
            painter = painterResource(id = R.drawable.onboarding_speed_transfer),
            contentDescription = "Gigabytes in seconds, not hours.",
            modifier = Modifier
                .height(315.dp)
                .offset(x = 0.dp, y = floatOffset.dp),
            contentScale = ContentScale.Fit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Visual Mockup 3: Air-Gapped Privacy (3D Isolated Shield Bubble Render)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PrivacyVaultVisualMockup() {
    val infiniteTransition = rememberInfiniteTransition(label = "airgap_shield_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_airgap"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the 3D visual (soft, subtle Volt Green)
        Canvas(modifier = Modifier.size(280.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC1F800).copy(alpha = 0.16f),
                        Color(0xFFC1F800).copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width / 2
                ),
                radius = size.width / 2
            )
        }

        // Hero 3D visual render (large and centered)
        Image(
            painter = painterResource(id = R.drawable.onboarding_airgap_privacy),
            contentDescription = "Air-Gapped & Private",
            modifier = Modifier
                .height(280.dp)
                .offset(x = 0.dp, y = floatOffset.dp),
            contentScale = ContentScale.Fit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Visual Mockup 4: QR Pairing Viewfinder
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QrPairingVisualMockup() {
    val infiniteTransition = rememberInfiniteTransition(label = "pairing_scan_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_scan"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(335.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the 3D visual (soft, subtle Volt Green)
        Canvas(modifier = Modifier.size(310.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC1F800).copy(alpha = 0.16f),
                        Color(0xFFC1F800).copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width / 2
                ),
                radius = size.width / 2
            )
        }

        // Hero 3D visual render — slightly bigger (350dp width)
        Image(
            painter = painterResource(id = R.drawable.onboarding_pairing_scan),
            contentDescription = "No passwords. No cloud signup.",
            modifier = Modifier
                .width(350.dp)
                .offset(x = 0.dp, y = floatOffset.dp),
            contentScale = ContentScale.Fit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IntroScreen — Full Pitch Black (#000000) Cinematic Launch Screen
// Center: 3D Wormhole Portal Logo (deskward_logo.png) with gentle breathing green aura
// Below: Aligned DESKWΛRD Wordmark (deskward_wordmark.png)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun IntroScreen(onIntroComplete: () -> Unit) {
    var contentVisible by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "intro_glow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(Unit) {
        delay(120)
        contentVisible = true
        delay(1800) // Display cinematic intro for ~1.8s
        onIntroComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Full pitch black as requested
        contentAlignment = Alignment.Center
    ) {
        // Soft pulsing ambient Volt Green aura behind logo
        Canvas(modifier = Modifier.size(240.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC1F800).copy(alpha = pulseGlow),
                        Color(0xFFC1F800).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width / 2
                ),
                radius = size.width / 2
            )
        }

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = FastOutSlowInEasing), initialScale = 0.90f),
            exit = fadeOut(tween(350))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Small Logo at center (~88dp)
                Image(
                    painter = painterResource(id = R.drawable.deskward_logo),
                    contentDescription = "Deskward Logo",
                    modifier = Modifier.size(88.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.height(24.dp))

                // Under it: The name DESKWARD like attached example (with Volt Green chevron)
                Image(
                    painter = painterResource(id = R.drawable.deskward_wordmark),
                    contentDescription = "DESKWARD",
                    modifier = Modifier.width(235.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    IntroScreen(onIntroComplete = onSplashComplete)
}

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit
) {
    OnboardingScreen(onFinish = onGetStarted)
}
