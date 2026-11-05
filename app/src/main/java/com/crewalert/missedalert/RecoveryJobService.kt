package com.crewalert.missedalert

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService

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
            EventLog.write(this, "watchdog_fired", "pending" to AlertStore.count(this),
                "listener_bound" to (MissedAlertListener.instance != null))
            if (MissedAlertListener.instance == null) {
                try {
                    NotificationListenerService.requestRebind(
                        ComponentName(this, MissedAlertListener::class.java))
                } catch (e: Exception) {
                    EventLog.write(this, "watchdog_rebind_failed", "error" to e.javaClass.simpleName)
                }
            }
            Scheduler.ensureScheduled(this)
        } catch (e: Exception) {
            EventLog.write(this, "watchdog_failed", "error" to e.javaClass.simpleName)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
