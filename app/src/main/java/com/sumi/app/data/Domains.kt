package com.sumi.app.data

/**
 * The parts of life Sumi offers under each element, and the words each one starts
 * with.
 *
 * A domain has to hold many activities over years or its spoke can never grow:
 * "Health" holds the run, the cooking, the sleep you protect and the doctor, while
 * "gym" holds an hour a week and would sit at the centre of the pentagon forever.
 * So a domain is a part of a life, and the words for what you actually did live a
 * level below it.
 *
 * Both lists are taken from the two classifications that already did this work
 * properly: the nine divisions of the UN classification for time use statistics
 * (ICATUS 2016), and the activity lexicon of the American Time Use Survey, which
 * names around 460 activities in eighteen categories. Sumi keeps the sorting and
 * drops the survey language: "Food and drink preparation" becomes "cooking".
 *
 * The list is meant to fit a life anywhere, so it holds the work most apps leave
 * out: unpaid work at home, looking after an elder, and the land and livestock
 * that ICATUS counts as production for own use, which is where a great deal of the
 * world's time actually goes.
 *
 * The element beside each domain is a suggestion drawn from that element's own
 * affinity, never a rule. Study is 火 for a student, whose study is their work, and
 * 風 for someone taking a course beside a job, so anything can be put anywhere, and
 * writing your own is always there for a life these words do not fit.
 */
data class DomainIdea(
    val name: String,
    /** What this one holds, so nobody has to guess where an activity belongs. */
    val holds: String,
    /** Where it sits naturally, offered first when adding under that element. */
    val element: Element,
    /** The words it starts with, so the composer has something to offer on day one. */
    val words: List<String>
)

object Domains {

    val common: List<DomainIdea> = listOf(
        // 地 Earth: the body, the house, the ground under everything.
        DomainIdea(
            name = "Health",
            holds = "exercise, food, the sleep you protect, the doctor",
            element = Element.EARTH,
            words = listOf("walk", "run", "gym", "yoga", "sport", "stretching", "sleep", "doctor")
        ),
        DomainIdea(
            name = "Home and care",
            holds = "cooking, cleaning, laundry, looking after someone",
            element = Element.EARTH,
            words = listOf("cooking", "cleaning", "dishes", "laundry", "groceries", "repairs", "garden", "pets")
        ),
        DomainIdea(
            name = "Money and admin",
            holds = "bills, banking, paperwork, the errands nobody enjoys",
            element = Element.EARTH,
            words = listOf("bills", "banking", "paperwork", "taxes", "insurance", "queue", "repairs")
        ),
        DomainIdea(
            name = "Land and livestock",
            holds = "growing, keeping animals, fetching water or fuel for the house",
            element = Element.EARTH,
            words = listOf("field", "livestock", "harvest", "water", "firewood", "market")
        ),
        DomainIdea(
            name = "Getting around",
            holds = "the commute and every other journey between things",
            element = Element.EARTH,
            words = listOf("commute", "walking there", "bus", "train", "driving", "waiting")
        ),

        // 水 Water: the people, and the time that flows between you.
        DomainIdea(
            name = "People",
            holds = "family, friends, the call home",
            element = Element.WATER,
            words = listOf("family", "friends", "call home", "meal together", "visiting", "message")
        ),
        DomainIdea(
            name = "Children",
            holds = "the time that is theirs: play, school runs, bedtime",
            element = Element.WATER,
            words = listOf("playing", "reading together", "school run", "homework help", "bedtime", "bath")
        ),
        DomainIdea(
            name = "Looking after someone",
            holds = "an elder, a patient, anyone who needs you nearby",
            element = Element.WATER,
            words = listOf("elders", "hospital", "medicines", "sitting with", "errands for")
        ),
        DomainIdea(
            name = "Community",
            holds = "neighbours, groups, volunteering, showing up for others",
            element = Element.WATER,
            words = listOf("volunteering", "neighbours", "group", "helping out", "event")
        ),

        // 火 Fire: what you push at, and what it produces.
        DomainIdea(
            name = "Work",
            holds = "the job, the trade, whatever pays",
            element = Element.FIRE,
            words = listOf("deep work", "meetings", "email", "calls", "shift", "customers", "field work")
        ),
        DomainIdea(
            name = "Study",
            holds = "school, a course, exams and the reading they take",
            element = Element.FIRE,
            words = listOf("class", "lecture", "revision", "assignment", "exam prep", "notes", "lab")
        ),
        DomainIdea(
            name = "Building",
            holds = "the thing you are making, beside what pays now",
            element = Element.FIRE,
            words = listOf("side project", "writing", "designing", "coding", "customers", "planning")
        ),
        DomainIdea(
            name = "Looking for work",
            holds = "applications, interviews, the search itself",
            element = Element.FIRE,
            words = listOf("applications", "interview", "portfolio", "outreach", "preparing")
        ),

        // 風 Wind: what changes you.
        DomainIdea(
            name = "Growth",
            holds = "learning, practice, anything that changes you",
            element = Element.WIND,
            words = listOf("reading", "course", "practice", "language", "tutorial", "notes")
        ),
        DomainIdea(
            name = "Skills",
            holds = "the thing you are getting better at, hour by hour",
            element = Element.WIND,
            words = listOf("instrument", "drawing", "cooking skills", "training", "drills")
        ),
        DomainIdea(
            name = "Making",
            holds = "art, music, writing, anything you make for its own sake",
            element = Element.WIND,
            words = listOf("writing", "music", "art", "photography", "craft", "building something")
        ),
        DomainIdea(
            name = "Thinking",
            holds = "planning, reviewing, working out what comes next",
            element = Element.WIND,
            words = listOf("planning", "review", "notes", "deciding", "reading up")
        ),

        // 空 Void: the quiet, and what it is for.
        DomainIdea(
            name = "Rest",
            holds = "quiet, play, doing nothing on purpose",
            element = Element.VOID,
            words = listOf("nap", "sitting quietly", "tea", "music", "nothing")
        ),
        DomainIdea(
            name = "Play",
            holds = "games, films, the scroll, whatever is simply enjoyed",
            element = Element.VOID,
            words = listOf("games", "tv", "film", "scrolling", "hobby", "going out")
        ),
        DomainIdea(
            name = "Purpose",
            holds = "faith, meditation, reflection, what you pass on",
            element = Element.VOID,
            words = listOf("prayer", "meditation", "worship", "reflection", "journal", "service")
        ),
        DomainIdea(
            name = "Own time",
            holds = "the hours that belong to nobody else",
            element = Element.VOID,
            words = listOf("reading", "walk alone", "bath", "sitting outside", "quiet")
        )
    )

    /** The ones suggested first when adding a part of life under an element. */
    fun under(element: Element): List<DomainIdea> = common.filter { it.element == element }

    /** What a domain from the list holds, for the line under its name. */
    fun holdsFor(name: String): String? = ideaFor(name)?.holds

    /** The words a new domain starts with, so the composer has something to offer. */
    fun wordsFor(name: String): List<String> = ideaFor(name)?.words.orEmpty()

    private fun ideaFor(name: String): DomainIdea? =
        common.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
