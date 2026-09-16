package com.sumi.app.data

/**
 * Ready-made sets of five, so nobody has to invent their own life categories from
 * an empty screen.
 *
 * Each preset is one way of collapsing the nine divisions of the UN classification
 * for time use statistics (ICATUS 2016) into five spokes. Nine into five always
 * drops something, and which thing gets dropped is the whole difference between
 * these rows: a job and a household cannot both have a spoke and leave room for
 * learning. The blurb says what each one gives up rather than pretending it fits
 * everyone.
 *
 * The names are deliberately broad. A slot has to be able to hold many different
 * activities over years, or its spoke can never grow: "Health" holds the run, the
 * cooking, the sleep you protect and the doctor, while "gym" holds one hour a week
 * and would sit at the centre of the pentagon forever.
 */
data class Preset(
    val title: String,
    /** Who it is for, and what it gives up. One line. */
    val blurb: String,
    /** A name per element. Empty strings mean the blank preset. */
    val names: Map<Element, String>,
    /**
     * An example of the shape a life like this tends to draw, each spoke relative
     * to the longest one, exactly as the Balance pentagon is drawn. Illustrative,
     * not a target and not a measurement: the point is that every one of them
     * leans, so an uneven pentagon reads as normal rather than as failure.
     */
    val shape: Map<Element, Float>
) {
    val isBlank: Boolean get() = names.values.all { it.isBlank() }
}

object Presets {

    val all: List<Preset> = listOf(
        Preset(
            title = "A balanced life",
            blurb = "A good start when none of the others fit. Unpaid work at home has no spoke of its own.",
            names = mapOf(
                Element.EARTH to "Health",
                Element.WATER to "People",
                Element.FIRE to "Work",
                Element.WIND to "Growth",
                Element.VOID to "Rest"
            ),
            shape = mapOf(
                Element.EARTH to 0.50f,
                Element.WATER to 0.52f,
                Element.FIRE to 1f,
                Element.WIND to 0.40f,
                Element.VOID to 0.48f
            )
        ),
        Preset(
            title = "Work and home",
            blurb = "A job and a household, the double shift. Learning gives up its place.",
            names = mapOf(
                Element.EARTH to "Health",
                Element.WATER to "People",
                Element.FIRE to "Job",
                Element.WIND to "Home and care",
                Element.VOID to "Rest"
            ),
            shape = mapOf(
                Element.EARTH to 0.32f,
                Element.WATER to 0.44f,
                Element.FIRE to 1f,
                Element.WIND to 0.74f,
                Element.VOID to 0.30f
            )
        ),
        Preset(
            title = "The home is the work",
            blurb = "The house and the people in it are the work. Paid work gives up its place.",
            names = mapOf(
                Element.EARTH to "Health",
                Element.WATER to "Family",
                Element.FIRE to "Home and care",
                Element.WIND to "Growth",
                Element.VOID to "Own time"
            ),
            shape = mapOf(
                Element.EARTH to 0.40f,
                Element.WATER to 0.70f,
                Element.FIRE to 1f,
                Element.WIND to 0.26f,
                Element.VOID to 0.38f
            )
        ),
        Preset(
            title = "Studying",
            blurb = "Study is the work, and skills are what you build beside it. Paid work gives up its place.",
            names = mapOf(
                Element.EARTH to "Health",
                Element.WATER to "Family and friends",
                Element.FIRE to "Study",
                Element.WIND to "Skills",
                Element.VOID to "Rest"
            ),
            shape = mapOf(
                Element.EARTH to 0.42f,
                Element.WATER to 0.55f,
                Element.FIRE to 1f,
                Element.WIND to 0.50f,
                Element.VOID to 0.46f
            )
        ),
        Preset(
            title = "My own work",
            blurb = "Your own trade, shop or practice: earning today is not the same time as building the thing.",
            names = mapOf(
                Element.EARTH to "Health",
                Element.WATER to "People",
                Element.FIRE to "Earning",
                Element.WIND to "Building",
                Element.VOID to "Rest"
            ),
            shape = mapOf(
                Element.EARTH to 0.34f,
                Element.WATER to 0.42f,
                Element.FIRE to 1f,
                Element.WIND to 0.58f,
                Element.VOID to 0.32f
            )
        ),
        Preset(
            title = "Later life",
            blurb = "Where the work becomes purpose: community, family, what you pass on.",
            names = mapOf(
                Element.EARTH to "Health",
                Element.WATER to "People",
                Element.FIRE to "Purpose",
                Element.WIND to "Learning",
                Element.VOID to "Rest"
            ),
            shape = mapOf(
                Element.EARTH to 0.58f,
                Element.WATER to 0.74f,
                Element.FIRE to 0.44f,
                Element.WIND to 0.48f,
                Element.VOID to 1f
            )
        ),
        Preset(
            title = "Start blank",
            blurb = "Five empty rows to name yourself.",
            names = Element.entries.associateWith { "" },
            shape = Element.entries.associateWith { 0f }
        )
    )

    /**
     * The shape part way between two presets, so the drawing flows while the wheel
     * turns instead of jumping at each name.
     */
    fun shapeBetween(from: Preset, to: Preset, fraction: Float): Map<Element, Float> {
        val f = fraction.coerceIn(0f, 1f)
        return Element.entries.associateWith { element ->
            val a = from.shape[element] ?: 0f
            val b = to.shape[element] ?: 0f
            a + (b - a) * f
        }
    }

    /**
     * The preset's names in slot order for the goals as they stand, so a preset
     * lands on the right rows even after someone has swapped which goal holds
     * which element.
     */
    fun namesForSlots(preset: Preset, goals: List<Goal>): List<String> =
        goals.sortedBy { it.slot }.map { preset.names[it.element] ?: "" }
}
