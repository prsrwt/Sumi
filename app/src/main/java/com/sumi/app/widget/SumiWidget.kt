package com.sumi.app.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.sumi.app.R
import com.sumi.app.data.SumiRepository
import com.sumi.app.ui.composer.ComposerActivity
import java.time.Instant
import java.time.ZoneId

/**
 * The home-screen widget: pale glass with the time on it, which becomes a
 * question once enough time has passed since the last entry. Tapping anywhere
 * opens the composer.
 *
 * Built in two layers. The glass is a bitmap, because RemoteViews cannot draw
 * gradients, blur or antialiased shapes. The text is real views on top, because
 * a clock drawn into a bitmap would be stale - widget redraws are throttled to
 * about half-hourly, while a TextClock is ticked by the system every minute.
 */
class SumiWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = SumiRepository.get(context)
        val zone = ZoneId.systemDefault()
        val face = WidgetFace.compute(
            latest = repository.latestEntry(),
            settings = repository.settingsNow(),
            now = Instant.now(),
            zone = zone
        )
        val style = WallpaperTone.styleFor(context)
        val density = context.resources.displayMetrics.density

        provideContent {
            val size = LocalSize.current
            val widthPx = (size.width.value * density).toInt().coerceIn(1, MAX_DIMENSION)
            val heightPx = (size.height.value * density).toInt().coerceIn(1, MAX_DIMENSION)

            val glass = remember(widthPx, heightPx, style) {
                GlassRenderer.render(widthPx, heightPx, density, style)
            }
            val text = remember(face, size) { textLayer(context, face, size.width.value, size.height.value) }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<ComposerActivity>())
                    .semantics {
                        contentDescription = when (face) {
                            is WidgetFace.Asking -> "${face.question} Tap to log."
                            WidgetFace.Resting -> "Sumi. Tap to log what you are doing."
                        }
                    }
            ) {
                Image(
                    provider = ImageProvider(glass),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxSize()
                )
                AndroidRemoteViews(remoteViews = text, modifier = GlanceModifier.fillMaxSize())
            }
        }
    }

    /**
     * The text layer, chosen and sized for the space the widget actually has.
     *
     * Below about one and a half rows the stacked layout has no room, so the time
     * and date (or the question and time) sit side by side instead. Above it,
     * they stack. Sizes scale with the space too - fixed sp would leave a large
     * widget with a small clock.
     */
    private fun textLayer(context: Context, face: WidgetFace, widthDp: Float, heightDp: Float): RemoteViews {
        val compact = heightDp < COMPACT_BELOW_DP
        return when (face) {
            WidgetFace.Resting -> if (compact) {
                RemoteViews(context.packageName, R.layout.widget_idle_compact).apply {
                    val clockDp = (heightDp * 0.40f).coerceIn(20f, 44f)
                    setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_DIP, clockDp)
                    setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_DIP, (clockDp * 0.40f).coerceIn(11f, 16f))
                }
            } else {
                RemoteViews(context.packageName, R.layout.widget_idle).apply {
                    val clockDp = minOf(heightDp * 0.36f, widthDp * 0.2f).coerceIn(28f, 96f)
                    setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_DIP, clockDp)
                    setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_DIP, (clockDp * 0.27f).coerceIn(11f, 20f))
                }
            }

            is WidgetFace.Asking -> if (compact) {
                RemoteViews(context.packageName, R.layout.widget_asking_compact).apply {
                    setTextViewText(R.id.widget_question, face.question)
                    setTextViewTextSize(R.id.widget_question, TypedValue.COMPLEX_UNIT_DIP, (heightDp * 0.24f).coerceIn(14f, 22f))
                }
            } else {
                RemoteViews(context.packageName, R.layout.widget_asking).apply {
                    setTextViewText(R.id.widget_question, face.question)
                    val questionDp = minOf(heightDp * 0.17f, widthDp * 0.075f).coerceIn(16f, 34f)
                    setTextViewTextSize(R.id.widget_question, TypedValue.COMPLEX_UNIT_DIP, questionDp)
                }
            }
        }
    }

    private companion object {
        /** Guards against an absurd bitmap if a launcher reports a bogus size. */
        const val MAX_DIMENSION = 3000

        /** Launchers report one row at roughly 70-120 dp and two at 180-240. */
        const val COMPACT_BELOW_DP = 150f
    }
}
