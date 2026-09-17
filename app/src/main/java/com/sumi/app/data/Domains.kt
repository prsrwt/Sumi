package com.sumi.app.data

/**
 * The catalogue: the parts of life Sumi can offer under each element, and the words
 * each one comes with.
 *
 * This is a list of ideas, not data. Nothing here is written to the database until
 * somebody chooses it, so a phone holds the handful of parts of life its owner
 * picked and the words under those, never the whole catalogue. [SumiDatabase] says
 * the same thing from the other side.
 *
 * Three levels, and each one answers a different question. The element is the
 * spoke on the pentagon. A part of life is a corner of your life wide enough to
 * grow over years: "Health" holds the run, the cooking, the sleep you protect and
 * the doctor, while "gym" holds an hour a week and would sit at the centre of the
 * pentagon forever. A word is what you actually did, and it lives under one part of
 * life, so tapping it says both things at once.
 *
 * Both lists come from the two classifications that already did this work
 * properly: the nine divisions of the UN classification for time use statistics
 * (ICATUS 2016), and the activity lexicon of the American Time Use Survey, which
 * names around 460 activities in eighteen categories. Sumi keeps the sorting and
 * drops the survey language: "Food and drink preparation" becomes "cooking".
 *
 * The list is meant to fit a life anywhere, so it holds the work most apps leave
 * out: unpaid work at home, looking after an elder, a stall or a trade of your own,
 * and the land and livestock that ICATUS counts as production for own use, which is
 * where a great deal of the world's time actually goes.
 *
 * No word appears under two parts of life. A word is a whole log, carrying its part
 * of life and its element with it, so the same word in two places would be a
 * question with no answer. Anybody can still put their own word wherever they like.
 *
 * The element beside each part of life is a suggestion drawn from that element's own
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

/**
 * Where a word Sumi already knows belongs: a part of life you have it under, or
 * one the catalogue would add for it.
 */
data class WordHome(
    val name: String,
    val element: Element,
    /** True when this is already one of your parts of life, not a new one. */
    val yours: Boolean
)

object Domains {

