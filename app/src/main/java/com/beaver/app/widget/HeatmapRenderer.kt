package com.beaver.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
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
class HeatmapRenderer(private val theme: GlassTheme = GlassTheme.OnDarkWallpaper) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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
        drawGlyphs(canvas, layout, goals, todayMask)

        return bitmap
    }

    // ---- the slab ----

    /**
     * Apple's Liquid Glass is blur, refraction, dispersion, specular edges and
     * adaptive tint. The first three all need the pixels behind the panel, which
     * a widget cannot reach, so this builds the rest: light on the rim rather
     * than across the face, a bevel for thickness, a shadow and halo for lift,
     * and grain because frosted glass is not optically smooth.
     */
    private fun drawPane(canvas: Canvas, layout: FaceLayout) {
        val pane = RectF(layout.paneLeft, layout.paneTop, layout.paneRight, layout.paneBottom)
        val r = layout.paneRadius
        val blurRadius = (layout.paneRadius * 0.42f).coerceAtLeast(1f)

        // Contact shadow, pushed down so the light reads as coming from above.
        glow.shader = null
        glow.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        glow.color = theme.paneShadow
        canvas.save()
        canvas.translate(0f, layout.hairline * 2.5f)
        canvas.drawRoundRect(pane, r, r, glow)
        canvas.restore()

        // Ambient halo, even on all sides.
        glow.color = theme.paneHalo
        canvas.drawRoundRect(pane, r, r, glow)
        glow.maskFilter = null

        fill.shader = null
        fill.color = theme.paneFill
        canvas.drawRoundRect(pane, r, r, fill)

        val clip = Path().apply { addRoundRect(pane, r, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        fill.shader = grainShader
        fill.alpha = theme.grainAlpha
        canvas.drawRect(pane, fill)
        fill.alpha = 255
        fill.shader = null
        canvas.restore()

        // Rim light: bright at the top-left, gone by the middle, a weaker return
        // at the bottom-right. Softened so it reads as a glint, not a drawn line.
        val inner = RectF(pane).apply { inset(layout.hairline, layout.hairline) }
        stroke.strokeWidth = layout.hairline * 1.6f
        stroke.maskFilter = BlurMaskFilter(layout.hairline, BlurMaskFilter.Blur.NORMAL)
        stroke.shader = LinearGradient(
            pane.left, pane.top, pane.right, pane.bottom,
            intArrayOf(theme.paneGlint, Color.TRANSPARENT, Color.TRANSPARENT, theme.paneRim),
            floatArrayOf(0f, 0.34f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(inner, r, r, stroke)
        stroke.maskFilter = null
        stroke.shader = null

        // Bevel: a dark line just inside the bright one gives the edge thickness.
        val bevel = RectF(pane).apply { inset(layout.hairline * 2.2f, layout.hairline * 2.2f) }
        stroke.strokeWidth = layout.hairline
        stroke.color = theme.paneBevelDark
        canvas.drawRoundRect(bevel, r, r, stroke)

        val edge = RectF(pane).apply { inset(layout.hairline * 0.5f, layout.hairline * 0.5f) }
        stroke.color = theme.paneRim
        canvas.drawRoundRect(edge, r, r, stroke)
    }

    /**
     * Tiled monochrome noise, generated once. The band is narrow on purpose:
     * full-range noise at device pixel scale reads as television static rather
     * than a surface.
     */
    private val grainShader: BitmapShader by lazy {
        val size = 64
        val pixels = IntArray(size * size)
        val random = java.util.Random(7)
        for (i in pixels.indices) {
            pixels[i] = Color.argb(104 + random.nextInt(48), 255, 255, 255)
        }
        val noise = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        noise.setPixels(pixels, 0, size, 0, 0, size, size)
        BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ---- day dots ----

    /**
     * Monochrome, and deliberately unlit. These thirty dots previously carried a
     * gloss overlay and an LED bloom each, which is a great deal of light for
     * what is meant to be a quiet record of the last month.
     */
    private fun drawGrid(
        canvas: Canvas,
        layout: FaceLayout,
        today: LocalDate,
        masks: Map<LocalDate, Int>
    ) {
        // Built once and reused by translating the canvas per dot, rather than
        // allocating a shader for every cell.
        val frost = LinearGradient(
            0f, 0f, 0f, layout.cell,
            theme.frostTop, theme.frostBottom,
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
                        fill.shader = null
                        fill.color = filled
                        canvas.drawCircle(mid, mid, r, fill)
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

    // ---- element glyphs ----

    /**
     * Bare characters, no disc behind them. The glyph itself carries the state:
     * faint while the goal is open, full strength and faintly lit once done.
     */
    private fun drawGlyphs(
        canvas: Canvas,
        layout: FaceLayout,
        goals: List<Goal>,
        todayMask: Int
    ) {
        text.textSize = layout.kanjiSize
        val metrics = text.fontMetrics
        val baseline = layout.chipCenterY - (metrics.ascent + metrics.descent) / 2

        val glyphBloom = BlurMaskFilter(
            (layout.kanjiSize * 0.26f).coerceAtLeast(0.6f),
            BlurMaskFilter.Blur.NORMAL
        )

        for (slot in 0 until FaceLayout.GOAL_SLOTS) {
            val cx = layout.slotCenterX(slot)
            val done = (todayMask shr slot) and 1 == 1
            val element = goals.getOrNull(slot)?.element ?: Element.forSlot(slot)

            if (done) {
                // Just enough halo to read as lit without becoming a lamp.
                text.maskFilter = glyphBloom
                text.color = withAlpha(element.color, theme.kanjiGlowAlpha)
                canvas.drawText(element.kanji, cx, baseline, text)
                text.maskFilter = null
            }

            text.color = if (done) element.color else withAlpha(element.color, theme.kanjiIdleAlpha)
            canvas.drawText(element.kanji, cx, baseline, text)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
