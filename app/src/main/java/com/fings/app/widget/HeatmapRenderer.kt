package com.fings.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.fings.app.data.DayProgress
import com.fings.app.data.Goal
import java.time.LocalDate

/**
 * Draws the entire widget face to a bitmap.
 *
 * Glance widgets are RemoteViews under the hood, which offer no gradients, no
 * antialiasing and no custom shapes. Drawing the face ourselves is the only way
 * to get the glass treatment, and it collapses ~90 grid views into one image.
 *
 * This class knows nothing about widgets or Android components, so its output is
 * a pure function of its inputs.
 */
class HeatmapRenderer(private val theme: GlassTheme = GlassTheme.Dark) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun render(
        layout: FaceLayout,
        today: LocalDate,
        goals: List<Goal>,
        days: List<DayProgress>
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            layout.widthPx.coerceAtLeast(1),
            layout.heightPx.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val masks = days.associate { it.date to it.doneMask }
        val todayMask = masks[today] ?: 0

        drawGlassPanel(canvas, layout)
        drawHeader(canvas, layout, todayMask)
        drawWeekdayLabels(canvas, layout)
        drawGrid(canvas, layout, today, masks)
        drawGoalDots(canvas, layout, goals, todayMask)

        return bitmap
    }

    // ---- the glass ----

    private fun drawGlassPanel(canvas: Canvas, layout: FaceLayout) {
        val panel = RectF(
            layout.panelLeft, layout.panelTop,
            layout.panelRight, layout.panelBottom
        )
        val r = layout.cornerRadius

        // Translucent base. Everything above it is a highlight, never an opaque fill.
        fill.shader = null
        fill.color = theme.scrim
        canvas.drawRoundRect(panel, r, r, fill)

        // Diagonal sheen, clipped to the panel so it reads as light across glass.
        val clip = Path().apply { addRoundRect(panel, r, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        fill.shader = LinearGradient(
            panel.left, panel.top,
            panel.left + panel.width() * 0.75f, panel.bottom,
            theme.sheenHigh, theme.sheenLow,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(panel, fill)
        fill.shader = null
        canvas.restore()

        // Bright edge fading from the top - the cue that sells a glass surface.
        val inset = layout.hairline * 0.5f
        val inner = RectF(panel).apply { inset(inset, inset) }
        stroke.strokeWidth = layout.hairline
        stroke.shader = LinearGradient(
            0f, panel.top, 0f, panel.top + panel.height() * 0.6f,
            theme.innerHighlight, theme.sheenLow,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(inner, r, r, stroke)

        // Even outer border so the panel keeps a defined edge on busy wallpaper.
        stroke.shader = null
        stroke.color = theme.border
        canvas.drawRoundRect(inner, r, r, stroke)
    }

    // ---- content ----

    private fun drawHeader(canvas: Canvas, layout: FaceLayout, todayMask: Int) {
        val centerY = layout.headerTop + layout.headerHeight / 2

        text.color = theme.titleColor
        text.textSize = layout.titleTextSize
        text.textAlign = Paint.Align.LEFT
        canvas.drawText("Fings", layout.panelLeft, baselineFor(centerY), text)

        text.color = theme.mutedColor
        text.textSize = layout.countTextSize
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "${Integer.bitCount(todayMask)}/${FaceLayout.GOAL_SLOTS}",
            layout.panelRight,
            baselineFor(centerY),
            text
        )
    }

    private fun drawWeekdayLabels(canvas: Canvas, layout: FaceLayout) {
        if (!layout.showWeekdayLabels) return
        text.color = theme.mutedColor
        text.textSize = layout.weekdayTextSize
        text.textAlign = Paint.Align.LEFT
        for (row in 0 until FaceLayout.ROWS) {
            val centerY = layout.cellTop(row) + layout.cell / 2
            canvas.drawText(WEEKDAY_LETTERS[row], layout.panelLeft, baselineFor(centerY), text)
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        layout: FaceLayout,
        today: LocalDate,
        masks: Map<LocalDate, Int>
    ) {
        fill.shader = null
        stroke.shader = null
        val rect = RectF()
        for (column in 0 until layout.columns) {
            for (row in 0 until FaceLayout.ROWS) {
                val date = layout.dateAt(today, column, row)
                rect.set(
                    layout.cellLeft(column),
                    layout.cellTop(row),
                    layout.cellLeft(column) + layout.cell,
                    layout.cellTop(row) + layout.cell
                )

                fill.color = if (date.isAfter(today)) {
                    theme.futureCell
                } else {
                    theme.cellColor(Integer.bitCount(masks[date] ?: 0))
                }
                canvas.drawRoundRect(rect, layout.cellRadius, layout.cellRadius, fill)

                if (date == today) {
                    stroke.color = theme.todayRing
                    stroke.strokeWidth = layout.hairline
                    val ring = RectF(rect).apply { inset(-layout.hairline, -layout.hairline) }
                    canvas.drawRoundRect(ring, layout.cellRadius, layout.cellRadius, stroke)
                }
            }
        }
    }

    private fun drawGoalDots(
        canvas: Canvas,
        layout: FaceLayout,
        goals: List<Goal>,
        todayMask: Int
    ) {
        fill.shader = null
        stroke.shader = null
        for (slot in 0 until FaceLayout.GOAL_SLOTS) {
            val cx = layout.dotCenterX(slot)
            val done = (todayMask shr slot) and 1 == 1

            if (done) {
                fill.color = theme.cellColor(FaceLayout.GOAL_SLOTS)
                canvas.drawCircle(cx, layout.dotCenterY, layout.dotRadius, fill)
            } else {
                stroke.color = theme.mutedColor
                stroke.strokeWidth = layout.hairline
                canvas.drawCircle(
                    cx,
                    layout.dotCenterY,
                    layout.dotRadius - layout.hairline / 2,
                    stroke
                )
            }

            if (!layout.showDotLabels) continue
            val goal = goals.getOrNull(slot) ?: Goal(slot, "")
            text.color = if (done) theme.titleColor else theme.mutedColor
            text.textSize = layout.dotLabelTextSize
            text.textAlign = Paint.Align.CENTER
            val label = TextUtils.ellipsize(
                goal.displayName,
                text,
                layout.dotSlotWidth * 0.92f,
                TextUtils.TruncateAt.END
            )
            canvas.drawText(
                label.toString(),
                cx,
                layout.dotCenterY + layout.dotRadius + layout.dotLabelTextSize * 1.15f,
                text
            )
        }
    }

    /** Baseline that puts the text visual centre on [centerY]. */
    private fun baselineFor(centerY: Float): Float {
        val fm = text.fontMetrics
        return centerY - (fm.ascent + fm.descent) / 2
    }

    private companion object {
        val WEEKDAY_LETTERS = arrayOf("M", "T", "W", "T", "F", "S", "S")
    }
}
