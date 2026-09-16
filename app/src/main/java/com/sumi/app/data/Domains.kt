package com.sumi.app.data

/**
 * The domains a goal can be, offered as a list rather than an empty text field.
 *
 * A slot has to hold many different activities over years or its spoke can never
 * grow: "Health" holds the run, the cooking, the sleep you protect and the doctor,
 * while "gym" holds an hour a week and would sit at the centre of the pentagon
 * forever. Typing invites the second kind; a list offers the first.
 *
 * These are the nine divisions of the UN classification for time use statistics
 * (ICATUS 2016) in ordinary words, split where a life usually splits them: paid
 * work apart from the work at home, study apart from growth, rest apart from the
 * people you rest with.
 *
 * Nothing is locked. "Write my own" is always the last choice, because somebody
 * will have a life none of these words fit, and Sumi is theirs too.
 */
data class Domain(
    val name: String,
    /** What this one holds, so nobody has to guess where an activity belongs. */
    val holds: String
)

object Domains {

    val common: List<Domain> = listOf(
        Domain("Health", "exercise, food, the sleep you protect, the doctor"),
        Domain("People", "family, friends, the call home"),
        Domain("Work", "the job, the trade, whatever pays"),
        Domain("Home and care", "cooking, cleaning, looking after someone"),
        Domain("Study", "school, a course, exams and the reading they take"),
        Domain("Growth", "learning, practice, anything that changes you"),
        Domain("Building", "the thing you are making, beside what pays now"),
        Domain("Purpose", "community, faith, volunteering, what you pass on"),
        Domain("Rest", "quiet, play, doing nothing on purpose"),
        Domain("Own time", "the hours that belong to nobody else")
    )

    /** True when a name came from the list, so the row can show it as a choice. */
    fun isCommon(name: String): Boolean = common.any { it.name.equals(name.trim(), ignoreCase = true) }
}
