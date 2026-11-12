package com.crewalert.missedalert

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.app.NotificationManager
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest

/**
 * After reboot / app update: preserve pending alerts and make sure the listener binds.
 * On a clock or timezone change: re-arm the alarm, because the alarm-clock alarm is wall-clock based.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        RecoveryJobService.ensureScheduled(context)
        // Diagnostic only: unattended mode must not open permission dialogs.
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            val allowed = nm.isNotificationListenerAccessGranted(
                ComponentName(context, MissedAlertListener::class.java))
            val post = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            EventLog.write(context, "unattended_preflight",
                "listener_access" to allowed, "post_permission" to post,
                "notifications_enabled" to nm.areNotificationsEnabled())
        } catch (e: Exception) {
            EventLog.write(context, "unattended_preflight_failed", "error" to e.javaClass.simpleName)
        }
        if (action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") {
            EventLog.write(context, "exact_permission_changed")
            try {
                if (AlertStore.count(context) > 0) Scheduler.scheduleNext(context)
            } catch (e: Exception) {
                EventLog.write(context, "exact_permission_recovery_failed",
                    "error" to e.javaClass.simpleName)
                RecoveryJobService.ensureScheduled(context)
            }
            return
        }
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            try {
                val pending = AlertStore.count(context)
                EventLog.write(context, "clock_changed", "action" to action.substringAfterLast('.'),
                    "pending" to pending)
                if (pending > 0) Scheduler.scheduleNext(context)
            } catch (e: Exception) {
                EventLog.write(context, "clock_change_recovery_failed",
                    "error" to e.javaClass.simpleName)
                RecoveryJobService.ensureScheduled(context)
            }
            return
        }
        EventLog.write(context, "boot_or_update", "action" to action.substringAfterLast('.'))
        Scheduler.cancel(context)
        try {
            if (AlertStore.count(context) > 0) {
                AlertStore.resetRepeats(context)
                Scheduler.scheduleNext(context)
            }
        } catch (e: Exception) {
            EventLog.write(context, "boot_alert_recovery_failed", "error" to e.javaClass.simpleName)
        }
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
