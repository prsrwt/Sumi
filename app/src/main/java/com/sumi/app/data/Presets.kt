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
    /** Who lives like this, in one line. */
    val blurb: String,
    /**
     * The parts of life under each element, in order. The first is the name the
     * pentagon shows for that spoke, which is why the order matters.
     */
    val parts: Map<Element, List<String>>,
    /**
     * An example of the shape a life like this tends to draw, each spoke relative
     * to the longest one, exactly as the Balance pentagon is drawn. Illustrative,
     * not a target and not a measurement: the point is that every one of them
     * leans, so an uneven pentagon reads as normal rather than as failure.
     */
    val shape: Map<Element, Float>
) {
    /** What each spoke is called: the first part of life under it. */
    val names: Map<Element, String> get() = parts.mapValues { (_, list) -> list.firstOrNull().orEmpty() }

    val isBlank: Boolean get() = parts.values.all { it.isEmpty() }
}

object Presets {

    val all: List<Preset> = listOf(
        Preset(
            title = "A bit of everything",
            blurb = "No single thing has taken over your week.",
            parts = mapOf(
                Element.EARTH to listOf("Health", "Home and care"),
                Element.WATER to listOf("People"),
                Element.FIRE to listOf("Work"),
                Element.WIND to listOf("Growth"),
                Element.VOID to listOf("Rest")
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
            title = "A job and a home",
            blurb = "You work, and you run a household. The double shift.",
            parts = mapOf(
                Element.EARTH to listOf("Home and care", "Health"),
                Element.WATER to listOf("People"),
                Element.FIRE to listOf("Work", "Money and admin"),
                Element.WIND to listOf("Growth"),
                Element.VOID to listOf("Rest")
            ),
            shape = mapOf(
                Element.EARTH to 0.74f,
                Element.WATER to 0.44f,
                Element.FIRE to 1f,
                Element.WIND to 0.26f,
                Element.VOID to 0.30f
            )
        ),
        Preset(
            title = "The home is the work",
            blurb = "The house and the people in it are your work.",
            parts = mapOf(
                Element.EARTH to listOf("Home and care", "Health"),
                Element.WATER to listOf("Family", "Community"),
                Element.FIRE to listOf("Money and admin"),
                Element.WIND to listOf("Growth"),
                Element.VOID to listOf("Own time", "Rest")
            ),
            shape = mapOf(
                Element.EARTH to 1f,
                Element.WATER to 0.70f,
                Element.FIRE to 0.30f,
                Element.WIND to 0.26f,
                Element.VOID to 0.38f
            )
        ),
        Preset(
            title = "Studying",
            blurb = "Study is your work, and skills are what you build beside it.",
            parts = mapOf(
                Element.EARTH to listOf("Health"),
                Element.WATER to listOf("People"),
                Element.FIRE to listOf("Study"),
                Element.WIND to listOf("Growth", "Building"),
                Element.VOID to listOf("Rest", "Own time")
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
            title = "My own trade",
            blurb = "You work for yourself. Earning today and building the thing are different time.",
            parts = mapOf(
                Element.EARTH to listOf("Health"),
                Element.WATER to listOf("People"),
                Element.FIRE to listOf("Work", "Money and admin"),
                Element.WIND to listOf("Building", "Growth"),
                Element.VOID to listOf("Rest")
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
            blurb = "Work has become purpose: community, family, what you pass on.",
            parts = mapOf(
                Element.EARTH to listOf("Health", "Home and care"),
                Element.WATER to listOf("People", "Community"),
                Element.FIRE to listOf("Purpose"),
                Element.WIND to listOf("Growth"),
                Element.VOID to listOf("Rest", "Own time")
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
            title = "None of these",
            blurb = "Five empty spokes, to fill in your own way.",
            parts = Element.entries.associateWith { emptyList() },
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

    /**
     * The preset these five names came from, if they came from one at all.
     *
     * It is what tells a ready-made five apart from a five somebody wrote. There
     * is nothing to warn about when swapping one ready-made set for another: the
     * only names worth asking about are the ones a person typed themselves.
     */
    fun matching(goals: List<Goal>, names: List<String>): Preset? =
        all.firstOrNull { preset ->
            namesForSlots(preset, goals) == names.map { it.trim() }
        }
}
