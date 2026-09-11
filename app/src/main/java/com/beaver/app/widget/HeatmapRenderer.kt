package com.beaver.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.beaver.app.data.DayProgress
import com.beaver.app.data.Goal
import java.time.LocalDate

/**
 * Draws the entire widget face to a bitmap.
 *
 * Glance widgets are RemoteViews under the hood, which offer no gradients, no
 * antialiasing and no custom shapes. Drawing the face ourselves is the only way
 * to get the glass treatment, and it collapses ~180 tiles into one image.
 *
 * This class knows nothing about widgets or Android components, so its output is
 * a pure function of its inputs.
 */
class HeatmapRenderer(private val theme: GlassTheme = GlassTheme.Default) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

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

        // Built once and reused by translating the canvas per tile, rather than
        // allocating a shader for every one of ~180 cells.
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

        drawGrid(canvas, layout, today, masks, frost, gloss)
        drawMarks(canvas, layout, todayMask)

        return bitmap
    }

    private fun drawGrid(
        canvas: Canvas,
        layout: FaceLayout,
        today: LocalDate,
        masks: Map<LocalDate, Int>,
        frost: Shader,
        gloss: Shader
    ) {
        val tile = RectF(0f, 0f, layout.cell, layout.cell)
        val r = layout.cellRadius

        for (column in 0 until layout.columns) {
            for (row in 0 until FaceLayout.ROWS) {
                val date = layout.dateAt(today, column, row)

                canvas.save()
                canvas.translate(layout.cellLeft(column), layout.cellTop(row))

                when {
                    date.isAfter(today) -> {
                        fill.shader = null
                        fill.color = theme.futureCell
                        canvas.drawRoundRect(tile, r, r, fill)
                    }

                    else -> {
                        val filled = theme.cellColor(Integer.bitCount(masks[date] ?: 0))
                        if (filled == null) {
                            // Untouched day: frosted glass.
                            fill.shader = frost
                            canvas.drawRoundRect(tile, r, r, fill)
                            fill.shader = null
                        } else {
                            fill.shader = null
                            fill.color = filled
                            canvas.drawRoundRect(tile, r, r, fill)
                            // Same gloss over the top so a filled tile still reads
                            // as tinted glass rather than flat paint.
                            fill.shader = gloss
                            canvas.drawRoundRect(tile, r, r, fill)
                            fill.shader = null
                        }

                        stroke.color = theme.frostEdge
                        stroke.strokeWidth = layout.hairline
                        canvas.drawRoundRect(tile, r, r, stroke)
                    }
                }

                canvas.restore()

                if (date == today) {
                    stroke.color = theme.todayRing
                    stroke.strokeWidth = layout.hairline
                    val ring = RectF(
                        layout.cellLeft(column) - layout.hairline,
                        layout.cellTop(row) - layout.hairline,
                        layout.cellLeft(column) + layout.cell + layout.hairline,
                        layout.cellTop(row) + layout.cell + layout.hairline
                    )
                    canvas.drawRoundRect(ring, r, r, stroke)
                }
            }
        }
    }

    /**
     * Placeholder marks for the five goals. Phase D replaces these with the
     * element kanji drawn in each goal's own colour.
     */
    private fun drawMarks(canvas: Canvas, layout: FaceLayout, todayMask: Int) {
        fill.shader = null
        stroke.shader = null

        for (slot in 0 until FaceLayout.GOAL_SLOTS) {
            val cx = layout.slotCenterX(slot)
            val done = (todayMask shr slot) and 1 == 1

            if (done) {
                fill.color = theme.cellColor(FaceLayout.GOAL_SLOTS) ?: theme.kanjiDone
                canvas.drawCircle(cx, layout.markCenterY, layout.markRadius, fill)
            } else {
                stroke.color = theme.kanjiIdle
                stroke.strokeWidth = layout.hairline
                canvas.drawCircle(
                    cx,
                    layout.markCenterY,
                    layout.markRadius - layout.hairline / 2,
                    stroke
                )
            }
        }
    }
}
