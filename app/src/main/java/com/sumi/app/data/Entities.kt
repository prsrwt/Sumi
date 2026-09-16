package com.sumi.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Five goals, five elements, always. */
const val GOAL_COUNT = 5

// ---------------------------------------------------------------------------
// Stored rows
// ---------------------------------------------------------------------------

/**
 * One of the five goal slots. Rows 0..4 always exist; the database seeds them.
 * The unique index on element is what keeps the goal-to-element mapping
 * one-to-one at the storage level rather than trusting every caller to.
 */
@Entity(tableName = "goals", indices = [Index(value = ["element"], unique = true)])
data class GoalEntity(
    @PrimaryKey val slot: Int,
    val name: String,
    val element: String
)

/**
 * A stretch of time and what it was spent on.
 *
 * Times are stored as UTC instants plus the zone they were logged in, so a
 * timesheet stays correct across daylight saving changes and travel. Either the
 * text or the element may be missing, never both.
 */
/**
 * A part of life under one element: Health under 地, Work under 火. An element can
 * hold several, which is what keeps the five spokes broad while the words stay
 * yours. The element is stored by name, as everywhere else.
 */
@Entity(tableName = "domains", indices = [Index(value = ["element", "name"], unique = true)])
data class DomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val element: String,
    /** Where it sits in its element's list, so the order is the user's own. */
    val position: Int
)

/**
 * A word inside a domain: run, thesis, cooking. Free to invent while logging, the
 * way a board takes a new pin.
 *
 * [uses] and [lastUsedAt] are what the composer ranks by, so the words you
 * actually use rise to the front without anybody managing a list.
 */
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = DomainEntity::class,
            parentColumns = ["id"],
            childColumns = ["domainId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["domainId", "name"], unique = true)]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainId: Long,
    val name: String,
    val uses: Int = 0,
    val lastUsedAt: Long? = null
)

@Entity(
    tableName = "entries",
    indices = [Index("startMillis"), Index("endMillis")]
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val zoneId: String,
    val text: String?,
    val element: String?,
    /**
     * What it was logged as, if anything. Kept as references rather than copied
     * words, so renaming an activity carries its whole history with it. Nothing
     * points back: an entry whose activity is deleted simply loses the tag and
     * keeps its note, which is the honest outcome.
     */
    val domainId: Long? = null,
    val activityId: Long? = null,
    val updatedAt: Long,
    /** When this row was last copied to Google Sheets; null means never. */
    val syncedAt: Long?,
    /**
     * Soft delete. A synced row that is hard-deleted leaves nothing behind to
     * tell the sync to remove it from the sheet, so deletion is a timestamp and
     * every read filters it out.
     */
    val deletedAt: Long? = null
)

/** A single row of preferences. Room rather than a second storage system. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val askIntervalMinutes: Int,
    val quietStartMinute: Int,
    val quietEndMinute: Int,
    /** When the first-launch introduction was finished or skipped; null shows it. */
    val onboardedAt: Long? = null
)

/**
 * The link to the user's Google Sheet. The row exists only while connected, so
 * "connected" is simply "a row is here". No access token is ever stored: Google
 * Play services keeps those, and hands a fresh one over when asked.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    val accountEmail: String,
    val spreadsheetId: String,
    val lastSyncedAt: Long?,
    /** Google stopped handing out tokens without asking the user again. */
    val needsReconnect: Boolean
)

/**
 * A month ("2026-09") whose tab in the sheet no longer matches the phone. It is
 * written in the same transaction as the change that caused it, so an entry can
 * never be saved without its month also being queued for the sheet.
 */
@Entity(tableName = "dirty_months")
data class DirtyMonthEntity(
    @PrimaryKey val month: String
)

// ---------------------------------------------------------------------------
// Domain
// ---------------------------------------------------------------------------

data class Goal(
    val slot: Int,
    val name: String,
    val element: Element
) {
    /** An unnamed goal is shown by its element, so the composer never has a blank key. */
    val displayName: String get() = name.ifBlank { element.displayName }
}

/**
 * What the user calls an element's time: their goal's name ("Workout") wherever
 * one is set, the element's own name otherwise, and "Untagged" for no element.
 * Every screen names time through this, so a rename shows up everywhere at once.
 */
fun List<Goal>.nameFor(element: Element?): String =
    if (element == null) Untagged.NAME
    else firstOrNull { it.element == element }?.displayName ?: element.displayName

/** An activity with the element of the domain holding it, as the composer needs it. */
data class ActivityWithElement(
    val id: Long,
    val name: String,
    val domainId: Long,
    val element: String,
    val uses: Int,
    val lastUsedAt: Long?
)

/** One of an element's parts of life, as the app talks about it. */
data class Domain(
    val id: Long,
    val name: String,
    val element: Element,
    val position: Int
)

/** A word the composer can offer: what it is called, and where it belongs. */
data class Word(
    val id: Long,
    val name: String,
    val domainId: Long,
    val element: Element
)

/** A word inside a domain, with how much it has been used. */
data class Activity(
    val id: Long,
    val domainId: Long,
    val name: String,
    val uses: Int,
    val lastUsedAt: Instant?
)

data class Entry(
    val id: Long,
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val text: String?,
    val element: Element?,
    val domainId: Long? = null,
    val activityId: Long? = null
) {
    val duration: Duration get() = Duration.between(start, end)

    /** Overlapping length with [from, to), for clipping to a day or a window. */
    fun overlapWith(from: Instant, to: Instant): Duration {
        val s = maxOf(start, from)
        val e = minOf(end, to)
        return if (e > s) Duration.between(s, e) else Duration.ZERO
    }
}

data class Settings(
    val askInterval: Duration,
    val quietStart: LocalTime,
    val quietEnd: LocalTime
) {
    /**
     * Quiet hours usually wrap midnight (23:00 to 07:00), so the check has two
     * shapes. Equal start and end means no quiet hours at all.
     */
    fun isQuiet(time: LocalTime): Boolean = when {
        quietStart == quietEnd -> false
        quietStart < quietEnd -> time >= quietStart && time < quietEnd
        else -> time >= quietStart || time < quietEnd
    }

    companion object {
        val Default = Settings(
            askInterval = Duration.ofMinutes(45),
            quietStart = LocalTime.of(23, 0),
            quietEnd = LocalTime.of(7, 0)
        )
    }
}

data class SheetsLink(
    val accountEmail: String,
    val spreadsheetId: String,
    val lastSyncedAt: Instant?,
    val needsReconnect: Boolean
)
