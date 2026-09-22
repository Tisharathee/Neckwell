package com.humblecoders.neckwell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val DarkColorScheme = darkColorScheme(
    primary = NeckWellAccent,
    onPrimary = Color(0xFF0B0F0E),
    primaryContainer = NeckWellAccentDark,
    onPrimaryContainer = NeckWellTextPrimary,

    secondary = NeckWellAccentDark,
    onSecondary = NeckWellTextPrimary,
    secondaryContainer = NeckWellAccentSurface,
    onSecondaryContainer = NeckWellAccent,

    tertiary = InfoBlue,
    onTertiary = NeckWellTextPrimary,

    background = NeckWellBackground,
    onBackground = NeckWellTextPrimary,

    surface = NeckWellSurface,
    onSurface = NeckWellTextPrimary,
    surfaceVariant = NeckWellSurfaceVariant,
    onSurfaceVariant = NeckWellTextSecondary,

    outline = NeckWellBorder,
    outlineVariant = NeckWellDivider,

    error = AlertCoral,
    onError = Color.White,
    errorContainer = Color(0xFF3D1C1C),
    onErrorContainer = AlertCoral,

    inverseSurface = NeckWellTextPrimary,
    inverseOnSurface = NeckWellBackground,
    inversePrimary = NeckWellAccentDark
)

@Composable
fun NeckWellTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
