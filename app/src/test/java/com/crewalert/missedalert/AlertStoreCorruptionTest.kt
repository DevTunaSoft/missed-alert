package com.crewalert.missedalert

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlertStoreCorruptionTest {
    private lateinit var context: Context

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun raw(value: String) {
        assertTrue(context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .edit().putString("items", value).commit())
    }

    @Test fun preservesValidRecordsAmongMalformedEntries() {
        val obj = JSONObject()
        obj.put("valid", JSONObject().put("pkg", "com.test.dispatch").put("t", 1234567L))
        obj.put("unexpected_scalar", "broken")
        obj.put("missing_package", JSONObject().put("t", 12L))
        obj.put("bad_timestamp", JSONObject().put("pkg", "com.test.dispatch").put("t", -1L))
        raw(obj.toString())
        val items = AlertStore.all(context)
        assertEquals(1, items.size)
        assertEquals("valid", items[0].key)
        assertEquals("com.test.dispatch", items[0].pkg)
        assertTrue(context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .getString("items", "")!!.contains("unexpected_scalar"))
    }

    @Test fun malformedTopLevelDoesNotSilentlyOverwriteUnrecoverableData() {
        val corrupted = "{not-json"
        raw(corrupted)
        try {
            AlertStore.add(context, Item("new", "com.test", "", "", 300L))
            fail("Expected a corruption error")
        } catch (_: IllegalStateException) { }
        assertEquals(corrupted, context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .getString("items", null))
    }

    @Test fun corruptAcknowledgmentMarkersDontCrashNotificationCheck() {
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit()
            .putString("acked", "{invalid-array").commit()
        assertFalse(AlertStore.isAcked(context, "k", 99L))
        AlertStore.markAcked(context, listOf(Item("k", "com.test", "", "", 99L)))
        assertTrue(AlertStore.isAcked(context, "k", 99L))
    }

    @Test fun randomizedMalformedEntriesNeverHideValidRecords() {
        val r = Random(1729)
        repeat(400) { round ->
            val obj = JSONObject()
            val good = "good_$round"
            obj.put(good, JSONObject().put("pkg", "com.test").put("t", 1000L + round))
            repeat(r.nextInt(1, 20)) { i ->
                val bad = "bad_$i"
                when (r.nextInt(5)) {
                    0 -> obj.put(bad, 42)
                    1 -> obj.put(bad, JSONObject().put("pkg", "com.test"))
                    2 -> obj.put(bad, JSONObject().put("t", 12))
                    3 -> obj.put(bad, JSONObject().put("pkg", "").put("t", 8))
                    else -> obj.put(bad, JSONObject().put("pkg", "com.test").put("t", -3))
                }
            }
            raw(obj.toString())
            val result = AlertStore.all(context)
            assertEquals("round $round", 1, result.size)
            assertEquals(good, result.single().key)
        }
    }
}
