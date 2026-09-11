package com.beaver.app.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import com.beaver.app.data.DayProgress
import com.beaver.app.data.Element
import com.beaver.app.data.Goal
import java.time.LocalDate

/**
 * Draws the entire widget face to a bitmap.
 *
 * Glance widgets are RemoteViews under the hood, which offer no gradients, no
 * antialiasing and no custom shapes. Drawing the face ourselves is the only way
 * to get the glass treatment, and it collapses the whole grid into one image.
 *
 * This class knows nothing about widgets or Android components, so its output is
 * a pure function of its inputs.
 */
class HeatmapRenderer(private val theme: GlassTheme = GlassTheme.Default) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /**
     * Bloom behind a lit dot, the way an indicator LED spills light into the
     * surface around it. Drawn first, then the crisp core over the top.
     *
     * BlurMaskFilter only works on a software canvas, which is exactly what we
     * have - the face is drawn into a Bitmap, never a hardware-accelerated view.
     */
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        // Serif carries the kanji strokes better than sans at small sizes. The
        // glyphs resolve through the system CJK fallback; Phase D bundles a
        // subset so devices without a CJK font still render them.
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
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

        drawPane(canvas, layout)
        drawGrid(canvas, layout, today, masks)
        drawChips(canvas, layout, goals, todayMask)

        return bitmap
    }

    // ---- the pane ----

    /**
     * A flat frosted slab.
     *
     * This used to carry a diagonal specular sweep and a lit top edge, which is
     * the textbook way to fake glass. On a real phone it just looked like a lamp
     * was pointed at the top of the widget, so both are gone: one even fill and
     * one even rim, lit the same everywhere.
     */
    private fun drawPane(canvas: Canvas, layout: FaceLayout) {
        val pane = RectF(layout.paneLeft, layout.paneTop, layout.paneRight, layout.paneBottom)
        val r = layout.paneRadius

        fill.shader = null
        fill.color = theme.paneFill
        canvas.drawRoundRect(pane, r, r, fill)

        val inner = RectF(pane).apply { inset(layout.hairline * 0.5f, layout.hairline * 0.5f) }
        stroke.shader = null
        stroke.strokeWidth = layout.hairline
        stroke.color = theme.paneRim
        canvas.drawRoundRect(inner, r, r, stroke)
    }

    // ---- day tiles ----

    private fun drawGrid(
        canvas: Canvas,
        layout: FaceLayout,
        today: LocalDate,
        masks: Map<LocalDate, Int>
    ) {
        val tileBloom = BlurMaskFilter(
            (layout.cell * theme.bloomRadiusRatio).coerceAtLeast(0.6f),
            BlurMaskFilter.Blur.NORMAL
        )

        // Built once and reused by translating the canvas per tile, rather than
        // allocating a shader for every cell.
        val frost = LinearGradient(
            0f, 0f, 0f, layout.cell,
            theme.frostTop, theme.frostBottom,
            Shader.TileMode.CLAMP
        )
        val gloss = LinearGradient(
            0f, 0f, 0f, layout.cell,
            theme.glossTop, theme.glossBottom,
            Shader.TileMode.CLAMP
        )

        val mid = layout.cell / 2
        val r = layout.cellRadius

        for (row in 0 until FaceLayout.ROWS) {
            for (column in 0 until FaceLayout.COLUMNS) {
                val date = layout.dateAt(today, column, row)

                canvas.save()
                canvas.translate(layout.cellLeft(column), layout.cellTop(row))

                if (date.isAfter(today)) {
                    fill.shader = null
                    fill.color = theme.futureCell
                    canvas.drawCircle(mid, mid, r, fill)
                } else {
                    val filled = theme.cellColor(Integer.bitCount(masks[date] ?: 0))
                    if (filled == null) {
                        fill.shader = frost
                        canvas.drawCircle(mid, mid, r, fill)
                        fill.shader = null
                    } else {
                        // The bloom carries the ramp's own alpha, so a one-goal
                        // day barely glows and a full day reads as properly lit.
                        glow.maskFilter = tileBloom
                        glow.color = withAlpha(
                            filled,
                            (Color.alpha(filled) * theme.bloomStrength).toInt().coerceIn(0, 255)
                        )
                        canvas.drawCircle(mid, mid, r * 0.90f, glow)
                        glow.maskFilter = null

                        fill.shader = null
                        fill.color = filled
                        canvas.drawCircle(mid, mid, r, fill)
                        fill.shader = gloss
                        canvas.drawCircle(mid, mid, r, fill)
                        fill.shader = null
                    }
                    stroke.shader = null
                    stroke.color = theme.frostEdge
                    stroke.strokeWidth = layout.hairline
                    canvas.drawCircle(mid, mid, r - layout.hairline / 2, stroke)
                }

                canvas.restore()

                if (date == today) {
                    stroke.shader = null
                    stroke.color = theme.todayRing
                    stroke.strokeWidth = layout.hairline
                    canvas.drawCircle(
                        layout.cellLeft(column) + mid,
                        layout.cellTop(row) + mid,
                        r + layout.hairline * 1.5f,
                        stroke
                    )
                }
            }
        }
    }

    // ---- element chips ----

    private fun drawChips(
        canvas: Canvas,
        layout: FaceLayout,
        goals: List<Goal>,
        todayMask: Int
    ) {
        val chipBloom = BlurMaskFilter(
            (layout.chipRadius * theme.bloomRadiusRatio * 1.5f).coerceAtLeast(0.6f),
            BlurMaskFilter.Blur.NORMAL
        )

        val chipFrost = LinearGradient(
            0f, layout.chipCenterY - layout.chipRadius,
            0f, layout.chipCenterY + layout.chipRadius,
            theme.chipTop, theme.chipBottom,
            Shader.TileMode.CLAMP
        )

        text.textSize = layout.kanjiSize
        val metrics = text.fontMetrics
        val baseline = layout.chipCenterY - (metrics.ascent + metrics.descent) / 2

        for (slot in 0 until FaceLayout.GOAL_SLOTS) {
            val cx = layout.slotCenterX(slot)
            val done = (todayMask shr slot) and 1 == 1
            val element = goals.getOrNull(slot)?.element ?: Element.forSlot(slot)

            // Frosted chip underneath, always - the glass reads the same whether
            // the goal is done or not; only what fills it changes.
            fill.shader = chipFrost
            canvas.drawCircle(cx, layout.chipCenterY, layout.chipRadius, fill)
            fill.shader = null

            if (done) {
                glow.maskFilter = chipBloom
                glow.color = withAlpha(element.color, theme.chipBloomAlpha)
                canvas.drawCircle(cx, layout.chipCenterY, layout.chipRadius * 0.94f, glow)
                glow.maskFilter = null

                fill.color = withAlpha(element.color, theme.chipFillAlpha)
                canvas.drawCircle(cx, layout.chipCenterY, layout.chipRadius, fill)
            } else {
                // Without this the glyph competes with whatever the wallpaper is
                // doing behind the frost - violet 空 on a violet photo vanished.
                fill.color = theme.chipIdleScrim
                canvas.drawCircle(cx, layout.chipCenterY, layout.chipRadius, fill)
            }

            stroke.shader = null
            stroke.color = theme.chipRim
            stroke.strokeWidth = layout.hairline
            canvas.drawCircle(
                cx,
                layout.chipCenterY,
                layout.chipRadius - layout.hairline / 2,
                stroke
            )

            text.color = if (done) {
                theme.kanjiOnColor
            } else {
                withAlpha(element.color, theme.kanjiIdleAlpha)
            }
            canvas.drawText(element.kanji, cx, baseline, text)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
