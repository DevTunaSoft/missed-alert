package com.crewalert.missedalert

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ReminderIntentGenerationTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun reminderActionAcknowledgesOnlyItsOwnAlert() {
        AlertStore.add(context, Item("sms", "com.google.android.apps.messaging", "", "", 1100L))
        AlertStore.add(context, Item("mdt", "com.traumasoft.mdt", "", "", 2200L))
        Scheduler.scheduleNext(context)
        val due = AlertStore.nextAt(context)
        AckReceiver().onReceive(context, android.content.Intent()
            .putExtra("key", "sms").putExtra("post_time", 1100L))
        assertEquals(listOf("mdt"), AlertStore.all(context).map { it.key })
        assertEquals(due, AlertStore.nextAt(context))
        assertFalse(AlertStore.isAcked(context, "mdt", 2200L))
    }

    @Test fun unversionedAndStaleActionsCannotClearAnyPendingAlert() {
        AlertStore.add(context, Item("sms", "com.google.android.apps.messaging", "", "", 2200L))
        AckReceiver().onReceive(context, android.content.Intent())
        AckReceiver().onReceive(context, android.content.Intent()
            .putExtra("key", "sms").putExtra("post_time", 1100L))
        assertEquals(1, AlertStore.count(context))
        assertFalse(AlertStore.isAcked(context, "sms", 2200L))
    }

    @Test fun staleReminderTapCannotDismissNewGeneration() {
        AlertStore.add(context, Item("key", "com.test", "", "", 1111L))
        AlertStore.add(context, Item("key", "com.test", "", "", 2222L))
        Acker.ackOne(context, "key", "old_reminder", 1111L)
        assertEquals(2222L, AlertStore.all(context).single().postTime)
        assertFalse(AlertStore.isAcked(context, "key", 2222L))
        Acker.ackOne(context, "key", "current_reminder", 2222L)
        assertEquals(0, AlertStore.count(context))
        assertTrue(AlertStore.isAcked(context, "key", 2222L))
    }

    @Test fun unversionedReminderCannotClearAnyAlert() {
        AlertStore.add(context, Item("key", "com.test", "", "", 2222L))
        Acker.ackOne(context, "key")
        assertEquals(1, AlertStore.count(context))
    }

    @Test fun repeatedStaleReminderTapsDoNotRemoveFreshAlert() {
        repeat(150) { index ->
            val generation = 3000L + index
            AlertStore.add(context, Item("same", "com.test", "", "", generation))
            Acker.ackOne(context, "same", "stale", generation - 1)
            assertEquals(generation, AlertStore.all(context).first { it.key == "same" }.postTime)
        }
    }
}
