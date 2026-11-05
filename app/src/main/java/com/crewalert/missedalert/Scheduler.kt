package com.crewalert.missedalert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** One exact, doze-proof alarm that re-arms itself every interval while alerts are pending. */
object Scheduler {
    private fun pi(c: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            c, 1, Intent(c, ReAlertReceiver::class.java),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    /** True if an alarm is armed and hasn't fired yet. */
    fun isPending(c: Context): Boolean = AlertStore.nextAt(c) > SystemClock.elapsedRealtime()

    fun scheduleNext(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val interval = Config.intervalMs(c)
        val p = pi(c, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = am.canScheduleExactAlarms()
        if (exact) {
            // setAlarmClock() is the one alarm type that keeps firing on time in deep Doze.
            // setExactAndAllowWhileIdle() is throttled to ~1 per 9 minutes per app while idle,
            // which would turn "every 60 s" into "every 9 min" on a phone sitting on a desk.
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
        AlertStore.setNextAt(c, SystemClock.elapsedRealtime() + interval)
        EventLog.write(c, "alarm_scheduled", "in_ms" to interval, "mode" to (if (exact) "alarm_clock" else "inexact_fallback"))
    }

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
