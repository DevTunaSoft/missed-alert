package com.crewalert.missedalert

import android.app.Activity
import android.content.ComponentName
import android.app.NotificationManager
import android.os.Bundle
import android.service.notification.NotificationListenerService

/** Knox auto-run initialization: no user input, dialogs, or visible setup screen. */
class BootstrapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            EventLog.write(this, "knox_auto_run_bootstrap")
            Notifier.ensureChannel(this)
            RecoveryJobService.ensureScheduled(this)
            try { Scheduler.ensureScheduled(this) } catch (e: Exception) {
                EventLog.write(this, "bootstrap_schedule_failed", "error" to e.javaClass.simpleName)
            }
            val listenerComponent = ComponentName(this, MissedAlertListener::class.java)
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.isNotificationListenerAccessGranted(listenerComponent)) {
                try { NotificationListenerService.requestRebind(listenerComponent) } catch (e: Exception) {
                    EventLog.write(this, "bootstrap_rebind_failed", "error" to e.javaClass.simpleName)
                }
            } else {
                EventLog.write(this, "bootstrap_listener_permission_missing")
            }
        } catch (e: Exception) {
            EventLog.write(this, "bootstrap_failed", "error" to e.javaClass.simpleName)
        } finally {
            finish()
        }
    }
}
