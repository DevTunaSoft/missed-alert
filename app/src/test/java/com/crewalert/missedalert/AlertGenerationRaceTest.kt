package com.crewalert.missedalert

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AlertGenerationRaceTest {
    private lateinit var context: Context

    @Before fun clear() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun staleRemovalCannotAcknowledgeNewGeneration() {
        AlertStore.add(context, Item("stable-key", "com.test.dispatch", "", "", 101L))
        AlertStore.add(context, Item("stable-key", "com.test.dispatch", "", "", 202L))
        assertFalse(AlertStore.acknowledgeIfMatches(context, "stable-key", 101L))
        assertEquals(202L, AlertStore.all(context).single().postTime)
        assertFalse(AlertStore.isAcked(context, "stable-key", 202L))
        assertTrue(AlertStore.acknowledgeIfMatches(context, "stable-key", 202L))
        assertEquals(0, AlertStore.count(context))
        assertTrue(AlertStore.isAcked(context, "stable-key", 202L))
    }

    @Test fun randomizedStaleCallbacksPreserveLatestPendingAlert() {
        val r = java.util.Random(440112)
        repeat(700) { i ->
            val key = "notif-$i"
            val generation = (i + 1L) * 1000L
            AlertStore.add(context, Item(key, "com.test", "", "", generation))
            repeat(r.nextInt(8) + 1) {
                val stale = generation - (r.nextInt(900) + 1)
                assertFalse(AlertStore.acknowledgeIfMatches(context, key, stale))
            }
            assertEquals(generation, AlertStore.all(context).first { it.key == key }.postTime)
            assertTrue(AlertStore.acknowledgeIfMatches(context, key, generation))
        }
        assertEquals(0, AlertStore.count(context))
    }

    @Test fun concurrentAcknowledgmentsOnlyClearTheirOwnGeneration() {
        val workers = 5
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val error = AtomicReference<Throwable?>()
        val pool = Executors.newFixedThreadPool(workers)
        try {
            repeat(workers) { w ->
                pool.execute {
                    try {
                        start.await()
                        repeat(60) { i ->
                            val key = "k-$w-$i"
                            val fresh = (i + 1L) * 1000L
                            AlertStore.add(context, Item(key, "com.test", "", "", fresh))
                            check(!AlertStore.acknowledgeIfMatches(context, key, fresh - 1L))
                            check(AlertStore.acknowledgeIfMatches(context, key, fresh))
                        }
                    } catch (t: Throwable) { error.compareAndSet(null, t) }
                    finally { done.countDown() }
                }
            }
            start.countDown()
            assertTrue(done.await(40, TimeUnit.SECONDS))
            error.get()?.let { throw AssertionError("concurrent ack failed", it) }
            assertEquals(0, AlertStore.count(context))
        } finally { pool.shutdownNow() }
    }
}
