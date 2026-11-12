package com.crewalert.missedalert

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Channel initialization must be safe to repeat without changing user-controlled settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ReminderChannelRecoveryTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private val channelId = "realert_v2"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(channelId)
    }

    @Test fun missingChannelCanBeRecreatedWithoutQueueChanges() {
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
        assertNull(manager.getNotificationChannel(channelId))
        Notifier.ensureChannel(context)
        val channel = manager.getNotificationChannel(channelId)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
        assertEquals(0, AlertStore.count(context))
    }

    @Test fun existingDisabledChannelIsNeverResetByRecovery() {
        Notifier.ensureChannel(context)
        val channel = manager.getNotificationChannel(channelId)!!
        channel.importance = NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)
        Notifier.ensureChannel(context)
        assertEquals(NotificationManager.IMPORTANCE_NONE,
            manager.getNotificationChannel(channelId)!!.importance)
    }

    @Test fun repeatedInitializationDoesNotCreateAdditionalChannels() {
        Notifier.ensureChannel(context)
        val before = manager.notificationChannels.count { it.id == channelId }
        repeat(20) { Notifier.ensureChannel(context) }
        assertEquals(1, before)
        assertEquals(before, manager.notificationChannels.count { it.id == channelId })
    }
}
