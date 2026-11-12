package com.crewalert.missedalert

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

/**
 * Settings come from the MDM (Knox Manage / Knox Service Plugin managed app config).
 * Every key is optional; defaults are safe.
 */
object Config {
    /** Apps that are always watched: TraumaSoft MDT plus the common text-message apps. */
    val SMS_PACKAGES = listOf(
        "com.traumasoft.mdt",
        "com.traumasoft.mdtv2",
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms",
    )

    /**
     * Phone/dialer packages. Notifications from these (or any CATEGORY_CALL notification) are
     * tracked ONLY if they are missed-call notifications – never incoming/ongoing calls.
     * "com.android.server.telecom" posts the standard Android missed-call notification
     * (channel "TelecomMissedCalls"); the others are Samsung / Google / AOSP dialers.
     */
    val CALL_PACKAGES = setOf(
        "com.android.server.telecom",
        "com.samsung.android.incallui",
        "com.samsung.android.dialer",
        "com.samsung.android.app.telephonyui",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.phone",
    )

    private fun restrictions(c: Context): Bundle =
        (c.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager)
            ?.applicationRestrictions ?: Bundle.EMPTY

    /** SMS apps + any extra packages pushed by the MDM (e.g. the TraumaSoft MDT app). */
    fun watchedPackages(c: Context): Set<String> {
        val extra = restrictions(c).getString("extra_packages")
            ?.split(",", ";", "\n", " ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return (SMS_PACKAGES + extra).toSet()
    }

    /** Knox sets true to launch silently. Default false gives IT a visible status screen. */
    fun headlessOnLaunch(c: Context): Boolean =
        restrictions(c).getBoolean("headless_on_launch", false)

    fun intervalMs(c: Context): Long =
        restrictions(c).getInt("interval_seconds", 60).coerceAtLeast(15) * 1000L

    /** 0 = repeat until acknowledged. */
    fun maxRepeats(c: Context): Int = restrictions(c).getInt("max_repeats", 0)
        .coerceAtLeast(0)
}
