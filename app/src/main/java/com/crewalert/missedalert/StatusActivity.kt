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
import android.graphics.Color
import android.service.notification.NotificationListenerService

/**
 * Setup / health screen. In production nobody needs to open it: Knox grants the permissions
 * and the listener runs on its own. It's here for staging, troubleshooting, and for discovering
 * the MDT app's package name (see "Apps that have posted notifications").
 */
open class StatusActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var headline: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecoveryJobService.ensureScheduled(this)
        Notifier.ensureChannel(this)
        try { Scheduler.ensureScheduled(this) } catch (e: Exception) {
            EventLog.write(this, "diagnostics_alarm_recover_failed", "error" to e.javaClass.simpleName)
        }
        val listenerComponent = ComponentName(this, MissedAlertListener::class.java)
        if (getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(listenerComponent)) {
            try { NotificationListenerService.requestRebind(listenerComponent) }
            catch (e: Exception) {
                EventLog.write(this, "diagnostics_listener_rebind_failed", "error" to e.javaClass.simpleName)
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(246, 248, 251))
            fitsSystemWindows = true
        }
        root.addView(TextView(this).apply {
            text = "MISSED ALERT"
            textSize = 27f
            setTextColor(Color.rgb(20, 38, 61))
            setPadding(0, 10, 0, 6)
        })
        root.addView(TextView(this).apply {
            text = "IT diagnostic dashboard • Monitoring starts automatically when permissions allow."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 20)
        })
        headline = TextView(this).apply {
            textSize = 20f
            setPadding(0, 4, 0, 12)
        }
        root.addView(headline)
        info = TextView(this).apply { textSize = 15f; setTextColor(Color.rgb(32, 48, 68)) }
        root.addView(info, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        fun btn(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action(); refresh() }
            })
        }
        btn("IT: Open notification access settings") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btn("IT: Allow notification posting") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else Toast.makeText(this, "Permission not required on this Android version", Toast.LENGTH_SHORT).show()
        }
        btn("IT: Open battery optimization settings") {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
        btn("IT: Test notification delivery (isolated)") {
            // One-shot diagnostic notification; never enters the dispatch queue or scheduler.
            try {
                val nm = getSystemService(NotificationManager::class.java)
                val channelId = "it_delivery_test_v1"
                if (nm.getNotificationChannel(channelId) == null) {
                    nm.createNotificationChannel(
                        android.app.NotificationChannel(
                            channelId, "IT delivery test",
                            NotificationManager.IMPORTANCE_HIGH
                        )
                    )
                }
                nm.notify(9494, android.app.Notification.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("IT notification delivery test")
                    .setContentText("Diagnostic only; no dispatch alert was created")
                    .setAutoCancel(true)
                    .build())
                EventLog.write(this, "it_delivery_test_posted")
                Toast.makeText(this, "Test posted. Verify sound on the device.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                EventLog.write(this, "it_delivery_test_failed", "error" to e.javaClass.simpleName)
                Toast.makeText(this, "Test failed: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
        btn("IT: Acknowledge all pending alerts") { Acker.ackAll(this, "status_screen") }
        btn("Refresh device status") { refresh() }
        btn("Export diagnostic report to Downloads") { exportLog() }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Stream the report to Downloads; discard incomplete archives after I/O failures. */
    private fun exportLog() {
        val name = "MissedAlert-log-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".zip"
        var uri: Uri? = null
        try {
            val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
            val header = "Missed Alert log export\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "App version: $version\n\n" + info.text
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("could not create file")
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Downloads output stream unavailable")
            output.use { EventLog.exportZip(this, header, it) }
            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            EventLog.write(this, "log_exported", "file" to name)
            Toast.makeText(this, "Saved to Downloads/$name", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            uri?.let { created ->
                try { contentResolver.delete(created, null, null) } catch (_: Exception) { }
            }
            EventLog.write(this, "log_export_failed", "error" to e.javaClass.simpleName)
            Toast.makeText(this, "Export failed: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun yes(b: Boolean) = if (b) "OK" else "MISSING"

    private fun refresh() {
        val nm = getSystemService(NotificationManager::class.java)
        val component = ComponentName(this, MissedAlertListener::class.java)
        val access = try { nm.isNotificationListenerAccessGranted(component) } catch (_: Exception) { false }
        val bound = MissedAlertListener.instance != null
        val posting = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val enabled = nm.areNotificationsEnabled()
        val channel = nm.getNotificationChannel("realert_v2")
        val channelAudible = channel != null && channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val exact = Build.VERSION.SDK_INT < 31 ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val issues = mutableListOf<String>()
        if (!access) issues.add("Notification access is OFF. The app cannot read MDT or text alerts.")
        if (access && !bound) issues.add("Notification listener has not connected yet. Check Android restrictions.")
        if (!posting) issues.add("Notification posting permission is OFF.")
        if (!enabled) issues.add("Android has disabled notifications for this app.")
        if (!channelAudible) issues.add("Reminder channel is disabled or set to silent/low importance.")
        if (!battery) issues.add("Battery exemption is missing; Knox should allowlist this app.")
        if (!exact) issues.add("Exact alarms are unavailable; repeats can be delayed in Doze.")
        val ready = access && bound && posting && enabled && channelAudible
        headline.text = if (ready) "MONITORING ACTIVE" else "NOT READY — ACTION REQUIRED"
        headline.setTextColor(if (ready) Color.rgb(15, 112, 65) else Color.rgb(177, 52, 39))
        val pending = try { AlertStore.count(this).toString() }
            catch (_: Exception) { "UNREADABLE — export diagnostics" }
        val watched = try { Config.watchedPackages(this).sorted().joinToString("\n  ") }
            catch (_: Exception) { "Unable to load watched packages" }
        val recent = try { EventLog.tail(this, 14).joinToString("\n") }
            catch (_: Exception) { "Unable to read diagnostic log" }
        info.text = buildString {
            appendLine("HOW IT WORKS")
            appendLine("The app automatically monitors watched notifications while Android allows the listener to run.")
            appendLine("No crew button presses are required. This page is for IT verification only.")
            appendLine()
            appendLine("DEVICE READINESS")
            appendLine("Notification access: ${yes(access)}")
            appendLine("Listener connected: ${yes(bound)}")
            appendLine("Can post notifications: ${yes(posting && enabled)}")
            appendLine("Reminder channel audible: ${yes(channelAudible)}")
            appendLine("Battery unrestricted: ${yes(battery)}")
            appendLine("Exact alarm permission: ${yes(exact)}")
            appendLine("Pending alerts: $pending")
            appendLine("Repeat interval: ${Config.intervalMs(this@StatusActivity) / 1000} seconds")
            appendLine()
            if (issues.isNotEmpty()) {
                appendLine("WHAT NEEDS ATTENTION")
                issues.forEach { appendLine("• $it") }
                appendLine()
            }
            appendLine("VERIFY DELIVERY")
            appendLine("Tap IT: Test notification delivery (isolated). This posts one diagnostic notification without modifying real alerts or scheduling repeats.")
            appendLine("If there is no sound, check channel sound, notification volume, DND, and Knox notification policy.")
            appendLine()
            appendLine("WATCHED APP PACKAGES")
            appendLine("  $watched")
            appendLine()
            appendLine("RECENT DIAGNOSTICS")
            appendLine(recent.ifBlank { "No events yet" })
        }
    }
}
