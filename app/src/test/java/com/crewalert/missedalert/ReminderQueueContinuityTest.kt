package com.crewalert.missedalert

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Reposted and additional source alerts must not postpone an existing reminder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ReminderQueueContinuityTest {
    private lateinit var context: Context
    private lateinit var listener: MissedAlertListener

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
        listener = Robolectric.buildService(MissedAlertListener::class.java).create().get()
    }

    private fun post(id: Int, time: Long) {
        val pkg = "com.google.android.apps.messaging"
        val n = Notification.Builder(context, "messages")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New message")
            .build()
        listener.onNotificationPosted(StatusBarNotification(
            pkg, pkg, id, null, Process.myUid(), 0, 0, n, Process.myUserHandle(), time
        ))
    }

    @Test fun additionalMessagesDoNotPostponeFirstReminder() {
        post(1, 1000L)
        val due = AlertStore.nextAt(context)
        assertTrue(due > 0)
        post(2, 2000L)
        assertEquals(2, AlertStore.count(context))
        assertEquals("new notification must not reset reminder due time", due, AlertStore.nextAt(context))
    }

    @Test fun repostedMessageDoesNotPostponeFirstReminder() {
        post(1, 1000L)
        val due = AlertStore.nextAt(context)
        post(1, 1000L)
        assertEquals(1, AlertStore.count(context))
        assertEquals(due, AlertStore.nextAt(context))
    }
}