    val common: List<DomainIdea> = listOf(
        // 地 Earth: the body, the house, the ground under everything.
        DomainIdea(
            name = "Health",
            holds = "exercise, food, the sleep you protect, the doctor",
            element = Element.EARTH,
            words = listOf(
                "walk", "run", "gym", "yoga", "sport", "stretching",
                "cycling", "swimming", "doctor", "medicine", "early night"
            )
        ),
        DomainIdea(
            name = "Home and care",
            holds = "cooking, cleaning, laundry, keeping the house running",
            element = Element.EARTH,
            words = listOf(
                "cooking", "cleaning", "dishes", "laundry", "ironing", "groceries",
                "tidying", "fixing things", "rubbish", "garden", "pets"
            )
        ),
        DomainIdea(
            name = "Money and admin",
            holds = "bills, banking, paperwork, the errands nobody enjoys",
            element = Element.EARTH,
            words = listOf(
                "bills", "banking", "paperwork", "taxes", "insurance",
                "forms", "queueing", "budgeting", "appointments", "renewals"
            )
        ),
        DomainIdea(
            name = "Land and livestock",
            holds = "growing, keeping animals, fetching water or fuel for the house",
            element = Element.EARTH,
            words = listOf(
                "field", "livestock", "milking", "sowing", "harvest",
                "watering", "fetching water", "firewood", "feed", "market day"
            )
        ),
        DomainIdea(
            name = "Getting around",
            holds = "the commute and every other journey between things",
            element = Element.EARTH,
            words = listOf(
                "commute", "bus", "train", "driving", "riding",
                "on foot", "waiting", "traffic", "fuel stop"
            )
        ),

        // 水 Water: the people, and the time that flows between you.
        DomainIdea(
            name = "People",
            holds = "family, friends, the call home",
            element = Element.WATER,
            words = listOf(
                "family", "friends", "call home", "meal together", "visiting",
                "messages", "catching up", "guests", "a long talk"
            )
        ),
        DomainIdea(
            name = "Partner",
            holds = "the person you share your life with, and the time that is theirs",
            element = Element.WATER,
            words = listOf(
                "time together", "date", "talking it through", "walk together",
                "planning together", "small kindness", "evening in"
            )
        ),
        DomainIdea(
            name = "Children",
            holds = "the time that is theirs: play, school runs, bedtime",
            element = Element.WATER,
            words = listOf(
                "playing", "reading together", "school run", "homework help",
                "bedtime", "bath time", "feeding", "park", "parents meeting"
            )
        ),
        DomainIdea(
            name = "Looking after someone",
            holds = "an elder, a patient, anyone who needs you nearby",
            element = Element.WATER,
            words = listOf(
                "elders", "hospital", "medicines", "sitting with", "errands for",
                "washing and dressing", "their appointments", "night watch"
            )
        ),
        DomainIdea(
            name = "Community",
            holds = "neighbours, groups, volunteering, showing up for others",
            element = Element.WATER,
            words = listOf(
                "volunteering", "neighbours", "group meeting", "helping out",
                "wedding", "funeral", "festival", "committee", "collection"
            )
        ),

        // 火 Fire: what you push at, and what it produces.
        DomainIdea(
            name = "Work",
            holds = "the job, the trade, whatever pays",
            element = Element.FIRE,
            words = listOf(
                "deep work", "meetings", "email", "calls", "shift",
                "reports", "overtime", "site work", "training day"
            )
        ),
        DomainIdea(
            name = "Business",
            holds = "your own shop, stall or trade, and what keeps it alive",
            element = Element.FIRE,
            words = listOf(
                "customers", "stall", "stock", "accounts",
                "suppliers", "orders", "delivery", "pricing"
            )
        ),
        DomainIdea(
            name = "Study",
            holds = "school, a course, exams and the reading they take",
            element = Element.FIRE,
            words = listOf(
                "class", "lecture", "revision", "assignment",
                "exam prep", "lab", "library", "group study", "tuition"
            )
        ),
        DomainIdea(
            name = "Building",
            holds = "the thing you are making, beside what pays now",
            element = Element.FIRE,
            words = listOf(
                "side project", "prototype", "coding", "designing",
                "drafting", "first customers", "launch", "pitching"
            )
        ),
        DomainIdea(
            name = "Looking for work",
            holds = "applications, interviews, the search itself",
            element = Element.FIRE,
            words = listOf(
                "applications", "interview", "portfolio", "outreach",
                "preparing", "listings", "follow-up", "references"
            )
        ),

        // 風 Wind: what changes you.
        DomainIdea(
            name = "Growth",
            holds = "learning, practice, anything that changes you",
            element = Element.WIND,
            words = listOf(
                "reading", "a course", "language", "tutorial",
                "taking notes", "a talk", "questions", "trying it out"
            )
        ),
        DomainIdea(
            name = "Skills",
            holds = "the thing you are getting better at, hour by hour",
            element = Element.WIND,
            words = listOf(
                "instrument", "drawing", "singing", "practice",
                "drills", "coaching", "technique", "a lesson"
            )
        ),
        DomainIdea(
            name = "Making",
            holds = "art, music, writing, anything you make for its own sake",
            element = Element.WIND,
            words = listOf(
                "writing", "music", "painting", "photography",
                "craft", "sewing", "woodwork", "editing", "recording"
            )
        ),
        DomainIdea(
            name = "Thinking",
            holds = "planning, reviewing, working out what comes next",
            element = Element.WIND,
            words = listOf(
                "planning", "review", "deciding", "reading up",
                "writing it down", "a walk to think", "weighing it up"
            )
        ),
        DomainIdea(
            name = "Teaching",
            holds = "passing on what you know: showing, explaining, mentoring",
            element = Element.WIND,
            words = listOf(
                "explaining", "showing someone", "mentoring",
                "answering", "preparing to teach", "feedback"
            )
        ),

        // 空 Void: the quiet, and what it is for.
        DomainIdea(
            name = "Rest",
            holds = "quiet, stillness, doing nothing on purpose",
            element = Element.VOID,
            words = listOf(
                "nap", "sitting quietly", "tea", "doing nothing",
                "lying down", "listening to music", "breathing", "a slow morning"
            )
        ),
        DomainIdea(
            name = "Play",
            holds = "games, films, the scroll, whatever is simply enjoyed",
            element = Element.VOID,
            words = listOf(
                "games", "tv", "film", "scrolling",
                "hobby", "going out", "a match with friends", "cards"
            )
        ),
        DomainIdea(
            name = "Purpose",
            holds = "faith, meditation, reflection, what you pass on",
            element = Element.VOID,
            words = listOf(
                "prayer", "meditation", "worship", "reflection",
                "journal", "service", "fasting", "scripture", "gratitude"
            )
        ),
        DomainIdea(
            name = "Own time",
            holds = "the hours that belong to nobody else",
            element = Element.VOID,
            words = listOf(
                "a walk alone", "bath", "sitting outside", "reading for pleasure",
                "quiet", "coffee alone", "an early start"
            )
        )
    )

    /** The ones suggested first when adding a part of life under an element. */
    fun under(element: Element): List<DomainIdea> = common.filter { it.element == element }

    /**
     * The part of life a known word sits under. "gym" is Health's, "cooking" is
     * Home and care's, and a word nobody has written down before is nobody's.
     */
    fun ideaForWord(word: String): DomainIdea? {
        val clean = word.trim()
        if (clean.isBlank()) return null
        return common.firstOrNull { idea -> idea.words.any { it.equals(clean, ignoreCase = true) } }
    }

    /** What a part of life from the catalogue holds, for the line under its name. */
    fun holdsFor(name: String): String? = ideaFor(name)?.holds

    /** The words a new part of life starts with, so the composer has something to offer. */
    fun wordsFor(name: String): List<String> = ideaFor(name)?.words.orEmpty()

    private fun ideaFor(name: String): DomainIdea? =
        common.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
