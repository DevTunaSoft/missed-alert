package com.crewalert.missedalert

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Invisible: acknowledges, opens the app the alert came from, and finishes immediately. */
class AckActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Acker.ackAll(this, "tap_reminder")
        val pkg = intent.getStringExtra("pkg")
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: if (pkg != null && pkg in Config.CALL_PACKAGES) {
                // Telecom itself has no launcher icon: open the call log instead.
                Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.dir/calls")
            } else null
        try {
            launch?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } catch (e: Exception) {
            // nothing to open; acknowledgement already recorded
        }
        finish()
    }
}
