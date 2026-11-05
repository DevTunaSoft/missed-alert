package com.crewalert.missedalert

import android.content.Context
import android.content.Intent
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
class ConcurrencyRecoveryTest {
    private lateinit var context: Context

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        Scheduler.cancel(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun parallelUniqueNotificationsAreNotLost() {
        val workers = 6
        val perWorker = 35
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val error = AtomicReference<Throwable?>()
        val pool = Executors.newFixedThreadPool(workers)
        try {
            repeat(workers) { worker ->
                pool.execute {
                    try {
                        start.await()
                        repeat(perWorker) { n ->
                            val key = "worker_${worker}_${n}"
                            AlertStore.add(context, Item(key, "com.test.dispatch", "", "", 1000L + n))
                        }
                    } catch (t: Throwable) {
                        error.compareAndSet(null, t)
                    } finally {
                        done.countDown()
                    }
                }
            }
            start.countDown()
            assertTrue("workers timed out", done.await(45, TimeUnit.SECONDS))
            error.get()?.let { throw AssertionError("concurrent add failed", it) }
            assertEquals(workers * perWorker, AlertStore.count(context))
            assertEquals(workers * perWorker, AlertStore.all(context).map { it.key }.toSet().size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun watchdogRearmsStoredAlertAfterAlarmCancellation() {
        AlertStore.add(context, Item("dispatch", "com.test.dispatch", "", "", 1000L))
        assertTrue(Scheduler.scheduleNext(context))
        assertTrue(AlertStore.nextAt(context) > 0L)
        Scheduler.cancel(context)
        assertEquals(0L, AlertStore.nextAt(context))
        Scheduler.ensureScheduled(context)
        assertTrue("pending alert must be rearmed", AlertStore.nextAt(context) > 0L)
        Scheduler.cancel(context)
        assertEquals(0L, AlertStore.nextAt(context))
    }

    @Test fun unreadableStorageDoesNotCancelAlarmState() {
        val bad = "{broken-pending-data"
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit()
            .putString("items", bad).putLong("nextAt", 12345L).commit()
        ReAlertReceiver().onReceive(context, Intent(context, ReAlertReceiver::class.java))
        assertEquals(bad, context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .getString("items", null))
        assertEquals(12345L, AlertStore.nextAt(context))
    }
}
