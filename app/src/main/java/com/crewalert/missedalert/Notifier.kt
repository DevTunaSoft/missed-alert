package com.crewalert.missedalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings

object Notifier {
    private const val CHANNEL = "realert_v2"
    private const val NOTIF_ID_A = 4242
    private const val NOTIF_ID_B = 4243

    /**
     * High-importance channel that plays the device's DEFAULT notification tone
     * (Settings.System.DEFAULT_NOTIFICATION_URI is resolved at play time, so it follows
     * whatever the device's default notification sound is).
     */
    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Missed alert reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Repeats until a missed text or MDT alert is acknowledged"
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(ch)
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIF_ID_A)
            cancel(NOTIF_ID_B)
        }
    }

    @Suppress("DEPRECATION")
    fun show(c: Context, items: List<Item>, repeatNo: Int) {
        ensureChannel(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        val pm = c.packageManager
        EventLog.write(
            c, "reminder_shown", "n" to repeatNo, "items" to items.size,
            "notifications_enabled" to nm.areNotificationsEnabled(),
            "channel_importance" to nm.getNotificationChannel(CHANNEL)?.importance
        )
        fun label(pkg: String) = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val ack = PendingIntent.getBroadcast(c, 2, Intent(c, AckReceiver::class.java), flags)
        val open = PendingIntent.getActivity(
            c, 3,
            Intent(c, AckActivity::class.java)
                .putExtra("pkg", items.last().pkg)
                .putExtra("key", items.last().key)
                .putExtra("post_time", items.last().postTime),
            flags
        )

        val last = items.last()
        val inbox = Notification.InboxStyle()
        items.takeLast(5).forEach { inbox.addLine(label(it.pkg)) }

        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(
                if (items.size == 1) "Unacknowledged: ${label(last.pkg)}"
                else "${items.size} unacknowledged alerts"
            )
            .setContentText("Review the source app, then acknowledge this reminder")
            .setStyle(inbox)
            .setSubText("Reminder #$repeatNo")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(false)          // sound + vibrate on EVERY repeat
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(open)           // tap = acknowledge + open source app
            .setDeleteIntent(ack)             // swipe away = acknowledge
            .addAction(Notification.Action.Builder(0, "ACKNOWLEDGE ALL", ack).build())
            .build()
        val current = if (repeatNo % 2 == 0) NOTIF_ID_A else NOTIF_ID_B
        val previous = if (repeatNo % 2 == 0) NOTIF_ID_B else NOTIF_ID_A
        nm.cancel(previous)
        if (!nm.areNotificationsEnabled() ||
            (nm.getNotificationChannel(CHANNEL)?.importance ?: 0) < NotificationManager.IMPORTANCE_DEFAULT) {
            EventLog.write(c, "reminder_blocked_or_silent",
                "notifications_enabled" to nm.areNotificationsEnabled(),
                "channel_importance" to nm.getNotificationChannel(CHANNEL)?.importance)
        }
        nm.notify(current, n)
        EventLog.write(c, "reminder_posted", "id" to current, "n" to repeatNo)
    }
}
