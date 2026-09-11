package com.beaver.app.widget

import android.graphics.Color

/**
 * Colours for the widget face.
 *
 * Two rules hold this together, both learned the hard way:
 *
 * 1. There is exactly one source of hue - the five element glyphs. The day grid
 *    is monochrome. An earlier version had a saturated yellow ramp competing
 *    with five saturated element discs, and two colour systems fighting for
 *    attention is most of what made the widget read as cartoony rather than
 *    minimal.
 *
 * 2. Nothing here is optically frosted. Android cannot blur what sits behind a
 *    widget, so the glass is milky translucency, a lit rim and fine grain, which
 *    is what actually reads as glass at this size.
 */
data class GlassTheme(
    // ---- the slab ----
    val paneFill: Int,
    val paneRim: Int,
    val paneBevelDark: Int,
    /** Tight specular glint on the rim. Real glass lights at its edges. */
    val paneGlint: Int,
    val paneShadow: Int,
    /** Soft ambient halo, the way OxygenOS lights its glass. */
    val paneHalo: Int,
    /** Strength of the surface grain; frosted glass is not optically smooth. */
    val grainAlpha: Int,

    // ---- day dots, monochrome ----
    val frostTop: Int,
    val frostBottom: Int,
    val frostEdge: Int,
    val todayRing: Int,
    val futureCell: Int,
    /** Index is goals completed, 1..5; index 0 unused, an empty day is frosted. */
    val ramp: IntArray,

    // ---- element glyphs, the only hue on the face ----
    /** Opacity of a glyph whose goal is still open. */
    val kanjiIdleAlpha: Int,
    /** Opacity of the soft halo behind a completed glyph. */
    val kanjiGlowAlpha: Int
) {
    /** Null means draw the frosted treatment rather than a flat colour. */
    fun cellColor(completed: Int): Int? =
        if (completed <= 0) null else ramp[completed.coerceAtMost(ramp.lastIndex)]

    // Data classes compare IntArray by reference, so equality would be quietly
    // wrong without this.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassTheme) return false
        return paneFill == other.paneFill &&
            frostTop == other.frostTop &&
            todayRing == other.todayRing &&
            kanjiIdleAlpha == other.kanjiIdleAlpha &&
            ramp.contentEquals(other.ramp)
    }

    override fun hashCode(): Int = 31 * paneFill + ramp.contentHashCode()

    companion object {
        /** Light glass on a dark wallpaper. */
        val OnDarkWallpaper = GlassTheme(
            paneFill = Color.argb(34, 255, 255, 255),
            paneRim = Color.argb(56, 255, 255, 255),
            paneBevelDark = Color.argb(44, 0, 0, 0),
            paneGlint = Color.argb(150, 255, 255, 255),
            paneShadow = Color.argb(92, 0, 0, 0),
            paneHalo = Color.argb(46, 255, 255, 255),
            grainAlpha = 9,

            frostTop = Color.argb(60, 255, 255, 255),
            frostBottom = Color.argb(46, 255, 255, 255),
            frostEdge = Color.argb(64, 255, 255, 255),
            todayRing = Color.argb(240, 255, 255, 255),
            futureCell = Color.argb(18, 255, 255, 255),
            ramp = intArrayOf(
                Color.TRANSPARENT,
                Color.argb(84, 255, 255, 255),
                Color.argb(122, 255, 255, 255),
                Color.argb(162, 255, 255, 255),
                Color.argb(202, 255, 255, 255),
                Color.argb(242, 255, 255, 255)
            ),

            kanjiIdleAlpha = 104,
            kanjiGlowAlpha = 96
        )

        /** Dark glass on a pale wallpaper, where white on white would vanish. */
        val OnLightWallpaper = GlassTheme(
            paneFill = Color.argb(30, 16, 18, 16),
            paneRim = Color.argb(52, 255, 255, 255),
            paneBevelDark = Color.argb(30, 0, 0, 0),
            paneGlint = Color.argb(190, 255, 255, 255),
            paneShadow = Color.argb(58, 0, 0, 0),
            paneHalo = Color.argb(30, 255, 255, 255),
            grainAlpha = 7,

            frostTop = Color.argb(48, 22, 24, 22),
            frostBottom = Color.argb(34, 22, 24, 22),
            frostEdge = Color.argb(40, 255, 255, 255),
            todayRing = Color.argb(232, 24, 26, 24),
            futureCell = Color.argb(14, 22, 24, 22),
            ramp = intArrayOf(
                Color.TRANSPARENT,
                Color.argb(74, 20, 22, 20),
                Color.argb(112, 20, 22, 20),
                Color.argb(152, 20, 22, 20),
                Color.argb(194, 20, 22, 20),
                Color.argb(236, 20, 22, 20)
            ),

            kanjiIdleAlpha = 120,
            kanjiGlowAlpha = 70
        )
    }
}
