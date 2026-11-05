package com.crewalert.missedalert

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.TextView

/**
 * Setup / health screen. In production nobody needs to open it: Knox grants the permissions
 * and the listener runs on its own. It's here for staging, troubleshooting, and for discovering
 * the MDT app's package name (see "Apps that have posted notifications").
 */
class StatusActivity : Activity() {
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecoveryJobService.ensureScheduled(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            fitsSystemWindows = true
        }
        info = TextView(this).apply { textSize = 15f }
        root.addView(info, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        fun btn(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action(); refresh() }
            })
        }
        btn("1. Grant notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btn("2. Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else Toast.makeText(this, "Permission not required on this Android version", Toast.LENGTH_SHORT).show()
        }
        btn("3. Disable battery optimization") {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
        btn("Send test alert (repeats until acknowledged)") {
            Notifier.ensureChannel(this)
            AlertStore.add(
                this,
                Item("test", packageName, "Test alert", "Repeats every minute until acknowledged", System.currentTimeMillis())
            )
            AlertStore.resetRepeats(this)
            Scheduler.scheduleNext(this)
        }
        btn("Acknowledge all") { Acker.ackAll(this, "status_screen") }
        btn("Export log (saves to Downloads)") { exportLog() }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Saves the full event log to Downloads (no file picker and no permissions needed, so it works in a kiosk). */
    private fun exportLog() {
        val name = "MissedAlert-log-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        try {
            val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
            val header = "Missed Alert log export\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "App version: $version\n\n" + info.text
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("could not create file")
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Downloads output stream unavailable")
            output.use { it.write(EventLog.export(this, header).toByteArray()) }
            EventLog.write(this, "log_exported", "file" to name)
            Toast.makeText(this, "Saved to Downloads/$name", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun ok(b: Boolean) = if (b) "OK" else "MISSING"

    private fun refresh() {
        val nm = getSystemService(NotificationManager::class.java)
        val listener = nm.isNotificationListenerAccessGranted(ComponentName(this, MissedAlertListener::class.java))
        val notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val exact = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        info.text = buildString {
            appendLine("Notification access:   ${ok(listener)}")
            appendLine("Post notifications:    ${ok(notif)}")
            appendLine("Battery unrestricted:  ${ok(battery)}")
            appendLine("Exact alarms:          ${ok(exact)}")
            appendLine()
            appendLine("Repeat every ${Config.intervalMs(this@StatusActivity) / 1000}s" +
                    (Config.maxRepeats(this@StatusActivity).let { if (it > 0) ", max $it" else ", until acknowledged" }))
            appendLine("Pending alerts: ${AlertStore.count(this@StatusActivity)}")
            appendLine("Log: adb pull /sdcard/Android/data/${this@StatusActivity.packageName}/files/logs")
            appendLine()
            appendLine("Watching:")
            Config.watchedPackages(this@StatusActivity).sorted().forEach { appendLine("  $it") }
            appendLine()
            appendLine("Apps that have posted notifications")
            appendLine("(use to find the MDT app's package name):")
            AlertStore.seen(this@StatusActivity).forEach { appendLine("  $it") }
            appendLine()
            appendLine("Recent events:")
            EventLog.tail(this@StatusActivity, 25).forEach { appendLine(it) }
        }
    }
}
