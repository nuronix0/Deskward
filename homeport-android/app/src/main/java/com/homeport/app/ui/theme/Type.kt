package com.homeport.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// craft/typography.md rules applied:
//   • 3-weight system: 400 (read), 500 (emphasize), 600 (announce)
//   • Display ≥32sp: negative tracking −0.02em to −0.03em (Latin)
//   • Body 15–18sp: line-height 1.5–1.6
//   • ALL CAPS labels: ≥0.06em tracking (enforced at use site)
//   • Max 6 type sizes above the fold

// Using system default which is close to SF Pro on Android.
// For production, embed Inter or SF Pro font files.
val HomePortFontFamily = FontFamily.Default

val HomePortTypography = Typography(

    // ── Display — cinematic hero titles ─────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.SemiBold,   // 600 — announce tier
        fontSize    = 48.sp,
        lineHeight  = 52.sp,                 // tight: 1.08 ratio
        letterSpacing = (-1.44).sp,          // −0.03em at 48sp
        color       = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 36.sp,
        lineHeight  = 40.sp,
        letterSpacing = (-0.72).sp,          // −0.02em at 36sp
        color       = TextPrimary
    ),
    displaySmall = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 30.sp,
        lineHeight  = 36.sp,
        letterSpacing = (-0.60).sp,          // −0.02em at 30sp
        color       = TextPrimary
    ),

    // ── Headline — section headers ───────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 26.sp,
        lineHeight  = 32.sp,
        letterSpacing = (-0.52).sp,          // −0.02em at 26sp
        color       = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 22.sp,
        lineHeight  = 28.sp,
        letterSpacing = (-0.44).sp,          // −0.02em at 22sp
        color       = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 19.sp,
        lineHeight  = 24.sp,
        letterSpacing = (-0.19).sp,          // −0.01em at 19sp
        color       = TextPrimary
    ),

    // ── Title — card titles, list section headers ────────────────────────────
    titleLarge = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.SemiBold,   // 600
        fontSize    = 17.sp,
        lineHeight  = 22.sp,
        letterSpacing = (-0.17).sp,          // −0.01em
        color       = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Medium,     // 500 — emphasize tier
        fontSize    = 15.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.sp,                // 0 at body-adjacent sizes
        color       = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 13.sp,
        lineHeight  = 18.sp,
        letterSpacing = 0.sp,
        color       = TextPrimary
    ),

    // ── Body — content text ──────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Normal,     // 400 — read tier
        fontSize    = 17.sp,
        lineHeight  = 26.sp,                 // 1.53 ratio — comfortable body
        letterSpacing = (-0.17).sp,
        color       = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 15.sp,
        lineHeight  = 22.sp,                 // 1.47 ratio
        letterSpacing = 0.sp,
        color       = TextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 13.sp,
        lineHeight  = 18.sp,                 // 1.38 ratio
        letterSpacing = 0.sp,
        color       = TextSecondary
    ),

    // ── Label — captions, badges, UI controls ───────────────────────────────
    labelLarge = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Medium,     // 500 — emphasize tier
        fontSize    = 13.sp,
        lineHeight  = 18.sp,
        letterSpacing = 0.sp,
        color       = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 14.sp,
        letterSpacing = 0.22.sp,             // +0.02em for small text readability
        color       = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily  = HomePortFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 10.sp,
        lineHeight  = 14.sp,
        letterSpacing = 0.60.sp,             // +0.06em — required for ALL CAPS labels
        color       = TextSecondary
    ),
)


