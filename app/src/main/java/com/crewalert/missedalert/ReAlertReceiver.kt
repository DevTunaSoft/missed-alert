package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** A notification failure must never sever the scheduling chain. */
class ReAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dueAt = AlertStore.nextAt(context)
        val lateMs = if (dueAt > 0L)
            (SystemClock.elapsedRealtime() - dueAt).coerceAtLeast(0L) else 0L
        val items = try { AlertStore.all(context) } catch (e: Exception) {
            EventLog.write(context, "alert_read_failed", "error" to e.javaClass.simpleName)
            // Unreadable storage is not an empty queue. Preserve state for recovery.
            RecoveryJobService.ensureScheduled(context)
            return
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
