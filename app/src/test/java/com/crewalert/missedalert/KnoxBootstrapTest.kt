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
        // Inspect the actual manifest; Robolectric package-manager query results
        // vary with simulated OS and are not evidence of manifest configuration.
        val manifest = listOf(
            java.io.File("src/main/AndroidManifest.xml"),
            java.io.File("app/src/main/AndroidManifest.xml")
        ).first { it.isFile }
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(manifest)
        val activities = document.getElementsByTagName("activity")
        fun launches(name: String): Boolean {
            for (i in 0 until activities.length) {
                val activity = activities.item(i) as org.w3c.dom.Element
                if (activity.getAttribute("android:name") != name) continue
                val filters = activity.getElementsByTagName("intent-filter")
                for (j in 0 until filters.length) {
                    val f = filters.item(j) as org.w3c.dom.Element
                    val actions = f.getElementsByTagName("action")
                    val categories = f.getElementsByTagName("category")
                    val main = (0 until actions.length).any {
                        (actions.item(it) as org.w3c.dom.Element).getAttribute("android:name") ==
                            "android.intent.action.MAIN"
                    }
                    val launcher = (0 until categories.length).any {
                        (categories.item(it) as org.w3c.dom.Element).getAttribute("android:name") ==
                            "android.intent.category.LAUNCHER"
                    }
                    if (main && launcher) return true
                }
            }
            return false
        }
        assertTrue(launches(".BootstrapActivity"))
        assertFalse(launches(".StatusActivity"))
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
    @Test fun repeatedKnoxLaunchesPreservePendingAlertWithoutButtonPress() {
        AlertStore.add(context, Item("dispatch", "com.test.dispatch", "", "", 1000L))
        repeat(5) {
            val activity = Robolectric.buildActivity(BootstrapActivity::class.java).create().get()
            assertTrue(activity.isFinishing)
            assertEquals(1, AlertStore.count(context))
            assertTrue(AlertStore.nextAt(context) > 0L)
        }
    }

    @Test fun emptyInstallAutoLaunchNeedsNoSetupClick() {
        val activity = Robolectric.buildActivity(BootstrapActivity::class.java).create().get()
        assertTrue(activity.isFinishing)
        assertEquals(0, AlertStore.count(context))
    }
}
