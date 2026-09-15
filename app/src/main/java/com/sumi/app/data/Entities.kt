package com.sumi.app.data

import androidx.room.Entity
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
    val quietEndMinute: Int
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

data class Entry(
    val id: Long,
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val text: String?,
    val element: Element?
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
