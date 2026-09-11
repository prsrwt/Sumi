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
     * The glass slab.
     *
     * Apple's Liquid Glass is blur, refraction, dispersion, specular edges and
     * adaptive tint. The first three all require sampling the pixels behind the
     * panel, which a widget cannot do - it is RemoteViews drawn blind in our
     * process and handed to the launcher, and WallpaperManager stopped handing
     * out the wallpaper to third-party apps in Android 13. So this builds the
     * two that do not need the backdrop, plus the cues that sell thickness:
     *
     *   - an ambient halo and a contact shadow, so the slab sits above the
     *     wallpaper rather than being painted onto it
     *   - specular light on the RIM, unevenly. An earlier version washed a
     *     gradient across the whole face and read as a lamp aimed at the widget;
     *     real glass lights at its edges
     *   - a bevel, bright outside and dark inside, implying the slab has depth
     *   - fine grain, because frosted glass is not optically smooth
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

        // Rim light: bright at the top-left, fading out by the middle, with a
        // weaker return at the bottom-right. Softened so it is a glint and not
        // a drawn line.
        val inner = RectF(pane).apply { inset(layout.hairline, layout.hairline) }
        stroke.strokeWidth = layout.hairline * 1.6f
        stroke.maskFilter = BlurMaskFilter(layout.hairline, BlurMaskFilter.Blur.NORMAL)
        stroke.shader = LinearGradient(
            pane.left, pane.top, pane.right, pane.bottom,
            intArrayOf(theme.paneGlint, theme.paneSheenLow, theme.paneSheenLow, theme.paneRim),
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

        // Crisp outer edge so the slab stays defined on busy wallpaper.
        val edge = RectF(pane).apply { inset(layout.hairline * 0.5f, layout.hairline * 0.5f) }
        stroke.color = theme.paneRim
        canvas.drawRoundRect(edge, r, r, stroke)
    }

    /**
     * Tiled monochrome noise. Generated once: a real frosted surface scatters
     * light unevenly, and without this the pane reads as flat plastic.
     */
    private val grainShader: BitmapShader by lazy {
        val size = 64
        val pixels = IntArray(size * size)
        val random = java.util.Random(7)
        for (i in pixels.indices) {
            // A narrow band, not the full 0..255. Full-range noise at device
            // pixel scale reads as television static rather than a surface.
            val v = 104 + random.nextInt(48)
            pixels[i] = Color.argb(v, 255, 255, 255)
        }
        val noise = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        noise.setPixels(pixels, 0, size, 0, 0, size, size)
        BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
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
