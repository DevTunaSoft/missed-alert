package com.crewalert.missedalert
import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UnattendedBootTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun bootRearmsExistingAlertWithoutUserActivity() {
        AlertStore.add(context, Item("boot", "com.test", "", "", 1000L))
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, AlertStore.count(context))
        assertTrue(AlertStore.nextAt(context) > 0L)
    }
    @Test fun packageUpdateRearmsExistingAlert() {
        AlertStore.add(context, Item("update", "com.test", "", "", 1000L))
        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(1, AlertStore.count(context))
        assertTrue(AlertStore.nextAt(context) > 0L)
    }
    @Test fun corruptedAlertStoreDoesNotCrashBootReceiver() {
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit()
            .putString("items", "{corrupt").commit()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals("{corrupt", context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .getString("items", null))
    }
    @Test fun corruptedAlertStoreDoesNotCrashExactAlarmPermissionCallback() {
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit()
            .putString("items", "{corrupt").commit()
        BootReceiver().onReceive(context,
            Intent("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))
        assertEquals("{corrupt", context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .getString("items", null))
    }
    @Test fun corruptedAlertStoreDoesNotCrashClockChangeCallback() {
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit()
            .putString("items", "{corrupt").commit()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals("{corrupt", context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .getString("items", null))
    }
    @Test fun emptyBootDoesNotCreateFakeReminder() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0, AlertStore.count(context))
        assertEquals(0L, AlertStore.nextAt(context))
    }
}
