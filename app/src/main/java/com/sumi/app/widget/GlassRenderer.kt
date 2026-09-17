package com.sumi.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

/**
 * The glass and the ink that sits on it, chosen together for the wallpaper.
 *
 * The glass is thin on purpose, so the wallpaper shows through clearly. That puts
 * readability on the text rather than on a milky layer: near-black ink over light
 * wallpapers, and paper-white ink on a faint dark glass over dark ones. Milky glass
 * with dark ink on a dark wallpaper was the alternative, and it looked washed out.
 */
data class GlassStyle(
    /** The fill at the foot of the pane. */
    val fill: Int,
    /** And at its head, a little more present, standing in for a blur no widget can have. */
    val fillTop: Int,
    /** The rim where it catches light, along the top. */
    val border: Int,
    /** And where it fades, at the bottom. */
    val borderFaint: Int,
    val grainAlpha: Int,
    /** The clock and the question. */
    val ink: Int,
    /** The date and the small clock: a little softer, still fully legible. */
    val inkMuted: Int
) {
    companion object {
        val OnDarkWallpaper = GlassStyle(
            fill = Color.argb(96, 18, 17, 16),
            fillTop = Color.argb(128, 34, 33, 31),
            border = Color.argb(120, 255, 255, 255),
            borderFaint = Color.argb(30, 255, 255, 255),
            grainAlpha = 4,
            ink = Color.rgb(250, 248, 244),
            inkMuted = Color.argb(217, 250, 248, 244)
        )

        val OnLightWallpaper = GlassStyle(
            fill = Color.argb(64, 252, 250, 246),
            fillTop = Color.argb(112, 255, 254, 252),
            border = Color.argb(150, 255, 255, 255),
            borderFaint = Color.argb(36, 255, 255, 255),
            grainAlpha = 4,
            ink = Color.rgb(17, 17, 17),
            inkMuted = Color.argb(217, 17, 17, 17)
        )
    }
}

/**
 * Draws the flat frosted slab the widget's text sits on.
 *
 * Still deliberately flat: an earlier version added a contact shadow, an ambient
 * halo, a rim glint and a bevel to imitate a thick pane, and it read as a raised
 * object rather than a quiet surface. None of that is back.
 *
 * What was added is one gentle vertical gradient in the fill and one in the rim,
 * brighter at the top where light would land. A real widget cannot blur what is
 * behind it, so without this the pane is a flat rectangle: it disappeared into
 * dark wallpapers and read as a grey card on busy ones. The gradient is what the
 * eye takes for frosted glass when there is no blur to be had.
 *
 * Only the glass is drawn here. The text is left to real views on top, because
 * the clock has to be a system-ticked TextClock.
 */
object GlassRenderer {

    fun render(widthPx: Int, heightPx: Int, density: Float, style: GlassStyle): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val hairline = maxOf(1f, density)
        val inset = 2f * density
        val pane = RectF(inset, inset, width - inset, height - inset)
        // At one row the slab is short; capping the radius at half its height
        // turns it into a clean pill instead of corners that overlap.
        val radius = minOf(28f * density, pane.height() / 2)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        fill.shader = LinearGradient(
            0f, pane.top, 0f, pane.bottom,
            style.fillTop, style.fill, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pane, radius, radius, fill)
        fill.shader = null

        val clip = Path().apply { addRoundRect(pane, radius, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
            fill.shader = grain
            fill.alpha = style.grainAlpha
            drawRect(pane, fill)
        }

        // A hairline all the way round, lit at the top and fading toward the foot.
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = hairline
            shader = LinearGradient(
                0f, pane.top, 0f, pane.bottom,
                style.border, style.borderFaint, Shader.TileMode.CLAMP
            )
        }
        val edge = RectF(pane).apply { inset(hairline / 2, hairline / 2) }
        canvas.drawRoundRect(edge, radius, radius, stroke)

        return bitmap
    }

    /**
     * Tiled noise, generated once. A narrow brightness band on purpose: full-range
     * noise at device-pixel scale reads as television static, not a surface.
     */
    private val grain: BitmapShader by lazy {
        val size = 64
        val random = java.util.Random(7)
        val pixels = IntArray(size * size) { Color.argb(104 + random.nextInt(48), 255, 255, 255) }
        val noise = createBitmap(size, size)
        noise.setPixels(pixels, 0, size, 0, 0, size, size)
        BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
