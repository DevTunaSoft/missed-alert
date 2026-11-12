package com.crewalert.missedalert

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Fourteen days of bounded diagnostic events, including the current day.
 * Daily files avoid the former 256 KB rolling-log data loss.
 * No message bodies, titles, contact names or phone numbers are logged.
 * Files live in app-specific storage and are deleted only after the retention window.
 */
object EventLog {
    private const val TAG = "MissedAlert"
    private const val PREFIX = "events-"
    private const val SUFFIX = ".log"
    private const val RETAIN_DAYS = 14
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    private var lastPruneDay = ""

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())

    private fun dateKey(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    private fun dir(c: Context): File {
        val base = c.getExternalFilesDir(null) ?: c.filesDir
        return File(base, "logs").also { it.mkdirs() }
    }

    private fun files(c: Context): List<File> =
        dir(c).listFiles()?.filter {
            it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) &&
                Regex("""events-\d{4}-\d{2}-\d{2}\.log""").matches(it.name)
        }?.sortedBy { it.name } ?: emptyList()

    private fun prune(c: Context) {
        val keep = mutableSetOf<String>()
        val cal = Calendar.getInstance()
        repeat(RETAIN_DAYS) {
            keep.add("$PREFIX${dateKey(cal.time)}$SUFFIX")
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        files(c).filter { it.name !in keep }.forEach { it.delete() }
        val current = "$PREFIX${dateKey()}$SUFFIX"
        var total = files(c).sumOf { it.length() }
        for (file in files(c)) {
            if (total <= MAX_TOTAL_BYTES) break
            if (file.name == current) continue
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun clean(v: Any?): String =
        (v?.toString() ?: "null").replace(Regex("""\s+"""), "_").take(80)

    @Synchronized
    fun write(c: Context, event: String, vararg fields: Pair<String, Any?>) {
        val line = buildString {
            append(stamp()).append(" up=").append(SystemClock.elapsedRealtime() / 1000).append("s ").append(event)
            fields.forEach { (k, v) -> append(' ').append(k).append('=').append(clean(v)) }
        }
        Log.i(TAG, line)
        try {
            val d = dir(c)
            File(d, "$PREFIX${dateKey()}$SUFFIX").appendText(line + "\n")
            val today = dateKey()
            if (lastPruneDay != today) {
                prune(c)
                lastPruneDay = today
            }
        } catch (_: Exception) {
            // A full or inaccessible disk must never interrupt alert handling.
        }
    }

    /** Export all retained daily files, oldest first, including current diagnostics. */
    @Synchronized
    fun export(c: Context, header: String): String = buildString {
        append(header).append("\n\n")
        for (file in files(c)) {
            append("=== ").append(file.name).append(" ===\n")
            try { append(file.readText()) } catch (_: Exception) { append("[unreadable]\n") }
        }
    }

    /** Recent events across day boundaries, newest events last. */
    @Synchronized
    fun tail(c: Context, n: Int): List<String> {
        val out = ArrayDeque<String>()
        for (file in files(c)) {
            try {
                file.forEachLine { line ->
                    out.addLast(line)
                    if (out.size > n) out.removeFirst()
                }
            } catch (_: Exception) { }
        }
        return out.toList()
    }
}
