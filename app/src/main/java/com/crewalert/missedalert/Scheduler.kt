package com.crewalert.missedalert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/** Single self-rearming reminder alarm with independent watchdog recovery. */
object Scheduler {
    private fun pi(c: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        c, 1, Intent(c, ReAlertReceiver::class.java), flags or PendingIntent.FLAG_IMMUTABLE
    )

    fun isPending(c: Context): Boolean =
        AlertStore.nextAt(c) > SystemClock.elapsedRealtime() &&
            pi(c, PendingIntent.FLAG_NO_CREATE) != null

    @Synchronized
    fun scheduleNext(c: Context): Boolean {
        if (AlertStore.count(c) == 0) {
            cancel(c)
            return false
        }
        val max = Config.maxRepeats(c)
        if (max > 0 && AlertStore.repeats(c) >= max) {
            cancel(c)
            return false
        }
        val am = c.getSystemService(AlarmManager::class.java)
        val interval = Config.intervalMs(c)
        val p = pi(c, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        val exact = try {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        } catch (e: Exception) {
            EventLog.write(c, "alarm_permission_check_failed", "error" to e.javaClass.simpleName)
            false
        }
        var mode = if (exact) "alarm_clock" else "inexact_fallback"
        try {
            if (exact) {
                val show = PendingIntent.getActivity(
                    c, 4, Intent(c, StatusActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(System.currentTimeMillis() + interval, show), p
                )
            } else {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + interval, p
                )
            }
        } catch (e: SecurityException) {
            EventLog.write(c, "alarm_permission_error", "error" to e.javaClass.simpleName)
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + interval, p
                )
                mode = "inexact_permission_fallback"
            } catch (fallback: Exception) {
                AlertStore.setNextAt(c, 0L)
                EventLog.write(c, "alarm_schedule_failed", "error" to fallback.javaClass.simpleName)
                return false
            }
        } catch (e: Exception) {
            AlertStore.setNextAt(c, 0L)
            EventLog.write(c, "alarm_schedule_failed", "error" to e.javaClass.simpleName)
            return false
        }
        AlertStore.setNextAt(c, SystemClock.elapsedRealtime() + interval)
        EventLog.write(c, "alarm_scheduled", "in_ms" to interval, "mode" to mode)
        return true
    }

    fun ensureScheduled(c: Context) {
        val max = Config.maxRepeats(c)
        if (max > 0 && AlertStore.repeats(c) >= max) {
            cancel(c)
            return
        }
        if (AlertStore.count(c) > 0 && !isPending(c)) {
            EventLog.write(c, "alarm_recover", "late_ms" to
                (SystemClock.elapsedRealtime() - AlertStore.nextAt(c)).coerceAtLeast(0L))
            scheduleNext(c)
        }
    }

    @Synchronized
    fun cancel(c: Context) {
        val was = AlertStore.nextAt(c) > 0L
        pi(c, PendingIntent.FLAG_NO_CREATE)?.let {
            c.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        AlertStore.setNextAt(c, 0L)
        if (was) EventLog.write(c, "alarm_cancelled")
    }
}
