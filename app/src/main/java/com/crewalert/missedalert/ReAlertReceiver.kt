package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires every interval while anything is unacknowledged. */
class ReAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Drop alerts whose original notification is already gone (read/cleared in the source app).
        MissedAlertListener.pruneAgainstActive(context)

        val items = AlertStore.all(context)
        if (items.isEmpty()) {
            Scheduler.cancel(context)
            Notifier.cancel(context)
            return
        }

        val n = AlertStore.incRepeats(context)
        val max = Config.maxRepeats(context)
        if (max > 0 && n > max) {
            Acker.ackAll(context) // gave up after the configured number of reminders
            return
        }

        Notifier.show(context, items, n)
        Scheduler.scheduleNext(context)
    }
}
