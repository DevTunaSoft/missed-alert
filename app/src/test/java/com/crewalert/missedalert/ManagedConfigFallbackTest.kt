package com.crewalert.missedalert

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ManagedConfigFallbackTest {
    @Test fun missingRestrictionsServicePreservesSafeDefaults() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.RESTRICTIONS_SERVICE) return null
                return super.getSystemService(name)
            }
        }
        assertEquals(60000L, com.crewalert.missedalert.Config.intervalMs(context))
        assertEquals(0, com.crewalert.missedalert.Config.maxRepeats(context))
        assertTrue(com.crewalert.missedalert.Config.watchedPackages(context)
            .contains("com.google.android.apps.messaging"))
    }
}
