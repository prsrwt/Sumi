package com.sumi.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps the widget honest about what day it is.
 *
 * Nothing else would redraw it at midnight: today's ring would sit on yesterday,
 * and the goal dots would still show yesterday's ticks as if they were today's.
 */
object DayRollover {

    private const val REQUEST_CODE = 4201
    const val ACTION_ROLLOVER = "com.sumi.app.action.DAY_ROLLOVER"

    /**
     * Arms a single alarm for the next local midnight. Each firing arms the next
     * one, and rendering re-arms it too, so a missed alarm heals itself.
     *
     * Exact where the platform allows it without asking for anything. Below
     * Android 12 exact alarms are free; from 12 on they need the "Alarms &
     * reminders" permission, which a habit widget has no business demanding, so
     * we check whether the user happens to have granted it and otherwise accept
     * an inexact alarm with up to an hour of slack.
     */
    fun scheduleNext(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextMidnightMillis()
        val operation = pendingIntent(context)

        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarms.canScheduleExactAlarms()

        if (canBeExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun nextMidnightMillis(): Long =
        LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DayRolloverReceiver::class.java).setAction(ACTION_ROLLOVER)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Redraws the widget when the day changes, whether that came from the midnight
 * alarm, the user changing the clock or timezone, or a reboot that cleared the
 * pending alarm.
 */
class DayRolloverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                SumiWidget().updateAll(appContext)
                DayRollover.scheduleNext(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
