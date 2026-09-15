package com.sumi.app.data

import java.time.Instant

/**
 * The ways Sumi asks where your time has gone.
 *
 * Varied so the question does not become wallpaper the eye stops reading. Every
 * phrasing is gentle and open: none of them demand an answer, imply there is a
 * right one, or treat the gap since the last entry as a failing. Kept short, so
 * each fits a one-row widget.
 */
object Prompts {

    private val questions = listOf(
        "What has this hour held?",
        "Where has your time gone?",
        "Where is your attention?",
        "What fills this moment?",
        "What have you been tending to?",
        "How was this hour spent?",
        "What is in your hands?",
        "What filled the last while?"
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

    /** The very first ask, before anything has been logged. */
    const val DEFAULT = "What is this hour holding?"
}
