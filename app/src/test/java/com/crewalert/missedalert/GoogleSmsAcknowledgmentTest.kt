package com.crewalert.missedalert

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GoogleSmsAcknowledgmentTest {
    private lateinit var context: Application
    private var read = "0"
    private var rows = 1

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.READ_SMS)
        ShadowContentResolver.registerProviderInternal("sms", object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
                assertEquals("date = ?", selection)
                return MatrixCursor(arrayOf("address", "body", "read")).apply {
                    if (selectionArgs?.single() == "1000") {
                        repeat(rows) { addRow(arrayOf("+15551234567", "Dispatch", read)) }
                    }
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?,
                selectionArgs: Array<out String>?) = 0
        })
    }

    @After fun cleanup() {
        Scheduler.cancel(context)
    }

    private fun item(key: String = "sms", generation: Long = 1100L, timestamp: Long = 1000L): Item {
        val sender = android.app.Person.Builder().setName("+15551234567").build()
        val style = android.app.Notification.MessagingStyle(
            android.app.Person.Builder().setName("Crew").build()
        ).addMessage("Dispatch", timestamp, sender)
        val notification = android.app.Notification.Builder(context, "sms").setStyle(style).build()
        return Item(key, GoogleSmsAcknowledgment.PACKAGE, "", "", generation,
            GoogleSmsAcknowledgment.evidence(notification))
    }

    private fun reconcile() = synchronized(AlertStore) {
        AlertStore.all(context).forEach { GoogleSmsAcknowledgment.reconcile(context, it) }
    }

    @Test fun readSmsAfterReloadClearsOnlyItsGeneration() {
        val first = item()
        AlertStore.add(context, first)
        AlertStore.add(context, item("other", 2200L, 2000L))
        Scheduler.scheduleNext(context)
        val deadline = AlertStore.nextAt(context)
        read = "1"
        reconcile()
        assertEquals(listOf("other"), AlertStore.all(context).map { it.key })
        assertEquals(deadline, AlertStore.nextAt(context))
        assertEquals(true, AlertStore.isAcked(context, first.key, first.postTime))
    }

    @Test fun unreadAndAmbiguousSmsStayPending() {
        AlertStore.add(context, item())
        reconcile()
        assertEquals(1, AlertStore.count(context))
        read = "1"
        rows = 2
        reconcile()
        assertEquals(1, AlertStore.count(context))
    }

    @Test fun staleReadEvidenceCannotAcknowledgeReplacement() {
        val original = item()
        AlertStore.add(context, original)
        val replacement = item(generation = 2200L, timestamp = 2000L)
        AlertStore.add(context, replacement)
        read = "1"
        synchronized(AlertStore) { GoogleSmsAcknowledgment.reconcile(context, original) }
        reconcile()
        assertEquals(replacement, AlertStore.all(context).single())
    }

    @Test fun lastReadSmsCancelsReminder() {
        AlertStore.add(context, item())
        Scheduler.scheduleNext(context)
        read = "1"
        reconcile()
        assertEquals(0, AlertStore.count(context))
        assertEquals(0L, AlertStore.nextAt(context))
    }
}
