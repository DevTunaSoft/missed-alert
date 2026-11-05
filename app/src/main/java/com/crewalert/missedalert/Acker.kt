package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object Acker {
    /** Acknowledge everything pending: stop the alarm, clear our reminder, remember what was acked. */
    /** Opening one source app acknowledges only that alert; other pending alerts remain. */
    fun ackOne(c: Context, key: String, source: String = "tap_reminder") {
        val item = AlertStore.all(c).firstOrNull { it.key == key } ?: return
        AlertStore.markAcked(c, listOf(item))
        AlertStore.remove(c, key)
        val left = AlertStore.count(c)
        if (left == 0) {
            Scheduler.cancel(c)
            Notifier.cancel(c)
            AlertStore.resetRepeats(c)
        } else {
            Scheduler.ensureScheduled(c)
        }
        EventLog.write(c, "acknowledged_one", "source" to source, "remaining" to left)
    }

    fun ackAll(c: Context, source: String = "unknown") {
        val items = AlertStore.all(c)
        val reminders = AlertStore.repeats(c)
        AlertStore.markAcked(c, items)
        AlertStore.clear(c)
        Scheduler.cancel(c)
        Notifier.cancel(c)
        EventLog.write(c, "acknowledged", "source" to source, "cleared" to items.size, "reminders_sent" to reminders)
    }
}

/** "ACKNOWLEDGE" button and swipe-to-dismiss on the reminder both land here. */
class AckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Acker.ackAll(context, "button_or_swipe")
}
