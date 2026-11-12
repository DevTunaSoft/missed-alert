package com.crewalert.missedalert

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
class DiagnosticScreenIntegrationTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun findButton(root: View, text: String): Button? {
        if (root is Button && root.text.toString() == text) return root
        if (root is ViewGroup) {
            for (n in 0 until root.childCount) {
                findButton(root.getChildAt(n), text)?.let { return it }
            }
        }
        return null
    }

    private fun allText(root: View): String {
        val own = if (root is TextView) root.text.toString() else ""
        if (root !is ViewGroup) return own
        return own + (0 until root.childCount).joinToString(" ") { allText(root.getChildAt(it)) }
    }

    @Test fun dashboardDescribesReadinessAndDoesNotRequireStartButton() {
        val activity = Robolectric.buildActivity(StatusActivity::class.java).create().start().resume().get()
        val text = allText(activity.window.decorView)
        assertTrue(text.contains("MISSED ALERT"))
        assertTrue(text.contains("DEVICE READINESS"))
        assertTrue(text.contains("Notification access:"))
        assertTrue(text.contains("Listener connected:"))
        assertNotNull(findButton(activity.window.decorView, "Refresh device status"))
    }

    @Test fun diagnosticNotificationDoesNotModifyRealAlerts() {
        AlertStore.add(context, Item("dispatch", "com.traumasoft.mdt", "", "", 1234L))
        assertTrue(Scheduler.scheduleNext(context))
        val due = AlertStore.nextAt(context)
        val repeats = AlertStore.repeats(context)
        val activity = Robolectric.buildActivity(StatusActivity::class.java).create().start().resume().get()
        val test = findButton(activity.window.decorView, "IT: Test notification delivery (isolated)")
        assertNotNull(test)
        assertTrue(test!!.performClick())
        assertEquals(1, AlertStore.count(activity))
        assertEquals("dispatch", AlertStore.all(activity).single().key)
        assertEquals(repeats, AlertStore.repeats(activity))
        assertEquals(due, AlertStore.nextAt(activity))
    }

    @Test fun diagnosticNotificationDoesNotCreatePendingAlert() {
        val activity = Robolectric.buildActivity(StatusActivity::class.java).create().start().resume().get()
        val test = findButton(activity.window.decorView, "IT: Test notification delivery (isolated)")
        assertNotNull(test)
        assertTrue(test!!.performClick())
        assertEquals(0, AlertStore.count(activity))
        assertEquals(0, AlertStore.repeats(activity))
        assertEquals(0L, AlertStore.nextAt(activity))
    }
}
