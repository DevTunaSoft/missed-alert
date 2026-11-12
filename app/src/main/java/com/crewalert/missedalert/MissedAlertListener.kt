package com.crewalert.missedalert

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Runs with no UI. The system binds this service once the device has granted notification access,
 * and rebinds it after reboot / process death.
 *
 * Rule: a notification from a watched app (or a missed-call notification) is "unacknowledged" until it
 * remains pending when the source app removes or updates its notification. Only an explicit crew
 * acknowledgment path removes it from the pending queue.
 */
class MissedAlertListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: MissedAlertListener? = null


    }

    override fun onListenerConnected() {
        instance = this
        Notifier.ensureChannel(this)
        RecoveryJobService.ensureScheduled(this)
        // Recreate a missing reminder alarm after system rebind or process recreation.
        try { Scheduler.ensureScheduled(this) } catch (e: Exception) {
            EventLog.write(this, "listener_alarm_recover_failed", "error" to e.javaClass.simpleName)
        }
        val active = try { activeNotifications } catch (e: Exception) { null }
        EventLog.write(this, "listener_connected", "active" to active?.size, "pending" to AlertStore.count(this))
        // Pick up anything already sitting in the shade (e.g. after the service was restarted).
        active?.forEach { track(it, "sync") }
    }

    override fun onListenerDisconnected() {
        instance = null
        EventLog.write(this, "listener_disconnected")
        // Ask the system to bind us again right away.
        try {
            requestRebind(ComponentName(this, MissedAlertListener::class.java))
        } catch (e: Exception) {
            EventLog.write(this, "listener_rebind_failed", "error" to e.javaClass.simpleName)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = track(sbn, "posted")

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        synchronized(AlertStore) {
        if (sbn.packageName == packageName) return
        // Android distinguishes a user swipe (CANCEL) from source-app removal
        // (APP_CANCEL); only explicit user dismissals/clicks acknowledge the alert.
        val userAction = NotificationRemovalPolicy.isUserDismissal(reason)
        if (!userAction) {
            if (AlertStore.all(this).any { it.key == sbn.key }) {
                EventLog.write(this, "source_removed_retained", "pkg" to sbn.packageName,
                    "reason" to reason)
            }
            return
        }
        // A late removal for an older notification generation must not clear its replacement.
        if (AlertStore.acknowledgeIfMatches(this, sbn.key, sbn.postTime)) {
            val left = AlertStore.count(this)
            EventLog.write(this, "alert_removed", "pkg" to sbn.packageName,
                "reason" to reason, "pending" to left)
            if (left == 0) {
                Scheduler.cancel(this)
                Notifier.cancel(this)
                AlertStore.resetRepeats(this)
            }
        }
        }
    }

    private fun track(sbn: StatusBarNotification, source: String) {
        synchronized(AlertStore) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        val channel = n.channelId.orEmpty()
        val watched = pkg in Config.watchedPackages(this)
        val dialer = pkg in Config.CALL_PACKAGES || n.category == Notification.CATEGORY_CALL

        // Never track persistent/foreground-service style notifications or group summary rows.
        val skipReason = when {
            n.flags and Notification.FLAG_ONGOING_EVENT != 0 -> "ongoing"
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "group_summary"
            else -> null
        }
        if (skipReason != null) {
            if (watched || dialer) {
                EventLog.write(this, "skipped", "pkg" to pkg, "channel" to channel, "reason" to skipReason)
            }
            return
        }

        AlertStore.recordSeen(this, "$pkg  [$channel]")
        val missedCall = !watched && isMissedCall(n, pkg)
        if (!watched && !missedCall) return

        if (AlertStore.isAcked(this, sbn.key, sbn.postTime)) {
            EventLog.write(this, "skipped", "pkg" to pkg, "channel" to channel, "reason" to "already_acked")
            return
        }

        val extras = n.extras
        val existing = AlertStore.all(this)
        val isNewAlert = existing.none { it.key == sbn.key && it.postTime == sbn.postTime }
        // Reposting or updating a source notification must not keep pushing the
        // first reminder 60 seconds into the future. Preserve the earliest alarm.
        AlertStore.add(
            this,
            Item(
                key = sbn.key,
                pkg = pkg,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                postTime = sbn.postTime,
            )
        )
        // Content (title/text) is deliberately NOT logged.
        EventLog.write(
            this, "alert_tracked",
            "source" to source, "pkg" to pkg, "channel" to channel,
            "kind" to (if (missedCall) "missed_call" else "watched_app"),
            "new" to isNewAlert, "pending" to AlertStore.count(this)
        )
        if (existing.isEmpty()) {
            AlertStore.resetRepeats(this)
            Scheduler.scheduleNext(this)
        } else {
            // Preserve the original due time even when more notifications arrive.
            Scheduler.ensureScheduled(this)
        }
        }
    }

    /** True for a missed-call notification from a dialer / telecom; false for incoming or ongoing calls. */
    private fun isMissedCall(n: Notification, pkg: String): Boolean {
        val fromDialer = pkg in Config.CALL_PACKAGES || n.category == Notification.CATEGORY_CALL
        if (!fromDialer) return false
        if (n.fullScreenIntent != null) return false // incoming-call screen
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return n.channelId.orEmpty().contains("missed", ignoreCase = true) ||
            title.contains("missed", ignoreCase = true) ||
            text.contains("missed", ignoreCase = true)
    }
}
