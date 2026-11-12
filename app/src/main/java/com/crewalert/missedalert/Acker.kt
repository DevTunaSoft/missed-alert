package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object Acker {
    /** Acknowledge everything pending: stop the alarm, clear our reminder, remember what was acked. */
    /** Opening one source app acknowledges only that alert; other pending alerts remain. */
    fun ackOne(c: Context, key: String, source: String = "tap_reminder", postTime: Long = -1L) {
        synchronized(AlertStore) {
        if (postTime <= 0L || !AlertStore.acknowledgeIfMatches(c, key, postTime)) {
            EventLog.write(c, "acknowledge_one_stale", "source" to source)
            return
        }
        val left = AlertStore.count(c)
        if (left == 0) {
            Scheduler.cancel(c)
            Notifier.cancel(c)
            AlertStore.resetRepeats(c)
        } else {
            // Other alerts retain their existing reminder deadline and repeat count.
            // Acknowledging one notification must never postpone the remaining alerts.
            Scheduler.ensureScheduled(c)
        }
        EventLog.write(c, "acknowledged_one", "source" to source, "remaining" to left)
        }
    }

    fun ackAll(c: Context, source: String = "unknown") {
        synchronized(AlertStore) {
        val reminders = AlertStore.repeats(c)
        val cleared = AlertStore.acknowledgeAll(c)
        Scheduler.cancel(c)
        Notifier.cancel(c)
        EventLog.write(c, "acknowledged", "source" to source, "cleared" to cleared, "reminders_sent" to reminders)
        }
    }
}

/** "ACKNOWLEDGE" button and swipe-to-dismiss on the reminder both land here. */
class AckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Acker.ackAll(context, "button_or_swipe")
}
