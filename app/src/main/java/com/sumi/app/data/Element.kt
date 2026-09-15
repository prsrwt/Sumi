package com.sumi.app.data

import android.graphics.Color

/**
 * The Godai, the classical Japanese five elements.
 *
 * A fixed set of exactly five, which is what makes the mapping onto five goals
 * bijective: every goal owns one element and no element is shared. Logged time is
 * sorted by element, so the set is closed on purpose - five known glyphs the eye
 * learns by position, rather than an open-ended tag list that grows until it
 * stops telling you anything.
 *
 * Fire is orange rather than the obvious red: nothing in this app is allowed to
 * read as an alert.
 */
enum class Element(
    val kanji: String,
    val color: Int,
    val displayName: String,
    /** The kind of activity this element is a natural fit for. Shown during assignment. */
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

        /** Elements are stored by enum name; anything unrecognised reads as untagged. */
        fun fromStored(value: String?): Element? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Time logged without an element. Not a sixth element: it has no goal, no spoke
 * on the pentagon and no place in the composer. It only gives untagged time a
 * mark of its own - 無, "without" - in a grey that sits back from the five colours.
 */
object Untagged {
    const val KANJI = "無"
    const val NAME = "Untagged"
    val color: Int = Color.rgb(150, 150, 150)
}
