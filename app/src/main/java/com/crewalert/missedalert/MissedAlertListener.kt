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
 * is removed (opened, swiped, cleared, or cancelled by its app) - or the reminder is acknowledged.
 */
class MissedAlertListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: MissedAlertListener? = null

        /** Remove tracked alerts whose source notification no longer exists. */
        fun pruneAgainstActive(c: Context) {
            val svc = instance ?: return
            val activeKeys = try {
                svc.activeNotifications?.map { it.key }?.toSet()
            } catch (e: Exception) {
                null
            } ?: return
            var pruned = 0
            AlertStore.all(c).forEach {
                if (it.key != "test" && it.key !in activeKeys) {
                    AlertStore.remove(c, it.key)
                    pruned++
                }
            }
            if (pruned > 0) EventLog.write(c, "pruned", "count" to pruned, "pending" to AlertStore.count(c))
        }
    }

    override fun onListenerConnected() {
        instance = this
        Notifier.ensureChannel(this)
        val active = try { activeNotifications } catch (e: Exception) { null }
        EventLog.write(this, "listener_connected", "active" to active?.size, "pending" to AlertStore.count(this))
        // Pick up anything already sitting in the shade (e.g. after the service was restarted).
        active?.forEach { track(it, "sync") }
    }

    override fun onListenerDisconnected() {
        instance = null
        EventLog.write(this, "listener_disconnected")
        // Ask the system to bind us again right away.
        requestRebind(ComponentName(this, MissedAlertListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = track(sbn, "posted")

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName == packageName) return
        if (AlertStore.remove(this, sbn.key)) {
            val left = AlertStore.count(this)
            EventLog.write(this, "alert_removed", "pkg" to sbn.packageName, "reason" to reason, "pending" to left)
            if (left == 0) {
                Scheduler.cancel(this)
                Notifier.cancel(this)
            }
        }
    }

    private fun track(sbn: StatusBarNotification, source: String) {
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
            "pending" to AlertStore.count(this)
        )
        if (!Scheduler.isPending(this)) Scheduler.scheduleNext(this)
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
