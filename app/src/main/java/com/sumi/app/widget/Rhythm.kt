package com.sumi.app.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sumi.app.data.Settings
import com.sumi.app.data.SumiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the widget should next change its face, and the alarm that makes it.
 *
 * A widget only changes when something redraws it, so Sumi works out the next
 * moment the face ought to differ and asks to be woken then. Not a fixed tick:
 * the moments are when the ask interval since the last entry runs out, when
 * quiet hours begin, when they end, and midnight, when the date drawn on the
 * widget turns over. Logging early simply moves the next ask.
 */
object Rhythm {

    private const val REQUEST_CODE = 4202
    const val ACTION_TICK = "com.sumi.app.action.RHYTHM"

    /**
     * Fire a moment after the boundary rather than on it, so the redraw that
     * follows reliably sees the new state instead of racing it.
     */
    private const val SETTLE_MILLIS = 2_000L

    /**
     * Android 12+ stretches any inexact window shorter than ten minutes to ten
     * minutes, so asking for less gains nothing.
     */
    private const val WINDOW_MILLIS = 10 * 60 * 1_000L

    /**
     * The next instant at which the widget's face could change. There is always
     * one, since at the latest the date changes at midnight.
     */
    fun nextChange(latestEnd: Instant?, settings: Settings, now: Instant, zone: ZoneId): Instant {
        val hasQuietHours = settings.quietStart != settings.quietEnd
        val quietNow = settings.isQuiet(now.atZone(zone).toLocalTime())

        val candidates = buildList {
            // The date on the widget is drawn, not ticked, so a new day needs a redraw.
            add(nextOccurrence(LocalTime.MIDNIGHT, now, zone))
            if (quietNow) {
                // Whatever is due has to wait; the only change coming is quiet ending.
                add(nextOccurrence(settings.quietEnd, now, zone))
            } else {
                if (hasQuietHours) add(nextOccurrence(settings.quietStart, now, zone))
                if (latestEnd != null) {
                    val due = latestEnd.plus(settings.askInterval)
                    if (due.isAfter(now)) add(due)
                }
            }
        }
        return candidates.min()
    }

    /**
     * The next time the local clock reads [time], strictly after [now]. Resolved
     * through ZonedDateTime so a daylight-saving jump lands on a real instant
     * rather than a local time that does not exist that day.
     */
    fun nextOccurrence(time: LocalTime, now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate().atTime(time).atZone(zone)
        return if (today.toInstant().isAfter(now)) today.toInstant() else today.plusDays(1).toInstant()
    }

    suspend fun scheduleNext(context: Context) {
        val repository = SumiRepository.get(context)
        schedule(context, repository.latestEntry()?.end, repository.settingsNow())
    }

    /**
     * For callers that already hold the latest entry and settings, to skip re-reading them.
     *
     * Exact alarms are used only where the system allows them; everywhere else
     * this falls back to a ten-minute window, so no exact-alarm permission is
     * requested and lint's warning about one is expected.
     */
    @SuppressLint("MissingPermission")
    fun schedule(context: Context, latestEnd: Instant?, settings: Settings) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = pendingIntent(context)
        val next = nextChange(latestEnd, settings, Instant.now(), ZoneId.systemDefault())

        val triggerAt = next.toEpochMilli() + SETTLE_MILLIS
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canBeExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            // A window rather than setAndAllowWhileIdle, which Android gives up to an
            // hour of slack. A window may be held back while the phone dozes, which
            // costs nothing: nobody is looking at the home screen then.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, operation)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RhythmReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Redraws the widget when its face is due to change, and re-arms the next alarm.
 * Also listens for reboots, which clear pending alarms, and clock or timezone
 * changes, which move every boundary.
 */
class RhythmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // The receiver has to be exported to hear boot and clock changes, which
        // means any app could send it something. Only these are acted on.
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                SumiWidget.updateAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}

private val HANDLED_ACTIONS = setOf(
    Rhythm.ACTION_TICK,
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED
)
