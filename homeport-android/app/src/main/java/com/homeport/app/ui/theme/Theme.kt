package com.homeport.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// HOMEPORT — Obsidian Black · Volt Green Dark Scheme (v4)
// Primary: VoltGreen (#C8FF00) — statement accent
// Foundation: Void #0A0A0C + Carbon ladder
private val HomePortDarkColorScheme = darkColorScheme(
    primary              = VoltGreen,
    onPrimary            = ActionVoltText,
    primaryContainer     = VoltGlassLight,
    onPrimaryContainer   = VoltGreen,

    secondary            = Color(0xFFE4FF80),           // Light volt
    onSecondary          = ActionVoltText,
    secondaryContainer   = Color(0xFF1A2200),            // Deep volt container
    onSecondaryContainer = VoltGreen,

    tertiary             = SignalAmber,
    onTertiary           = ActionVoltText,
    tertiaryContainer    = Color(0xFF261A00),
    onTertiaryContainer  = SignalAmber,

    background           = Background,                   // #0D0D0F obsidian
    onBackground         = White90,

    surface              = Carbon,                       // #121214 card surface
    onSurface            = White90,
    surfaceVariant       = Charcoal,                     // #1A1A1C elevated
    onSurfaceVariant     = White60,

    outline              = GlassEdge,                    // ~13% hairline
    outlineVariant       = Zinc,

    error                = SignalRed,
    onError              = ActionVoltText,
    errorContainer       = Color(0xFF2D0A0A),
    onErrorContainer     = SignalRed,

    inverseSurface       = White90,
    inverseOnSurface     = Background,
    inversePrimary       = VoltGreenDim,

    scrim                = Color(0xD9000000),            // 85% modal scrim
)

@Composable
fun HomePortTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = HomePortDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Full edge-to-edge, transparent system bars
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = HomePortTypography,
        content     = content
    )
}
