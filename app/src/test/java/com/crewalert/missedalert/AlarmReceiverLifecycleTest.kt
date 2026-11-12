package com.crewalert.missedalert

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlarmReceiverLifecycleTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun receiverAfterEmptyQueueCancelsOldAlarmWithoutCrewIntervention() {
        AlertStore.add(context, Item("dispatch", "com.test", "", "", 1000L))
        assertTrue(Scheduler.scheduleNext(context))
        AlertStore.clear(context)
        ReAlertReceiver().onReceive(context, Intent(context, ReAlertReceiver::class.java))
        assertEquals(0L, AlertStore.nextAt(context))
    }

    @Test fun receiverWithPendingQueueRearmsAfterReminderAttempt() {
        AlertStore.add(context, Item("dispatch", "com.test", "", "", 1000L))
        ReAlertReceiver().onReceive(context, Intent(context, ReAlertReceiver::class.java))
        assertEquals(1, AlertStore.count(context))
        assertTrue("pending reminder must remain scheduled", AlertStore.nextAt(context) > 0L)
    }

    @Test fun duplicateCallbackAfterRearmDoesNotAdvanceRepeatCount() {
        AlertStore.add(context, Item("dispatch", "com.test", "", "", 1000L))
        assertTrue(Scheduler.scheduleNext(context))
        val due = AlertStore.nextAt(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .edit().putLong("nextAt", due - 1L).commit()
        ReAlertReceiver().onReceive(context, Intent(context, ReAlertReceiver::class.java))
        val repeats = AlertStore.repeats(context)
        val next = AlertStore.nextAt(context)
        assertTrue(next > SystemClock.elapsedRealtime())
        ReAlertReceiver().onReceive(context, Intent(context, ReAlertReceiver::class.java))
        assertEquals(repeats, AlertStore.repeats(context))
        assertEquals(next, AlertStore.nextAt(context))
    }
}
