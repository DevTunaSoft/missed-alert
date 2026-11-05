package com.crewalert.missedalert

import android.service.notification.NotificationListenerService as NLS
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NotificationRemovalPolicyTest {
    @Test fun allExplicitUserActionsAcknowledge() {
        for (reason in intArrayOf(NLS.REASON_CLICK, NLS.REASON_CANCEL,
            NLS.REASON_CANCEL_ALL, NLS.REASON_LISTENER_CANCEL,
            NLS.REASON_LISTENER_CANCEL_ALL)) {
            assertTrue("dismissal reason $reason", NotificationRemovalPolicy.isUserDismissal(reason))
        }
    }
    @Test fun sourceAndSystemRemovalsCannotAcknowledge() {
        for (reason in intArrayOf(NLS.REASON_APP_CANCEL, NLS.REASON_APP_CANCEL_ALL,
            NLS.REASON_TIMEOUT, NLS.REASON_ERROR, NLS.REASON_PACKAGE_CHANGED,
            NLS.REASON_PACKAGE_BANNED, NLS.REASON_SNOOZED, -1,
            Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertFalse("nondismissal reason $reason", NotificationRemovalPolicy.isUserDismissal(reason))
        }
    }
    @Test fun exhaustiveIntegerDomainSamplesNeverAccidentallyAcknowledge() {
        val random = java.util.Random(88211L)
        repeat(10000) {
            val reason = random.nextInt()
            val intended = reason in setOf(NLS.REASON_CLICK, NLS.REASON_CANCEL,
                NLS.REASON_CANCEL_ALL, NLS.REASON_LISTENER_CANCEL, NLS.REASON_LISTENER_CANCEL_ALL)
            assertEquals("reason=$reason", intended, NotificationRemovalPolicy.isUserDismissal(reason))
        }
    }
}
