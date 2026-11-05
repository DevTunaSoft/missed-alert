package com.crewalert.missedalert

import android.app.Activity
import android.os.Bundle

/** Invisible: acknowledges, opens the app the alert came from, and finishes immediately. */
class AckActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Acker.ackAll(this)
        intent.getStringExtra("pkg")?.let { pkg ->
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        }
        finish()
    }
}
