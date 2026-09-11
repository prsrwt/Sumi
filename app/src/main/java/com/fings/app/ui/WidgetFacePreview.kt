package com.fings.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fings.app.data.DayProgress
import com.fings.app.data.Goal
import com.fings.app.widget.FaceLayout
import com.fings.app.widget.HeatmapRenderer
import java.time.LocalDate
import androidx.compose.foundation.Image as ComposeImage

/**
 * Shows the real widget face, drawn by the same renderer the widget uses, over a
 * stand-in wallpaper. Lets the glass and the colour ramp be judged without
 * placing a widget on the home screen for every tweak.
 */
@Composable
fun WidgetFacePreview(
    goals: List<Goal>,
    history: List<DayProgress>,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    val renderer = remember { HeatmapRenderer() }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .clip(RoundedCornerShape(18.dp))
            .background(StandInWallpaper)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.roundToPx() }
            val heightPx = with(density) { maxHeight.roundToPx() }

            val bitmap = remember(widthPx, heightPx, goals, history) {
                renderer.render(
                    layout = FaceLayout(widthPx, heightPx, density.density),
                    today = today,
                    goals = goals,
                    days = history
                )
            }

            ComposeImage(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview of the Fings widget",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val PREVIEW_HEIGHT = 180.dp

/** Saturated and varied, so any accidental opacity in the panel is obvious. */
private val StandInWallpaper = Brush.linearGradient(
    listOf(
        Color(0xFF3B1D6E),
        Color(0xFF14557A),
        Color(0xFF9A3B5C)
    )
)
