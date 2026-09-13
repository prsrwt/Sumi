package com.sumi.app.widget

import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.sumi.app.data.DayProgress
import com.sumi.app.data.SumiRepository
import com.sumi.app.data.Goal
import java.time.LocalDate

class SumiWidget : GlanceAppWidget() {

    /** Exact, so the face is drawn at the size the user actually resized to. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = SumiRepository.get(context)
        val today = LocalDate.now()

        // Fetched before composing, and wide enough for the largest grid any size
        // could ask for. The renderer takes only the columns it has room for, so
        // resizing never needs another database round trip.
        val range = FaceLayout.maxRange(today)
        val goals = repository.goalsNow()
        val days = repository.rangeNow(range.start, range.endInclusive)
        val density = context.resources.displayMetrics.density

        // Light glass on a dark wallpaper, dark glass on a pale one. This is the
        // only thing Android tells a third-party app about what is behind the
        // widget - three representative colours, no pixels.
        val theme = WallpaperTone.themeFor(context)

        // Re-arm here as well as on each firing, so a dropped alarm heals itself
        // the next time the widget draws for any reason.
        DayRollover.scheduleNext(context)

        provideContent {
            val size = LocalSize.current
            val widthPx = (size.width.value * density).toInt().coerceIn(1, MAX_DIMENSION)
            val heightPx = (size.height.value * density).toInt().coerceIn(1, MAX_DIMENSION)

            val layout = remember(widthPx, heightPx) { FaceLayout(widthPx, heightPx, density) }
            val bitmap = remember(layout, goals, days, theme) {
                HeatmapRenderer(theme)
                    .render(layout = layout, today = today, goals = goals, days = days)
            }

            Box(modifier = GlanceModifier.fillMaxSize()) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "Sumi goal heatmap",
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxSize()
                )
                GoalTapTargets(layout, goals, days, today)
            }
        }
    }

    /**
     * Invisible hit areas sitting exactly over the five drawn dots. The dots are
     * part of the bitmap, so they cannot be clicked themselves; these five equal
     * columns tile the same span the renderer spreads the dots across.
     */
    @androidx.compose.runtime.Composable
    private fun GoalTapTargets(
        layout: FaceLayout,
        goals: List<Goal>,
        days: List<DayProgress>,
        today: LocalDate
    ) {
        val todayMask = days.firstOrNull { it.date == today }?.doneMask ?: 0

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Spacer(modifier = GlanceModifier.height(layout.chipBandTopDp.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(layout.chipBandHeightDp.dp)
                    .padding(horizontal = layout.innerLeftDp.dp)
            ) {
                for (slot in 0 until FaceLayout.GOAL_SLOTS) {
                    val done = (todayMask shr slot) and 1 == 1
                    val name = goals.getOrNull(slot)?.displayName ?: "Goal ${slot + 1}"
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .clickable(
                                actionRunCallback<ToggleGoalAction>(
                                    actionParametersOf(ToggleGoalAction.SlotKey to slot)
                                )
                            )
                            .semantics {
                                contentDescription =
                                    if (done) "$name, done" else "$name, not done"
                            }
                    ) {
                        // An empty Box measures to nothing and cannot be tapped;
                        // this fills the slot so the whole column is hittable.
                        Spacer(modifier = GlanceModifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private companion object {
        /** Guards against an absurd bitmap if a launcher reports a bogus size. */
        const val MAX_DIMENSION = 3000
    }
}
