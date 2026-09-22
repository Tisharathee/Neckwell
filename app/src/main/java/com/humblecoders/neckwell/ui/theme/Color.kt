package com.humblecoders.neckwell.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dark Design System Palette ──────────────────────────────────────────────
// Near-black backgrounds
val NeckWellBackground = Color(0xFF0B0F0E)
val NeckWellSurface = Color(0xFF131A17)
val NeckWellSurfaceVariant = Color(0xFF111815)
val NeckWellSurfaceBright = Color(0xFF1A2320)

// Accent: mint/emerald green
val NeckWellAccent = Color(0xFF3ECF8E)
val NeckWellAccentDark = Color(0xFF2BA86E)
val NeckWellAccentSurface = Color(0xFF162D23)

// Text hierarchy
val NeckWellTextPrimary = Color(0xFFE8EDE9)
val NeckWellTextSecondary = Color(0xFF6B7C74)
val NeckWellTextMuted = Color(0xFF4A5A53)

// Borders & dividers
val NeckWellBorder = Color(0xFF1E2A25)
val NeckWellDivider = Color(0xFF1A2320)

// Semantic colors (tuned for dark backgrounds)
val AlertCoral = Color(0xFFFF6B6B)
val WarningAmber = Color(0xFFFFB347)
val InfoBlue = Color(0xFF4DABF7)
val Purple = Color(0xFFA78BFA)

// ── Legacy aliases (kept for backward compat during migration) ──────────────
val PrimaryBlue = NeckWellAccentDark
val PrimaryBlueDark = NeckWellAccentDark
val AccentTeal = NeckWellAccent
val AccentTealDark = NeckWellAccentDark
val BackgroundGray = NeckWellBackground
val SurfaceWhite = NeckWellSurface
val TextDark = NeckWellTextPrimary
val TextGray = NeckWellTextSecondary
val BorderSoft = NeckWellBorder
val MintSurface = NeckWellAccentSurface

// Default overrides (unused, kept for compile compat)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
