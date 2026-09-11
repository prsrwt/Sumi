package com.beaver.app.data

import android.graphics.Color

/**
 * The Godai, the classical Japanese five elements.
 *
 * A fixed set of exactly five, which is what makes the mapping onto five goals
 * bijective: every goal owns one element and no element is shared. Keeping the
 * set closed also keeps the widget legible - five known glyphs the eye learns by
 * position, rather than arbitrary user-supplied art to render at 40px.
 *
 * Fire is orange rather than the obvious red on purpose: nothing in this app is
 * allowed to read as an alert, and a dim red glyph on a dark wallpaper does.
 */
enum class Element(
    val kanji: String,
    val color: Int,
    val displayName: String,
    /** The kind of habit this element is a natural fit for. Shown during assignment. */
    val affinity: String
) {
    EARTH("地", Color.rgb(217, 164, 65), "Earth", "stability, body, grounding"),
    WATER("水", Color.rgb(79, 163, 217), "Water", "flow, recovery, connection"),
    FIRE("火", Color.rgb(255, 138, 76), "Fire", "drive, intensity, output"),
    WIND("風", Color.rgb(143, 209, 160), "Wind", "change, learning, ideas"),
    VOID("空", Color.rgb(160, 140, 196), "Void", "spirit, reflection, rest");

    companion object {
        /** Order used before the user has assigned elements themselves. */
        val defaultOrder: List<Element> = listOf(EARTH, WATER, FIRE, WIND, VOID)

        fun forSlot(slot: Int): Element =
            defaultOrder[slot.coerceIn(0, defaultOrder.lastIndex)]
    }
}
