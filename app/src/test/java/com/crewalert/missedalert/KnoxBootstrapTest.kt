package com.crewalert.missedalert

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class KnoxBootstrapTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun launcherEntryIsInvisibleBootstrapNotSetupScreen() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentActivities(launcher, 0)
        assertTrue(resolved.any { it.activityInfo.name.endsWith(".BootstrapActivity") })
        assertFalse(resolved.any { it.activityInfo.name.endsWith(".StatusActivity") })
    }

    @Test fun firstRunInitializesWatchdogWithoutButtonPress() {
        val activity = Robolectric.buildActivity(BootstrapActivity::class.java).create().get()
        assertTrue(activity.isFinishing)
    }

    @Test fun existingAlertsArePreservedAcrossKnoxAutoRun() {
        AlertStore.add(context, Item("dispatch", "com.test.dispatch", "", "", 1000L))
        val activity = Robolectric.buildActivity(BootstrapActivity::class.java).create().get()
        assertTrue(activity.isFinishing)
        assertEquals(1, AlertStore.count(context))
        assertTrue("initial startup must schedule pending reminders", AlertStore.nextAt(context) > 0L)
    }
}
