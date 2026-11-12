package com.crewalert.missedalert

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.os.SystemClock
import android.os.PowerManager
import android.app.AlarmManager

/** Coarse recovery only: Android can defer jobs and cannot restart a force-stopped application. */
class RecoveryJobService : JobService() {
    companion object {
        private const val JOB_ID = 7104
        fun ensureScheduled(c: Context) {
            try {
                val js = c.getSystemService(JobScheduler::class.java)
                if (js.allPendingJobs.any { it.id == JOB_ID }) return
                val job = JobInfo.Builder(JOB_ID, ComponentName(c, RecoveryJobService::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build()
                val result = js.schedule(job)
                EventLog.write(c, "watchdog_scheduled", "result" to result)
            } catch (e: Exception) {
                EventLog.write(c, "watchdog_schedule_failed", "error" to e.javaClass.simpleName)
            }
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val listenerComponent = ComponentName(this, MissedAlertListener::class.java)
            val listenerAccess = nm.isNotificationListenerAccessGranted(listenerComponent)
            val postGranted = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            EventLog.write(this, "watchdog_fired", "pending" to AlertStore.count(this),
                "listener_bound" to (MissedAlertListener.instance != null),
                "listener_access" to listenerAccess,
                "post_granted" to postGranted,
                "notifications_enabled" to nm.areNotificationsEnabled(),
                "channel_importance" to nm.getNotificationChannel("realert_v2")?.importance)
            if (MissedAlertListener.instance == null) {
                if (!listenerAccess) {
                    EventLog.write(this, "watchdog_listener_permission_missing")
                } else {
                    try {
                        NotificationListenerService.requestRebind(listenerComponent)
                    } catch (e: Exception) {
                        EventLog.write(this, "watchdog_rebind_failed", "error" to e.javaClass.simpleName)
                    }
                }
            }
            // Passive health check: no synthetic alerts or acknowledgment mutations.
            val pending = AlertStore.count(this)
            val dueAt = AlertStore.nextAt(this)
            val alarmHealthy = pending == 0 || Scheduler.isPending(this)
            val listenerState = when {
                !listenerAccess -> "permission_missing"
                MissedAlertListener.instance == null -> "disconnected"
                else -> "connected"
            }
            val listenerHealthy = listenerAccess && MissedAlertListener.instance != null
            val channel = nm.getNotificationChannel("realert_v2")
            val reminderHealthy = postGranted && nm.areNotificationsEnabled() &&
                channel != null && channel.importance >= NotificationManager.IMPORTANCE_DEFAULT &&
                channel.sound != null
            val batteryExempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            val exactAlarms = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            EventLog.write(this, "self_test",
                "listener" to (if (listenerHealthy) "pass" else "fail"),
                "listener_state" to listenerState,
                "reminder_channel" to (if (reminderHealthy) "pass" else "fail"),
                "alarm" to (if (alarmHealthy) "pass" else "fail"),
                "battery_exempt" to batteryExempt,
                "exact_alarm_allowed" to exactAlarms,
                "interruption_filter" to nm.currentInterruptionFilter,
                "channel_sound" to (channel?.sound != null),
                "pending" to pending,
                "overdue_ms" to (if (pending > 0 && dueAt > 0L)
                    (SystemClock.elapsedRealtime() - dueAt).coerceAtLeast(0L) else 0L))
            // Report deployment blockers explicitly; a background service cannot
            // silently grant Android's notification listener or posting permissions.
            if (!listenerAccess || !postGranted || !nm.areNotificationsEnabled()) {
                EventLog.write(this, "knox_permission_blocker",
                    "listener_access" to listenerAccess,
                    "post_granted" to postGranted,
                    "notifications_enabled" to nm.areNotificationsEnabled(),
                    "action" to "grant_in_device_policy_or_settings")
            }
            if (channel == null || channel.importance < NotificationManager.IMPORTANCE_DEFAULT ||
                channel.sound == null) {
                EventLog.write(this, "reminder_channel_blocker",
                    "channel_exists" to (channel != null),
                    "importance" to channel?.importance,
                    "sound_present" to (channel?.sound != null),
                    "action" to (if (channel == null) "initialize_notification_channel"
                        else "inspect_notification_channel_settings"))
                if (channel == null) {
                    try {
                        Notifier.ensureChannel(this)
                        val created = nm.getNotificationChannel("realert_v2")
                        EventLog.write(this, "reminder_channel_recovery_result",
                            "channel_exists" to (created != null),
                            "importance" to created?.importance,
                            "sound_present" to (created?.sound != null))
                    } catch (e: Exception) {
                        EventLog.write(this, "reminder_channel_recovery_failed",
                            "error" to e.javaClass.simpleName)
                    }
                }
            }
        } catch (e: Exception) {
            EventLog.write(this, "watchdog_failed", "error" to e.javaClass.simpleName)
        } finally {
            // A failed permission/channel diagnostic must not skip alarm recovery.
            try {
                val pending = AlertStore.count(this)
                val wasHealthy = pending == 0 || Scheduler.isPending(this)
                if (!wasHealthy) EventLog.write(this, "self_test_alarm_recovery")
                Scheduler.ensureScheduled(this)
                if (!wasHealthy) {
                    EventLog.write(this, "self_test_recovery_result",
                        "alarm" to (if (Scheduler.isPending(this)) "pass" else "fail"),
                        "pending" to AlertStore.count(this),
                        "next_at_elapsed_ms" to AlertStore.nextAt(this))
                }
            } catch (e: Exception) {
                EventLog.write(this, "watchdog_alarm_recovery_failed",
                    "error" to e.javaClass.simpleName)
            }
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
