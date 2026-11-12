package com.crewalert.missedalert

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Acknowledging one of several alerts must not defer the remaining reminders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PartialAcknowledgmentScheduleTest {
    private lateinit var context: Context

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun acknowledgingOnePreservesRemainingDeadlineAndRepeatCount() {
        AlertStore.add(context, Item("first", "com.google.android.apps.messaging", "", "", 1000L))
        AlertStore.add(context, Item("second", "com.traumasoft.mdt", "", "", 2000L))
        assertTrue(Scheduler.scheduleNext(context))
        AlertStore.incRepeats(context)
        val due = AlertStore.nextAt(context)
        val repeats = AlertStore.repeats(context)

        Acker.ackOne(context, "first", "test", 1000L)

        assertEquals(1, AlertStore.count(context))
        assertEquals(due, AlertStore.nextAt(context))
        assertEquals(repeats, AlertStore.repeats(context))
        assertTrue(Scheduler.isPending(context))
    }

    @Test fun acknowledgingFinalAlertCancelsAlarm() {
        AlertStore.add(context, Item("only", "com.traumasoft.mdt", "", "", 1000L))
        assertTrue(Scheduler.scheduleNext(context))
        Acker.ackOne(context, "only", "test", 1000L)
        assertEquals(0, AlertStore.count(context))
        assertEquals(0L, AlertStore.nextAt(context))
        assertEquals(0, AlertStore.repeats(context))
    }

    @Test fun staleAcknowledgmentCannotChangeDeadline() {
        AlertStore.add(context, Item("key", "com.traumasoft.mdt", "", "", 2000L))
        assertTrue(Scheduler.scheduleNext(context))
        val due = AlertStore.nextAt(context)
        Acker.ackOne(context, "key", "test", 1000L)
        assertEquals(1, AlertStore.count(context))
        assertEquals(due, AlertStore.nextAt(context))
    }
}
