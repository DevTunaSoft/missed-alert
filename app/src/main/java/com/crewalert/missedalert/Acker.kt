package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object Acker {
    /** Acknowledge everything pending: stop the alarm, clear our reminder, remember what was acked. */
    fun ackAll(c: Context) {
        AlertStore.markAcked(c, AlertStore.all(c))
        AlertStore.clear(c)
        Scheduler.cancel(c)
        Notifier.cancel(c)
    }
}

/** "ACKNOWLEDGE" button and swipe-to-dismiss on the reminder both land here. */
class AckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Acker.ackAll(context)
}
