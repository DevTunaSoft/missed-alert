package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

/**
 * After reboot / app update: reset state (old notifications are gone) and make sure the listener binds.
 * On a clock or timezone change: re-arm the alarm, because the alarm-clock alarm is wall-clock based.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            EventLog.write(context, "clock_changed", "action" to action.substringAfterLast('.'),
                "pending" to AlertStore.count(context))
            if (AlertStore.count(context) > 0) Scheduler.scheduleNext(context)
            return
        }
        EventLog.write(context, "boot_or_update", "action" to action.substringAfterLast('.'))
        AlertStore.clear(context)
        Scheduler.cancel(context)
        Notifier.ensureChannel(context)
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, MissedAlertListener::class.java)
            )
        } catch (e: Exception) {
            EventLog.write(context, "rebind_failed", "error" to e.javaClass.simpleName)
        }
    }
}
