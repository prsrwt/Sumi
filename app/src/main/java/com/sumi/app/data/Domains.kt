package com.sumi.app.data

/**
 * The domains Sumi offers, and the element each one sits under naturally.
 *
 * A domain has to hold many activities over years or its spoke can never grow:
 * "Health" holds the run, the cooking, the sleep you protect and the doctor, while
 * "gym" holds an hour a week and would sit at the centre of the pentagon forever.
 * So the list offers parts of a life, and the words for what you actually did live
 * a level below, as activities.
 *
 * These are the nine divisions of the UN classification for time use statistics
 * (ICATUS 2016) in ordinary words, split where a life usually splits them. The
 * element beside each is a suggestion from that element's own affinity, never a
 * rule: study is 火 for a student, whose study is their work, and 風 for someone
 * taking a course beside a job. Anything can be put anywhere, and "Write my own"
 * is always there for a life these words do not fit.
 */
data class DomainIdea(
    val name: String,
    /** What this one holds, so nobody has to guess where an activity belongs. */
    val holds: String,
    /** Where it sits naturally, offered first when adding under that element. */
    val element: Element
)

object Domains {

    val common: List<DomainIdea> = listOf(
        DomainIdea("Health", "exercise, food, the sleep you protect, the doctor", Element.EARTH),
        DomainIdea("Home and care", "cooking, cleaning, looking after someone", Element.EARTH),
        DomainIdea("Money and admin", "bills, paperwork, the errands nobody enjoys", Element.EARTH),
        DomainIdea("People", "family, friends, the call home", Element.WATER),
        DomainIdea("Community", "neighbours, groups, showing up for others", Element.WATER),
        DomainIdea("Work", "the job, the trade, whatever pays", Element.FIRE),
        DomainIdea("Building", "the thing you are making, beside what pays now", Element.FIRE),
        DomainIdea("Study", "school, a course, exams and the reading they take", Element.WIND),
        DomainIdea("Growth", "learning, practice, anything that changes you", Element.WIND),
        DomainIdea("Rest", "quiet, play, doing nothing on purpose", Element.VOID),
        DomainIdea("Purpose", "faith, reflection, what you pass on", Element.VOID),
        DomainIdea("Own time", "the hours that belong to nobody else", Element.VOID)
    )

    /** The ones suggested first when adding a domain under an element. */
    fun under(element: Element): List<DomainIdea> = common.filter { it.element == element }

    /** True when a name came from the list, so a row can show what it holds. */
    fun holdsFor(name: String): String? =
        common.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.holds
}
