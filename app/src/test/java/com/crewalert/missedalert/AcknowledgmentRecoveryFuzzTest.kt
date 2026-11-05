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
class AcknowledgmentRecoveryFuzzTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun restoreCorruptMarkersWithoutRearmingRecentAcknowledgments() {
        repeat(800) { n ->
            val item = Item("key_$n", "com.test", "", "", 1000L + n)
            AlertStore.markAcked(context, listOf(item))
        }
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        assertEquals(prefs.getString("acked", null), prefs.getString("acked_backup", null))
        prefs.edit().putString("acked", "not-an-array").commit()
        repeat(800) { n ->
            assertTrue("missing ack $n after recovery", AlertStore.isAcked(context, "key_$n", 1000L + n))
        }
        assertEquals("not-an-array", prefs.getString("acked_corrupt_snapshot", null))
    }
    @Test fun missingBothCopiesNeverSilentlyWipesAcknowledgmentHistory() {
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        prefs.edit().putString("acked", "{broken")
            .putString("acked_backup", "{also-broken").commit()
        try {
            AlertStore.isAcked(context, "key", 1000L)
            fail("unrecoverable corruption must be reported")
        } catch (_: IllegalStateException) {}
        assertEquals("{broken", prefs.getString("acked", null))
    }
    @Test fun bulkAcknowledgmentOfLargeStormKeepsOlderMarkers() {
        repeat(900) { n ->
            AlertStore.add(context, Item("storm_$n", "com.test", "", "", 1000L + n))
        }
        assertEquals(900, AlertStore.acknowledgeAll(context))
        assertEquals(0, AlertStore.count(context))
        for (n in 0 until 900) {
            assertTrue("lost ack $n", AlertStore.isAcked(context, "storm_$n", 1000L + n))
        }
    }
}
