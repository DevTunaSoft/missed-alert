package com.crewalert.missedalert

import android.content.Context
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Recovery must not restart a full repeat interval when a reminder is overdue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class OverdueAlarmRecoveryTest {
    private lateinit var context: Context

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun overdueReminderRearmsWithShortGraceAndPreservesQueue() {
        AlertStore.add(context, Item("dispatch", "com.traumasoft.mdt", "", "", 1000L))
        assertTrue(Scheduler.scheduleNext(context))
        val repeats = AlertStore.repeats(context)
        AlertStore.setNextAt(context, SystemClock.elapsedRealtime() - 5000L)

        Scheduler.ensureScheduled(context)

        val remaining = AlertStore.nextAt(context) - SystemClock.elapsedRealtime()
        assertTrue("overdue reminder should rearm promptly: $remaining", remaining in 1L..5000L)
        assertEquals(1, AlertStore.count(context))
        assertEquals(repeats, AlertStore.repeats(context))
        assertTrue(Scheduler.isPending(context))
    }

    @Test fun healthyReminderDeadlineIsNotChanged() {
        AlertStore.add(context, Item("dispatch", "com.traumasoft.mdt", "", "", 1000L))
        assertTrue(Scheduler.scheduleNext(context))
        val due = AlertStore.nextAt(context)

        Scheduler.ensureScheduled(context)

        assertEquals(due, AlertStore.nextAt(context))
    }

    @Test fun emptyQueueCannotCreateRecoveryAlarm() {
        AlertStore.setNextAt(context, SystemClock.elapsedRealtime() - 5000L)
        Scheduler.ensureScheduled(context)
        assertEquals(0, AlertStore.count(context))
        assertFalse(Scheduler.isPending(context))
    }
}
