package com.crewalert.missedalert

import android.content.Context
import android.content.RestrictionsManager

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

    private fun restrictions(c: Context) =
        (c.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager).applicationRestrictions

    /** SMS apps + any extra packages pushed by the MDM (e.g. the TraumaSoft MDT app). */
    fun watchedPackages(c: Context): Set<String> {
        val extra = restrictions(c).getString("extra_packages")
            ?.split(",", ";", "\n", " ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return (SMS_PACKAGES + extra).toSet()
    }

    fun intervalMs(c: Context): Long =
        restrictions(c).getInt("interval_seconds", 60).coerceAtLeast(15) * 1000L

    /** 0 = repeat until acknowledged. */
    fun maxRepeats(c: Context): Int = restrictions(c).getInt("max_repeats", 0)
}
