package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Fires every interval while anything is unacknowledged. */
class ReAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // How late did the alarm fire versus when it was due? (Doze / battery-saver evidence.)
        val lateMs = SystemClock.elapsedRealtime() - AlertStore.nextAt(context)

        // Drop alerts whose original notification is already gone (read/cleared in the source app).
        MissedAlertListener.pruneAgainstActive(context)

        val items = AlertStore.all(context)
        EventLog.write(
            context, "alarm_fired",
            "late_ms" to lateMs, "pending" to items.size,
            "listener_bound" to (MissedAlertListener.instance != null)
        )
        if (items.isEmpty()) {
            Scheduler.cancel(context)
            Notifier.cancel(context)
            return
        }

        val n = AlertStore.incRepeats(context)
        val max = Config.maxRepeats(context)
        if (max > 0 && n > max) {
            EventLog.write(context, "gave_up", "max_repeats" to max)
            Acker.ackAll(context, "max_repeats") // gave up after the configured number of reminders
            return
        }

        Notifier.show(context, items, n)
        Scheduler.scheduleNext(context)
    }
}
