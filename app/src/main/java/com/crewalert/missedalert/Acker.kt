package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object Acker {
    /** Acknowledge everything pending: stop the alarm, clear our reminder, remember what was acked. */
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
