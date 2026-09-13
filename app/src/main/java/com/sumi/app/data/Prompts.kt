package com.sumi.app.data

import java.time.Instant

/**
 * The ways Sumi asks what you are doing.
 *
 * Varied so the question does not become wallpaper the eye stops reading. Every
 * phrasing is descriptive and open - none of them imply there is a right answer,
 * or that the gap since the last entry was a failing.
 */
object Prompts {

    private val questions = listOf(
        "What are you doing?",
        "Where is your attention?",
        "What has this hour held?",
        "What are you giving your time to?",
        "What is in your hands right now?",
        "What have you been tending to?",
        "Where did the last while go?",
        "What fills this moment?"
    )

    /**
     * Chosen from the moment the question became due rather than at random, so
     * every redraw of the same ask shows the same words. A question that changed
     * each time the launcher refreshed would feel restless, not calm.
     */
    fun questionFor(anchor: Instant): String {
        val index = Math.floorMod(anchor.epochSecond / 60, questions.size.toLong()).toInt()
        return questions[index]
    }

    const val DEFAULT = "What are you doing?"
}
