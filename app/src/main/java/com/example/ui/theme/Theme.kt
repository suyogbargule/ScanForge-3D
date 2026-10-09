package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = AccentViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3700B3),
    onSecondaryContainer = Color(0xFFEADBFF),
    background = DeepNavy,
    onBackground = DarkOnSurface,
    surface = SurfaceDark,
    onSurface = DarkOnSurface,
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorCoral
)

private val LightColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF0D1117),
    background = DeepNavy,
    surface = SurfaceDark,
    onSurface = DarkOnSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep high-contrast 3D dark theme consistent
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
