package com.netpulse.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Brand {
    val Green = Color(0xFF22C55E)
    val GreenDeep = Color(0xFF15803D)
    val Red = Color(0xFFEF4444)
    val RedDeep = Color(0xFFB91C1C)
    val Amber = Color(0xFFF59E0B)
    val Slate = Color(0xFF475569)
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7CB0FF),
    onPrimary = Color(0xFF07101F),
    primaryContainer = Color(0xFF1B2E57),
    onPrimaryContainer = Color(0xFFD6E4FF),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF111A2D),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1A2540),
    onSurfaceVariant = Color(0xFF9AA6BD),
    surfaceContainer = Color(0xFF0F1829),
    surfaceContainerHigh = Color(0xFF16213A),
    outline = Color(0xFF2A3654),
    outlineVariant = Color(0xFF222D48),
    error = Brand.Red,
    errorContainer = Color(0xFF4A1D22),
    onErrorContainer = Color(0xFFFFD9DC),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B1F5C),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF0B1220),
    surface = Color.White,
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE8ECF5),
    onSurfaceVariant = Color(0xFF55627A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF2F9),
    outline = Color(0xFFD3DAE8),
    outlineVariant = Color(0xFFE2E7F2),
    error = Brand.RedDeep,
    errorContainer = Color(0xFFFFE1E3),
    onErrorContainer = Color(0xFF5A0F16),
)

@Composable
fun NetPulseTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
