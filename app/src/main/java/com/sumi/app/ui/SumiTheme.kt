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
 * product belongs to the five elements.
 */
private val DarkScheme = darkColorScheme(
    background = Color(0xFF0B0D0C),
    surface = Color(0xFF101312),
    onBackground = Color(0xFFF2EFE9),
    onSurface = Color(0xFFF2EFE9),
    onSurfaceVariant = Color(0xFF9BA09C),
    primary = Color(0xFFE8C45C),
    onPrimary = Color(0xFF241C05),
    outline = Color(0xFF3A403C),
    // Menus, dialogs and sheets are drawn on these, and Material's own defaults
    // are tinted violet. Left alone, every popup in the app would arrive in a
    // colour that belongs to no other part of it.
    surfaceVariant = Color(0xFF272B29),
    surfaceContainerLowest = Color(0xFF060807),
    surfaceContainerLow = Color(0xFF0E1110),
    surfaceContainer = Color(0xFF141817),
    surfaceContainerHigh = Color(0xFF1B201E),
    surfaceContainerHighest = Color(0xFF232826)
)

private val LightScheme = lightColorScheme(
    background = Color(0xFFF7F5F1),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF161A18),
    onSurface = Color(0xFF161A18),
    onSurfaceVariant = Color(0xFF5C625E),
    primary = Color(0xFF7A5E12),
    onPrimary = Color(0xFFFFFFFF),
    outline = Color(0xFFD3D0C9),
    surfaceVariant = Color(0xFFEDEAE4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF9F5),
    surfaceContainer = Color(0xFFF4F1EB),
    surfaceContainerHigh = Color(0xFFEFECE5),
    surfaceContainerHighest = Color(0xFFE9E5DD)
)

/**
 * Headlines and titles in Shippori Mincho; body and label text stay in the system
 * sans. Mincho's thin strokes carry the calm Japanese character at title sizes,
 * but get spindly and harder to read at the small sizes body text uses.
 */
private val SumiTypography: Typography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = SumiFonts.mincho),
        headlineMedium = base.headlineMedium.copy(fontFamily = SumiFonts.mincho),
        headlineSmall = base.headlineSmall.copy(fontFamily = SumiFonts.mincho),
        titleLarge = base.titleLarge.copy(fontFamily = SumiFonts.mincho),
        titleMedium = base.titleMedium.copy(fontFamily = SumiFonts.mincho),
        titleSmall = base.titleSmall.copy(fontFamily = SumiFonts.mincho)
    )
}

@Composable
fun SumiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = SumiTypography,
        content = content
    )
}
