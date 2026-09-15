package com.sumi.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import com.sumi.app.R
import com.sumi.app.data.SumiRepository
import com.sumi.app.ui.composer.ComposerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The home-screen widget: thin glass with the time on it, which becomes a
 * question once enough time has passed since the last entry. Tapping anywhere
 * opens the composer.
 *
 * Drawn directly with RemoteViews and pushed to the launcher with
 * AppWidgetManager, the moment anything changes. An earlier version used Glance,
 * which runs each update through a queued background session and worked out the
 * face once per session: logging while a session was still alive redrew the old
 * face, so the widget could keep asking after an answer. Here every redraw reads
 * the latest entry afresh and reaches the launcher at once.
 *
 * Built in two layers. The glass is a bitmap, because RemoteViews cannot draw
 * gradients, blur or antialiased shapes. The text is real views on top, because
 * a clock drawn into a bitmap would be stale: widget redraws are rare, while a
 * TextClock is ticked by the system every minute.
 */
object SumiWidget {

    /** Redraws every Sumi widget on the home screen and re-arms the next change. */
    suspend fun updateAll(context: Context) = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val repository = SumiRepository.get(app)
        val zone = ZoneId.systemDefault()
        val latest = repository.latestEntry()
        val settings = repository.settingsNow()

        // Re-arm on every draw as well as on every save, so a dropped or cleared
        // alarm heals the next time the widget renders for any reason.
        Rhythm.schedule(app, latest?.end, settings)

        val manager = AppWidgetManager.getInstance(app) ?: return@withContext
        val ids = manager.getAppWidgetIds(ComponentName(app, SumiWidgetReceiver::class.java))
        if (ids.isEmpty()) return@withContext

        val face = WidgetFace.compute(latest = latest, settings = settings, now = Instant.now(), zone = zone)
        val style = WallpaperTone.styleFor(app)
        val today = LocalDate.now(zone)

        ids.forEach { id ->
            manager.updateAppWidget(id, viewsFor(app, manager.getAppWidgetOptions(id), face, style, today))
        }
    }

    /**
     * Android 12 and later say every size the widget can appear at (usually one
     * for portrait and one for landscape) and pick the matching layout itself.
     * Older versions give a size range; the portrait size is the width's lower
     * bound with the height's upper bound.
     */
    private fun viewsFor(context: Context, options: Bundle, face: WidgetFace, style: GlassStyle, today: LocalDate): RemoteViews {
        val sizes = sizesOf(options)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1) {
            RemoteViews(sizes.associateWith { render(context, it, face, style, today) })
        } else {
            render(context, sizes.first(), face, style, today)
        }
    }

    private fun sizesOf(options: Bundle): List<SizeF> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exact = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            if (!exact.isNullOrEmpty()) return exact.distinct().take(Sizes.MAX_SIZES)
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: Sizes.DEFAULT_WIDTH_DP
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: Sizes.DEFAULT_HEIGHT_DP
        return listOf(SizeF(width.toFloat(), height.toFloat()))
    }

    private fun render(context: Context, sizeDp: SizeF, face: WidgetFace, style: GlassStyle, today: LocalDate): RemoteViews {
        val density = context.resources.displayMetrics.density
        val widthPx = (sizeDp.width * density).toInt().coerceIn(1, Sizes.MAX_DIMENSION)
        val heightPx = (sizeDp.height * density).toInt().coerceIn(1, Sizes.MAX_DIMENSION)

        return RemoteViews(context.packageName, R.layout.widget_root).apply {
            setImageViewBitmap(R.id.widget_glass, GlassRenderer.render(widthPx, heightPx, density, style))
            removeAllViews(R.id.widget_text)
            addView(R.id.widget_text, textLayer(context, face, style, today, sizeDp.width, sizeDp.height))
            setOnClickPendingIntent(R.id.widget_root, composerIntent(context))
            setContentDescription(
                R.id.widget_root,
                when (face) {
                    is WidgetFace.Asking -> "${face.question} Tap to log."
                    WidgetFace.Resting -> "Sumi. Tap to log what you are doing."
                }
            )
        }
    }

    private fun composerIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, ComposerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
        val compact = heightDp < Sizes.COMPACT_BELOW_DP
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
                            context, Sizes.SHORT_DATE.format(today), px((clockDp * 0.42f).coerceIn(12f, 17f)),
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
                            context, Sizes.LONG_DATE.format(today), px((clockDp * 0.30f).coerceIn(13f, 22f)),
                            style.inkMuted, px(widthDp - 48f).toInt(), maxLines = 1
                        )
                    )
                }
            }

            is WidgetFace.Asking -> if (compact) {
                RemoteViews(context.packageName, R.layout.widget_asking_compact).apply {
                    // Side padding, the gap, and about the small clock's width.
                    val room = px(widthDp - 48f - 12f - 40f).toInt()
                    val oneLineMin = px(15f)
                    // One line if it fits once shrunk a little; otherwise two smaller
                    // lines, so a longer question is never cut short at one row tall.
                    val question = if (InkText.fitsOneLine(context, face.question, oneLineMin, room)) {
                        InkText.render(
                            context, face.question, px((heightDp * 0.26f).coerceIn(15f, 24f)),
                            style.ink, room, maxLines = 1, minSizePx = oneLineMin,
                            alignment = Layout.Alignment.ALIGN_NORMAL
                        )
                    } else {
                        InkText.render(
                            context, face.question, px((heightDp * 0.19f).coerceIn(12f, 16f)),
                            style.ink, room, maxLines = 2, alignment = Layout.Alignment.ALIGN_NORMAL
                        )
                    }
                    setImageViewBitmap(R.id.widget_question, question)
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

    private object Sizes {
        /** A launcher that reports no size at all gets a plain four-by-one widget. */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 70

        /** Android allows at most 16 sizes; two or three is normal. */
        const val MAX_SIZES = 16

        /** Guards against an absurd bitmap if a launcher reports a bogus size. */
        const val MAX_DIMENSION = 3000

        /** Launchers report one row at roughly 70-120 dp and two at 180-240. */
        const val COMPACT_BELOW_DP = 150f

        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE · d MMMM")
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE · d MMM")
    }
}
