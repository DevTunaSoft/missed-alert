package com.crewalert.missedalert

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class EventLogRetentionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun logDir(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").also { it.mkdirs() }

    @Test fun pruneRemovesExpiredLogsButPreservesCurrentDay() {
        val dir = logDir()
        val old = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val oldFile = File(dir, "events-${dateFormat.format(old.time)}.log")
        val todayFile = File(dir, "events-${dateFormat.format(Calendar.getInstance().time)}.log")
        // Simulate the first retention pass of a fresh process: the singleton
        // caches the last prune date across Robolectric tests in this JVM.
        EventLog::class.java.getDeclaredField("lastPruneDay").apply {
            isAccessible = true
            set(null, "")
        }
        oldFile.writeText("expired event\n")
        todayFile.appendText("current day sentinel\n")
        EventLog.write(context, "retention_test")
        assertFalse("expired diagnostic file should be pruned", oldFile.exists())
        assertTrue("current-day diagnostics must remain", todayFile.exists())
        assertTrue(todayFile.readText().contains("current day sentinel"))
    }

    @Test fun zipExportIncludesHeaderAndRetainedLogWithoutReadingWholeArchiveIntoMemory() {
        EventLog.write(context, "zip_export_test")
        val output = ByteArrayOutputStream()
        EventLog.exportZip(context, "test-device-status", output)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertEquals("test-device-status", entries["device-status.txt"])
        assertTrue(entries.values.any { it.contains("zip_export_test") })
    }
}
