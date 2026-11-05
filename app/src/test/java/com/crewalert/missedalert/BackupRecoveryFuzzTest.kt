package com.crewalert.missedalert

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BackupRecoveryFuzzTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun restoresLatestCommittedQueueWithoutCrewAction() {
        AlertStore.add(context, Item("dispatch", "com.test", "", "", 1000L))
        AlertStore.add(context, Item("sms", "com.test", "", "", 2000L))
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val saved = prefs.getString("items", null)
        assertEquals(saved, prefs.getString("items_backup", null))
        prefs.edit().putString("items", "{broken-json").commit()
        assertEquals(2, AlertStore.count(context))
        assertEquals(saved, prefs.getString("items", null))
        assertEquals("{broken-json", prefs.getString("items_corrupt_snapshot", null))
    }

    @Test fun neverResurrectsAlertClearedByExplicitAcknowledgment() {
        AlertStore.add(context, Item("dispatch", "com.test", "", "", 1000L))
        assertTrue(AlertStore.acknowledgeIfMatches(context, "dispatch", 1000L))
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        prefs.edit().putString("items", "{broken").commit()
        assertEquals(0, AlertStore.count(context))
        assertTrue(AlertStore.isAcked(context, "dispatch", 1000L))
    }

    @Test fun recoveryAfterRepeatedRandomizedCorruptionMaintainsLastQueue() {
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val r = Random(81602)
        repeat(300) { index ->
            val key = "alert_$index"
            AlertStore.add(context, Item(key, "com.test", "", "", 5000L + index))
            val prior = prefs.getString("items_backup", null)
            val corruption = listOf("{invalid", "[]", "not-json", "{", "null", "\u0000").get(r.nextInt(6))
            prefs.edit().putString("items", corruption).commit()
            try {
                val keys = AlertStore.all(context).map { it.key }.toSet()
                assertTrue(keys.contains(key))
                assertEquals(index + 1, keys.size)
                assertEquals(prior, prefs.getString("items", null))
            } catch (failure: Throwable) {
                throw AssertionError("round $index corruption=$corruption", failure)
            }
        }
    }

    @Test fun corruptPrimaryAndBackupRemainBlockedInsteadOfErasingAlerts() {
        val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        prefs.edit().putString("items", "{bad")
            .putString("items_backup", "{also-bad").commit()
        try {
            AlertStore.count(context)
            fail("must not silently erase unreadable queues")
        } catch (_: IllegalStateException) {}
        assertEquals("{bad", prefs.getString("items", null))
    }
}
