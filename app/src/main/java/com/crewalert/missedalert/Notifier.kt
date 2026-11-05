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
    private const val CHANNEL = "realert_v1"
    private const val NOTIF_ID = 4242

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
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
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
            Intent(c, AckActivity::class.java).putExtra("pkg", items.last().pkg),
            flags
        )

        val last = items.last()
        val inbox = Notification.InboxStyle()
        items.takeLast(5).forEach { inbox.addLine("${label(it.pkg)}: ${it.title} ${it.text}".trim()) }

        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(
                if (items.size == 1) "Unacknowledged: ${label(last.pkg)}"
                else "${items.size} unacknowledged alerts"
            )
            .setContentText("${last.title} ${last.text}".trim())
            .setStyle(inbox)
            .setSubText("Reminder #$repeatNo")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)          // sound + vibrate on EVERY repeat
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(open)           // tap = acknowledge + open source app
            .setDeleteIntent(ack)             // swipe away = acknowledge
            .addAction(Notification.Action.Builder(0, "ACKNOWLEDGE", ack).build())
            .build()
        nm.notify(NOTIF_ID, n)
    }
}
