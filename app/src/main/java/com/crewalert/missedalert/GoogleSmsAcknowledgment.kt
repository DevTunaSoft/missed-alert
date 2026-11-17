package com.crewalert.missedalert

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import java.security.MessageDigest

object GoogleSmsAcknowledgment {
    const val PACKAGE = "com.google.android.apps.messaging"
    data class Evidence(val timestamp: Long, val fingerprint: String, val bodyFingerprint: String = "")

    fun evidence(notification: Notification): Evidence? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        @Suppress("DEPRECATION")
        val bundles = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val message = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
            .singleOrNull() ?: return null
        val sender = message.senderPerson?.name?.toString().orEmpty()
        val body = message.text?.toString().orEmpty()
        if (message.timestamp <= 0 || sender.isBlank() || body.isBlank()) return null
        return Evidence(message.timestamp, fingerprint(sender, body), bodyFingerprint(body))
    }

    private fun fingerprint(sender: String, body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${sender.length}:$sender$body".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun bodyFingerprint(body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun decision(context: Context, evidence: Evidence?): String {
        if (evidence == null) return "missing_message_identity"
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "sms_permission_unavailable"
        }
        return try {
            // Some SMS apps report slightly different notification and provider timestamps.
            // Bound the query to two seconds; NEVER acknowledge on time or body alone
            // when the sender identity can be verified.
            val lower = (evidence.timestamp - 2000L).coerceAtLeast(0L)
            val upper = evidence.timestamp + 2000L
            var exactCount = 0
            var exactRead = false
            var aliasCount = 0
            var aliasRead = false
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.READ, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf(lower.toString(), upper.toString()),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0) ?: continue
                    val body = cursor.getString(1) ?: continue
                    val isRead = cursor.getInt(2) == 1
                    if (fingerprint(address, body) == evidence.fingerprint) {
                        exactCount++
                        exactRead = isRead
                    }
                    // Alias fallback must use the EXACT timestamp and one candidate,
                    // never silently choose among multiple same-body notifications.
                    if (evidence.bodyFingerprint.isNotEmpty() &&
                        cursor.getLong(3) == evidence.timestamp &&
                        bodyFingerprint(body) == evidence.bodyFingerprint) {
                        aliasCount++
                        aliasRead = isRead
                    }
                    if (exactCount > 1 && aliasCount > 1) break
                }
            } ?: return "provider_unavailable"
            when {
                exactCount == 1 -> if (exactRead) "inbox_read" else "inbox_unread"
                exactCount > 1 -> "ambiguous_inbox_match"
                aliasCount == 1 -> if (aliasRead) "inbox_read" else "inbox_unread"
                aliasCount > 1 -> "ambiguous_inbox_match"
                else -> "no_exact_inbox_match"
            }
        } catch (e: RuntimeException) {
            EventLog.write(context, "sms_provider_query_failed", "error" to e.javaClass.simpleName)
            "provider_query_failed"
        }
    }

    // Call while holding AlertStore's lock. The acknowledgment path checks the generation again.
    fun reconcile(context: Context, item: Item) {
        if (item.pkg != PACKAGE) return
        val result = decision(context, item.smsEvidence)
        EventLog.write(context, "sms_ack_evidence", "decision" to result, "generation" to item.postTime)
        if (result == "inbox_read") {
            Acker.ackOne(context, item.key, "google_sms_inbox_read", item.postTime)
        }
    }
}
