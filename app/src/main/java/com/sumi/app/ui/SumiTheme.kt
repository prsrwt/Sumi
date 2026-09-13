package com.sumi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A deliberately plain palette. The app is a quiet page around the widget, so
 * almost everything is a neutral ground with text on it; the only colour in the
 * product belongs to the five elements and the heatmap.
 */
private val DarkScheme = darkColorScheme(
    background = Color(0xFF0B0D0C),
    surface = Color(0xFF101312),
    onBackground = Color(0xFFF2EFE9),
    onSurface = Color(0xFFF2EFE9),
    onSurfaceVariant = Color(0xFF9BA09C),
    primary = Color(0xFFE8C45C),
    onPrimary = Color(0xFF241C05),
    outline = Color(0xFF3A403C)
)

private val LightScheme = lightColorScheme(
    background = Color(0xFFF7F5F1),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF161A18),
    onSurface = Color(0xFF161A18),
    onSurfaceVariant = Color(0xFF5C625E),
    primary = Color(0xFF7A5E12),
    onPrimary = Color(0xFFFFFFFF),
    outline = Color(0xFFD3D0C9)
)

@Composable
fun SumiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
