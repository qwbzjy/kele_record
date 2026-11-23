package com.cola.record.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFC96F4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1D2),
    onPrimaryContainer = Color(0xFF3B1608),
    secondary = Color(0xFF6E8B6B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEBD6),
    onSecondaryContainer = Color(0xFF1F331D),
    tertiary = Color(0xFFB58A1F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEFC2),
    onTertiaryContainer = Color(0xFF3A2C00),
    background = Color(0xFFFFF9F4),
    onBackground = Color(0xFF2A1E18),
    surface = Color(0xFFFFFBF8),
    onSurface = Color(0xFF2A1E18),
    surfaceVariant = Color(0xFFF6E7DC),
    onSurfaceVariant = Color(0xFF5D4A40),
    outline = Color(0xFFB59A88),
    outlineVariant = Color(0xFFE6D3C6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun ColaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}
