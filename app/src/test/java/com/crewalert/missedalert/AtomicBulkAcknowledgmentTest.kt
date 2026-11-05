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
class AtomicBulkAcknowledgmentTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun allAcknowledgmentsAndQueueRemovalPersistTogether() {
        repeat(50) { index ->
            AlertStore.add(context, Item("alert_$index", "com.test", "", "", 1000L + index))
        }
        val cleared = AlertStore.acknowledgeAll(context)
        assertEquals(50, cleared)
        assertEquals(0, AlertStore.count(context))
        repeat(50) { index ->
            assertTrue(AlertStore.isAcked(context, "alert_$index", 1000L + index))
        }
    }

    @Test fun markersAreLimitedAndNewerAcknowledgmentsRemain() {
        repeat(260) { index ->
            AlertStore.add(context, Item("k_$index", "com.test", "", "", 10000L + index))
        }
        assertEquals(260, AlertStore.acknowledgeAll(context))
        assertEquals(0, AlertStore.count(context))
        repeat(200) { index ->
            val n = index + 60
            assertTrue("missing recent marker $n", AlertStore.isAcked(context, "k_$n", 10000L + n))
        }
    }

    @Test fun corruptQueueCannotBeSilentlyCleared() {
        val bad = "{corrupted"
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().putString("items", bad).commit()
        try {
            AlertStore.acknowledgeAll(context)
            fail("must not clear unreadable alerts")
        } catch (_: IllegalStateException) {}
        assertEquals(bad, context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .getString("items", null))
    }
}
