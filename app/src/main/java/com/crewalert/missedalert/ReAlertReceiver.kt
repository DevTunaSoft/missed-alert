package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** A notification failure must never sever the scheduling chain. */
class ReAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lateMs = SystemClock.elapsedRealtime() - AlertStore.nextAt(context)
        val items = try { AlertStore.all(context) } catch (e: Exception) {
            EventLog.write(context, "alert_read_failed", "error" to e.javaClass.simpleName)
            emptyList()
        }
        EventLog.write(context, "alarm_fired", "late_ms" to lateMs, "pending" to items.size,
            "listener_bound" to (MissedAlertListener.instance != null))
        if (items.isEmpty()) {
            Scheduler.cancel(context)
            Notifier.cancel(context)
            return
        }
        try {
            val n = AlertStore.repeats(context) + 1
            val max = Config.maxRepeats(context)
            if (max > 0 && n > max) {
                EventLog.write(context, "repeat_limit_reached", "max_repeats" to max)
                return
            }
            Notifier.show(context, items, n)
            AlertStore.incRepeats(context)
        } catch (e: Exception) {
            EventLog.write(context, "reminder_failed", "error" to e.javaClass.simpleName)
        } finally {
            if (AlertStore.count(context) > 0) {
                val max = Config.maxRepeats(context)
                if (max <= 0 || AlertStore.repeats(context) < max) Scheduler.scheduleNext(context)
            }
            RecoveryJobService.ensureScheduled(context)
        }
    }
}
