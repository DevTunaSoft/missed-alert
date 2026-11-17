package com.crewalert.missedalert

import android.service.notification.NotificationListenerService

/** User-initiated dismissal only. App/system removal is not acknowledgment. */
object NotificationRemovalPolicy {
    /** Only a click unambiguously opens this specific source notification.
     * Clearing or swiping it does not prove the message was reviewed.
     */
    fun isExplicitOpen(reason: Int): Boolean =
        reason == NotificationListenerService.REASON_CLICK

    fun isUserDismissal(reason: Int): Boolean = when (reason) {
        NotificationListenerService.REASON_CLICK,
        NotificationListenerService.REASON_CANCEL,
        NotificationListenerService.REASON_CANCEL_ALL,
        NotificationListenerService.REASON_LISTENER_CANCEL,
        NotificationListenerService.REASON_LISTENER_CANCEL_ALL -> true
        else -> false
    }
}
