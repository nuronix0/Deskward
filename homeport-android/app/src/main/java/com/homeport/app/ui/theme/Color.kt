package com.homeport.app.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
// HOMEPORT — Obsidian + Volt Green Design System (v4 — Livora/Obtic inspired)
// Visual target: Near-black (#0D0D0F), charcoal cards, volt-green accent
// Inspired by: Livora, Obtic, Raycast, Linear — ultra-premium dark UI
// ══════════════════════════════════════════════════════════════════════════════

// ── Foundation (true dark — not just "dark mode gray") ──────────────────────
val Void                = Color(0xFF0A0A0C)   // Deepest bg: near pure black
val Obsidian            = Color(0xFF0D0D0F)   // Default page background
val Carbon              = Color(0xFF121214)   // Card background
val Charcoal            = Color(0xFF1A1A1C)   // Elevated surface (modals, sheets)
val Iron                = Color(0xFF242427)   // Input fields, selected states
val Zinc                = Color(0xFF2E2E32)   // Borders, subtle separators
val Steel               = Color(0xFF3C3C41)   // Disabled, placeholders

// ── Volt Green Accent (THE statement color) ──────────────────────────────────
val VoltGreen           = Color(0xFFC1F800)   // The user's signature Electric Lime Volt Green
val VoltGreenDim        = Color(0xFFA6D400)   // Dimmed variant
val VoltGlow            = Color(0x33C1F800)   // 20% — ambient glow for cards
val VoltGlassLight      = Color(0x18C1F800)   // 10% — light tint for active bg
val VoltBorder          = Color(0x44C1F800)   // 27% — active border

// ── Glass / Transparency Materials ───────────────────────────────────────────
val Glass04             = Color(0x0AFFFFFF)   // 4%  — ultra-subtle fill
val Glass08             = Color(0x14FFFFFF)   // 8%  — primary glass
val Glass12             = Color(0x1FFFFFFF)   // 12% — elevated glass
val Glass20             = Color(0x33FFFFFF)   // 20% — strong highlight
val GlassEdge           = Color(0x22FFFFFF)   // Edge highlight on glass
val GlassEdgeSubtle     = Color(0x0FFFFFFF)   // Hairline divider

// ── Text Hierarchy ────────────────────────────────────────────────────────────
val White100            = Color(0xFFFFFFFF)   // Pure white — hero text
val White90             = Color(0xFFE8E8EA)   // Primary text — nearly white
val White60             = Color(0xFF96969A)   // Secondary text
val White40             = Color(0xFF636367)   // Tertiary / hints
val White20             = Color(0xFF3A3A3E)   // Disabled / ghost

// ── Semantic Accent ───────────────────────────────────────────────────────────
val SignalGreen         = Color(0xFF2DB87B)   // Online / success
val SignalAmber         = Color(0xFFE8B84B)   // Warning / connecting
val SignalRed           = Color(0xFFE85555)   // Error / offline

val SignalGreenGlow     = Color(0x302DB87B)
val SignalAmberGlow     = Color(0x30E8B84B)
val SignalRedGlow       = Color(0x30E85555)

// ── Action Buttons ────────────────────────────────────────────────────────────
val ActionVolt          = VoltGreen           // Primary CTA: volt green fill
val ActionVoltText      = Color(0xFF0A0A0C)   // Black text on volt (readable)
val ActionWhite         = Color(0xFFFFFFFF)   // Secondary CTA: white
val ActionWhiteText     = Color(0xFF0A0A0C)   // Black text on white

// ── Background System ─────────────────────────────────────────────────────────
val Background          = Obsidian
val BackgroundDeep      = Void
val Surface0            = Carbon
val Surface1            = Charcoal
val Surface2            = Iron
val Surface3            = Zinc

// ── File Category Colors ───────────────────────────────────────────────────────
val ColorDocument       = Color(0xFF6B8CC4)
val ColorImage          = Color(0xFF6BAE84)
val ColorVideo          = Color(0xFFC48B5E)
val ColorAudio          = Color(0xFF9B7ABE)
val ColorArchive        = Color(0xFFC4A050)
val ColorCode           = Color(0xFF6BA8C4)
val ColorDatabase       = Color(0xFF5EA8A0)
val ColorApplication    = Color(0xFF7A8AC4)
val ColorDesign         = Color(0xFFC46B84)
val ColorModel3D        = Color(0xFF8A7AC4)
val ColorFont           = Color(0xFFA88E6A)
val ColorConfig         = Color(0xFF707070)
val ColorSecurity       = Color(0xFFC4AF5A)
val ColorSystem         = Color(0xFF585858)
val ColorUnknown        = Color(0xFF3C3C3C)

// ── Legacy compat aliases (keep all old references working) ──────────────────
val SoftEmerald         = VoltGreen
val MintAccent          = VoltGreen
val MintDark            = VoltGreenDim
val MintGlow            = VoltGlow
val MintGlassFill       = VoltGlassLight
val MintBorder          = VoltBorder
val EmeraldDark         = VoltGreenDim
val EmeraldGlow         = VoltGlow
val EmeraldGlassFill    = VoltGlassLight
val EmeraldBorder       = VoltBorder
val HomePortBlue        = VoltGreen
val HomePortBlueDark    = VoltGreenDim
val HomePortBlueLight   = Color(0xFFE4FF80)
val HomePortBlueGlow    = VoltGlow
val HomePortBlueGlowMd  = VoltGlassLight
val HomePortTeal        = VoltGreen
val HomePortPurple      = Color(0xFF888899)
val HomePortIndigo      = Color(0xFF7A7A8A)
val HomePortOrange      = SignalAmber
val StatusTrusted       = SignalGreen
val StatusOnline        = SignalGreen
val StatusConnecting    = SignalAmber
val StatusWarning       = SignalAmber
val StatusError         = SignalRed
val StatusOffline       = White40
val StatusOnlineGlow    = SignalGreenGlow
val StatusConnGlow      = SignalAmberGlow
val StatusErrorGlow     = SignalRedGlow
val GlassFill           = Glass08
val GlassFillSec        = Glass04
val GlassFillElevated   = Glass12
val GlassFillHover      = Glass08
val GlassFillActive     = Glass12
val GlassBorder         = GlassEdge
val GlassBorderSubtle   = GlassEdgeSubtle
val GlassBorderStrong   = GlassEdge
val GlassBorderHigh     = GlassEdge
val GlassBorderLow      = GlassEdgeSubtle
val GlassHighlight      = Glass20
val GlassShadow         = Color(0xC0000000)
val TextPrimary         = White90
val TextSecondary       = White60
val TextTertiary        = White40
val TextDisabled        = White20
val TextOnAction        = ActionVoltText
val TextOnMint          = ActionVoltText
val TextOnAccent        = ActionVoltText
val TextLink            = VoltGreen
val TextQuaternary      = White20
val PureWhite           = White100
val WhiteGlass          = Glass08
val WhiteGlassMid       = Glass12
val ActionSolid         = ActionVolt
val ActionText          = ActionVoltText
val ActionMint          = VoltGreen
val Divider             = GlassEdgeSubtle
val DividerOpaque       = Zinc
val Separator           = GlassEdgeSubtle
val SeparatorOpaque     = Zinc
val ObsidianBase        = Void
val SolidNearWhite      = ActionWhite
val GraphiteLow         = Carbon
val GraphiteLowest      = Void
val GraphiteRaised      = Carbon
val GraphiteHigh        = Charcoal
val GraphiteTop         = Iron
val Graphite            = Obsidian
