package com.frenky.egypt.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EgyptDarkColors = darkColorScheme(
    primary = Color(0xFFC9A227),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFE8D48A),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB0B0B0),
    primaryContainer = Color(0xFF3D3010),
    onPrimaryContainer = Color(0xFFFFE082),
    error = Color(0xFFCF6679),
)

@Composable
fun EgyptTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EgyptDarkColors,
        content = content,
    )
}
