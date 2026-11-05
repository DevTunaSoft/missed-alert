package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

/** After reboot / app update: reset state (old notifications are gone) and make sure the listener binds. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertStore.clear(context)
        Scheduler.cancel(context)
        Notifier.ensureChannel(context)
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, MissedAlertListener::class.java)
            )
        } catch (e: Exception) {
            // Listener access not granted yet; nothing to bind.
        }
    }
}
