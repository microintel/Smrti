package com.smrti.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smrti.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Reminders run on a self-rescheduling alarm chain, so any interval from 1 minute up works
 * (WorkManager can't go below 15 minutes).
 */
object Scheduler {

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** (Re)schedules the next reminder one interval from now. Replaces any pending one. */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        var at = System.currentTimeMillis() + Prefs.intervalMinutes(context) * 60_000L
        if (isQuiet(context, at)) at = quietEndAfter(context, at)
        Prefs.setNextAt(context, at)
        val pi = pendingIntent(context)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            // exact-alarm permission was revoked a moment ago: fall back to an inexact alarm
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        Prefs.setNextAt(context, 0L)
    }

    /** Called on app start: restores the alarm if it was lost (force stop, missed while off). */
    fun ensureScheduled(context: Context) {
        if (Prefs.isEnabled(context) && Prefs.nextAt(context) < System.currentTimeMillis()) {
            scheduleNext(context)
        }
    }

    fun isQuiet(context: Context, timeMillis: Long): Boolean {
        if (!Prefs.quietOn(context)) return false
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = Prefs.quietStart(context)
        val end = Prefs.quietEnd(context)
        return when {
            start == end -> false
            start < end -> minute in start until end
            else -> minute >= start || minute < end
        }
    }

    private fun quietEndAfter(context: Context, after: Long): Long {
        val end = Prefs.quietEnd(context)
        val cal = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, end / 60)
            set(Calendar.MINUTE, end % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= after) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

/** Fires when the alarm goes off: posts a reminder, then schedules the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Prefs.isEnabled(app)) {
                    try {
                        if (!Scheduler.isQuiet(app, System.currentTimeMillis())) Notifier.sendNext(app)
                    } catch (_: Throwable) {
                        // a failed notification must not crash the app; the next one is still scheduled
                    }
                    Scheduler.scheduleNext(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Restores the alarm after reboot, app update, or a change in exact-alarm permission. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (Prefs.isEnabled(context)) Scheduler.scheduleNext(context)
        } catch (_: Throwable) { }
    }
}
