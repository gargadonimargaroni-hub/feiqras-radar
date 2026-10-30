package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RadarColorScheme = darkColorScheme(
    primary = RadarCyanPrimary,
    onPrimary = Color(0xFF002229),
    primaryContainer = Color(0xFF004953),
    onPrimaryContainer = Color(0xFF9CF4FF),

    secondary = RadarGreenSecondary,
    onSecondary = Color(0xFF003820),
    secondaryContainer = Color(0xFF005230),
    onSecondaryContainer = Color(0xFF86FFC3),

    tertiary = RadarWarningAmber,
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDF9E),

    error = RadarDangerRed,
    onError = Color.White,
    errorContainer = Color(0xFF930018),
    onErrorContainer = Color(0xFFFFDAD8),

    background = RadarPitchBlack,
    onBackground = TextPrimaryDark,

    surface = RadarDarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = RadarDarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,

    outline = RadarBorder,
    outlineVariant = Color(0xFF22354E)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RadarColorScheme,
        typography = Typography,
        content = content
    )
}
