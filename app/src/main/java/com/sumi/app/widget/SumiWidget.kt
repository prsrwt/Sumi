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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        val latest = repository.latestEntry()
        val settings = repository.settingsNow()
        val face = WidgetFace.compute(latest = latest, settings = settings, now = Instant.now(), zone = zone)

        // Re-arm on every draw as well as on every save, so a dropped or cleared
        // alarm heals the next time the widget renders for any reason.
        Rhythm.schedule(context, latest?.end, settings)
        val style = WallpaperTone.styleFor(context)
        // Read once per redraw, so the Mincho date is drawn again when the day turns.
        val today = LocalDate.now(zone)
        val density = context.resources.displayMetrics.density

        provideContent {
            val size = LocalSize.current
            val widthPx = (size.width.value * density).toInt().coerceIn(1, MAX_DIMENSION)
            val heightPx = (size.height.value * density).toInt().coerceIn(1, MAX_DIMENSION)

            val glass = remember(widthPx, heightPx, style) {
                GlassRenderer.render(widthPx, heightPx, density, style)
            }
            val text = remember(face, size, style, today) { textLayer(context, face, style, today, size.width.value, size.height.value) }

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
     * they stack. Sizes scale with the space too: fixed sp would leave a large
     * widget with a small clock. The clocks are live TextClocks; the question and
     * the date are Mincho images from [InkText], drawn at exactly the size needed.
     */
    private fun textLayer(
        context: Context,
        face: WidgetFace,
        style: GlassStyle,
        today: LocalDate,
        widthDp: Float,
        heightDp: Float
    ): RemoteViews {
        val compact = heightDp < COMPACT_BELOW_DP
        val density = context.resources.displayMetrics.density
        fun px(dp: Float) = dp * density

        val views = when (face) {
            WidgetFace.Resting -> if (compact) {
                RemoteViews(context.packageName, R.layout.widget_idle_compact).apply {
                    val clockDp = (heightDp * 0.40f).coerceIn(20f, 44f)
                    setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_DIP, clockDp)
                    // Room left beside the clock: side padding, the gap, and roughly
                    // the clock's own width at this size.
                    val roomDp = widthDp - 48f - 16f - clockDp * 2.6f
                    setImageViewBitmap(
                        R.id.widget_date,
                        InkText.render(
                            context, SHORT_DATE.format(today), px((clockDp * 0.42f).coerceIn(12f, 17f)),
                            style.inkMuted, px(roomDp).toInt(), maxLines = 1, minSizePx = px(11f)
                        )
                    )
                }
            } else {
                RemoteViews(context.packageName, R.layout.widget_idle).apply {
                    val clockDp = minOf(heightDp * 0.36f, widthDp * 0.2f).coerceIn(28f, 96f)
                    setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_DIP, clockDp)
                    setImageViewBitmap(
                        R.id.widget_date,
                        InkText.render(
                            context, LONG_DATE.format(today), px((clockDp * 0.30f).coerceIn(13f, 22f)),
                            style.inkMuted, px(widthDp - 48f).toInt(), maxLines = 1
                        )
                    )
                }
            }

            is WidgetFace.Asking -> if (compact) {
                RemoteViews(context.packageName, R.layout.widget_asking_compact).apply {
                    // Side padding, the gap, and about the small clock's width.
                    val roomDp = widthDp - 48f - 12f - 40f
                    setImageViewBitmap(
                        R.id.widget_question,
                        InkText.render(
                            context, face.question, px((heightDp * 0.26f).coerceIn(15f, 24f)),
                            style.ink, px(roomDp).toInt(), maxLines = 1, minSizePx = px(13f)
                        )
                    )
                }
            } else {
                RemoteViews(context.packageName, R.layout.widget_asking).apply {
                    val questionDp = minOf(heightDp * 0.18f, widthDp * 0.08f).coerceIn(17f, 36f)
                    setImageViewBitmap(
                        R.id.widget_question,
                        InkText.render(context, face.question, px(questionDp), style.ink, px(widthDp - 56f).toInt(), maxLines = 2)
                    )
                }
            }
        }

        // Ink follows the glass: dark on light wallpapers, paper-white on dark ones.
        // The Mincho images are already drawn in it; the live clocks are set here.
        when (face) {
            WidgetFace.Resting -> views.setTextColor(R.id.widget_clock, style.ink)
            is WidgetFace.Asking -> views.setTextColor(R.id.widget_small_clock, style.ink)
        }
        return views
    }

    private companion object {
        /** Guards against an absurd bitmap if a launcher reports a bogus size. */
        const val MAX_DIMENSION = 3000

        /** Launchers report one row at roughly 70-120 dp and two at 180-240. */
        const val COMPACT_BELOW_DP = 150f

        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE · d MMMM")
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE · d MMM")
    }
}
