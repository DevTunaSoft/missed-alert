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
class StoreCommitIntegrityTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun additionsAndRemovalsKeepBackupInSync() {
        repeat(70) { i ->
            AlertStore.add(context, Item("k_$i", "com.test", "", "", 1000L + i))
        }
        val p = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        assertEquals(p.getString("items", null), p.getString("items_backup", null))
        repeat(30) { i -> assertTrue(AlertStore.remove(context, "k_$i")) }
        assertEquals(p.getString("items", null), p.getString("items_backup", null))
        assertEquals(40, AlertStore.count(context))
    }
    @Test fun clearKeepsPrimaryAndBackupEmpty() {
        AlertStore.add(context, Item("k", "com.test", "", "", 1000L))
        AlertStore.clear(context)
        val p = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        assertEquals("{}", p.getString("items", null))
        assertEquals("{}", p.getString("items_backup", null))
    }
    @Test fun acknowledgedMarkersSurviveSuccessfulWrite() {
        val item = Item("k", "com.test", "", "", 1000L)
        AlertStore.markAcked(context, listOf(item))
        assertTrue(AlertStore.isAcked(context, item.key, item.postTime))
    }
}
