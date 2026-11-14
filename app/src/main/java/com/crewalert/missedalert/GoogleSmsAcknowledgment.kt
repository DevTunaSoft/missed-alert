package com.crewalert.missedalert

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import java.security.MessageDigest

object GoogleSmsAcknowledgment {
    const val PACKAGE = "com.google.android.apps.messaging"
    data class Evidence(val timestamp: Long, val fingerprint: String)

    fun evidence(notification: Notification): Evidence? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        @Suppress("DEPRECATION")
        val bundles = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val message = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
            .singleOrNull() ?: return null
        val sender = message.senderPerson?.name?.toString().orEmpty()
        val body = message.text?.toString().orEmpty()
        if (message.timestamp <= 0 || sender.isBlank() || body.isBlank()) return null
        return Evidence(message.timestamp, fingerprint(sender, body))
    }

    private fun fingerprint(sender: String, body: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${sender.length}:$sender$body".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun decision(context: Context, evidence: Evidence?): String {
        if (evidence == null) return "missing_message_identity"
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "sms_permission_unavailable"
        }
        return try {
            val matches = mutableListOf<Boolean>()
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.READ),
                "${Telephony.Sms.DATE} = ?",
                arrayOf(evidence.timestamp.toString()),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0) ?: continue
                    val body = cursor.getString(1) ?: continue
                    if (fingerprint(address, body) != evidence.fingerprint) continue
                    matches.add(cursor.getString(2) == "1")
                    if (matches.size > 1) break
                }
            } ?: return "provider_unavailable"
            when {
                matches.isEmpty() -> "no_exact_inbox_match"
                matches.size > 1 -> "ambiguous_inbox_match"
                matches.single() -> "inbox_read"
                else -> "inbox_unread"
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
