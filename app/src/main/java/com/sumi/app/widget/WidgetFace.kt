package com.sumi.app.widget

import com.sumi.app.data.Entry
import com.sumi.app.data.Prompts
import com.sumi.app.data.Settings
import java.time.Instant
import java.time.ZoneId

/** Which face the widget shows, and when that will next change. */
sealed interface WidgetFace {
    data object Resting : WidgetFace
    data class Asking(val question: String) : WidgetFace

    companion object {
        /**
         * Asks once the interval has passed since the last entry ended - timed from
         * your own last log rather than a fixed clock, so it never asks moments
         * after you have just answered. Nothing asks during quiet hours.
         *
         * With no entries at all it asks straight away, which doubles as the
         * first-run invitation.
         */
        fun compute(latest: Entry?, settings: Settings, now: Instant, zone: ZoneId): WidgetFace {
            if (settings.isQuiet(now.atZone(zone).toLocalTime())) return Resting
            if (latest == null) return Asking(Prompts.DEFAULT)

            val due = dueAt(latest, settings)
            return if (now.isBefore(due)) Resting else Asking(Prompts.questionFor(due))
        }

        fun dueAt(latest: Entry, settings: Settings): Instant = latest.end.plus(settings.askInterval)
    }
}
