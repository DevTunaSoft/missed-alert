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
 * Rule: a notification from a watched app is "unacknowledged" until it is removed
 * (opened, swiped, cleared, or cancelled by its app) — or the reminder is acknowledged.
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
            AlertStore.all(c).forEach {
                if (it.key != "test" && it.key !in activeKeys) AlertStore.remove(c, it.key)
            }
        }
    }

    override fun onListenerConnected() {
        instance = this
        Notifier.ensureChannel(this)
        // Pick up anything already sitting in the shade (e.g. after the service was restarted).
        try {
            activeNotifications?.forEach { track(it) }
        } catch (e: Exception) {
            // ignore; next post will be tracked
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        // Ask the system to bind us again right away.
        requestRebind(ComponentName(this, MissedAlertListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = track(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName == packageName) return
        if (AlertStore.remove(this, sbn.key) && AlertStore.count(this) == 0) {
            Scheduler.cancel(this)
            Notifier.cancel(this)
        }
    }

    private fun track(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return

        // Never track persistent/foreground-service style notifications or group summary rows.
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        AlertStore.recordSeen(this, "${sbn.packageName}  [${n.channelId.orEmpty()}]")
        val watched = sbn.packageName in Config.watchedPackages(this)
        if (!watched && !isMissedCall(n, sbn.packageName)) return
        if (AlertStore.isAcked(this, sbn.key, sbn.postTime)) return

        val extras = n.extras
        AlertStore.add(
            this,
            Item(
                key = sbn.key,
                pkg = sbn.packageName,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                postTime = sbn.postTime,
            )
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
