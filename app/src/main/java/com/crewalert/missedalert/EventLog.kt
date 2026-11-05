package com.crewalert.missedalert

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small rolling event log for IT / pilot evidence.
 * NEVER records message text, titles, names or phone numbers - only package names, channel ids,
 * counts, timings and reason codes.
 *
 * Pull it with:  adb pull /sdcard/Android/data/com.crewalert.missedalert/files/logs
 * (events.log = current, events.1.log = previous; each rolls at ~256 KB)
 */
object EventLog {
    private const val TAG = "MissedAlert"
    private const val MAX_BYTES = 256 * 1024L

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())

    private fun dir(c: Context): File {
        val base = c.getExternalFilesDir(null) ?: c.filesDir
        return File(base, "logs").also { it.mkdirs() }
    }

    private fun clean(v: Any?): String = (v?.toString() ?: "null").replace(Regex("\\s+"), "_").take(80)

    @Synchronized
    fun write(c: Context, event: String, vararg fields: Pair<String, Any?>) {
        val line = buildString {
            append(stamp()).append(" up=").append(SystemClock.elapsedRealtime() / 1000).append("s ").append(event)
            fields.forEach { (k, v) -> append(' ').append(k).append('=').append(clean(v)) }
        }
        Log.i(TAG, line)
        try {
            val d = dir(c)
            val f = File(d, "events.log")
            if (f.length() > MAX_BYTES) {
                val old = File(d, "events.1.log")
                old.delete()
                f.renameTo(old)
            }
            File(d, "events.log").appendText(line + "\n")
        } catch (e: Exception) {
            // logging must never break alerting
        }
    }

    fun tail(c: Context, n: Int): List<String> =
        try { File(dir(c), "events.log").readLines().takeLast(n) } catch (e: Exception) { emptyList() }
}
