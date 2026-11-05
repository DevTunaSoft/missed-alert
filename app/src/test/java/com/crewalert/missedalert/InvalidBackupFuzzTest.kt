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
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class InvalidBackupFuzzTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun loadCorruptBackup(raw: String) {
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit()
            .putString("items", "{primary-corrupted")
            .putString("items_backup", raw).commit()
    }

    @Test fun malformedButParseableBackupsNeverReplacePrimary() {
        val samples = listOf(
            "{\"key\":42}",
            "{\"key\":{\"t\":123}}",
            "{\"key\":{\"pkg\":\"com.test\"}}",
            "{\"key\":{\"pkg\":\"\",\"t\":123}}",
            "{\"key\":{\"pkg\":\"com.test\",\"t\":-1}}",
            "{\"key\":{\"pkg\":\"com.test\",\"t\":0}}"
        )
        for (candidate in samples) {
            loadCorruptBackup(candidate)
            try {
                AlertStore.count(context)
                fail("invalid backup was accepted: $candidate")
            } catch (_: IllegalStateException) { }
            assertEquals("{primary-corrupted", context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
                .getString("items", null))
        }
    }

    @Test fun validRecoveryRestoresEveryRecordAndPreservesCorruptBytes() {
        repeat(20) { i -> AlertStore.add(context, Item("key_$i", "com.test", "", "", 1000L + i)) }
        val preferences = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val expected = preferences.getString("items_backup", null)
        preferences.edit().putString("items", "{primary-corrupted").commit()
        assertEquals(20, AlertStore.count(context))
        assertEquals(expected, preferences.getString("items", null))
        assertEquals("{primary-corrupted", preferences.getString("items_corrupt_snapshot", null))
    }

    @Test fun randomizedInvalidEntriesCannotPoisonBackupRecovery() {
        val r = Random(710713)
        repeat(400) { round ->
            val obj = JSONObject().put("valid", JSONObject()
                .put("pkg", "com.test").put("t", 5000L + round))
            val malformed = when (r.nextInt(5)) {
                0 -> 10
                1 -> JSONObject().put("pkg", "com.test")
                2 -> JSONObject().put("t", 33L)
                3 -> JSONObject().put("pkg", "").put("t", 33L)
                else -> JSONObject().put("pkg", "com.test").put("t", -3L)
            }
            obj.put("broken_$round", malformed)
            loadCorruptBackup(obj.toString())
            try {
                AlertStore.count(context)
                fail("round $round accepted invalid backup")
            } catch (_: IllegalStateException) { }
        }
    }
}
