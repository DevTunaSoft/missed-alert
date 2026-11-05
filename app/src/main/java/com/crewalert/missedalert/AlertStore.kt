package com.crewalert.missedalert

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val postTime: Long,
)

/** Tiny persistent store so pending alerts survive process death. */
object AlertStore {
    private fun p(c: Context) = c.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    /** Backups must be more than parseable JSON: reject missing or malformed entries.
     * Otherwise recovery could silently convert real pending alerts into an empty queue.
     */
    private fun validBackup(o: JSONObject): Boolean {
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = o.optJSONObject(key) ?: return false
            if (!entry.has("pkg") || entry.optString("pkg").isBlank() ||
                !entry.has("t") || entry.optLong("t") <= 0L) return false
        }
        return true
    }

    private fun objectItems(c: Context): JSONObject {
        val raw = p(c).getString("items", "{}") ?: "{}"
        return try { JSONObject(raw) } catch (e: Exception) {
            EventLog.write(c, "items_corrupt", "error" to e.javaClass.simpleName)
            // Restore only from a previously committed, parseable snapshot.
            // Preserve the damaged bytes for IT diagnosis and do not silently clear alerts.
            val backup = p(c).getString("items_backup", null)
            val recovered = try { backup?.let { JSONObject(it) } } catch (_: Exception) { null }
            if (recovered != null && backup != null && validBackup(recovered)) {
                val saved = p(c).edit().putString("items_corrupt_snapshot", raw)
                    .putString("items", backup).commit()
                if (saved) {
                    EventLog.write(c, "items_restored_from_backup", "count" to recovered.length())
                    return recovered
                }
                EventLog.write(c, "items_restore_commit_failed")
            }
            throw IllegalStateException("Pending alert storage corrupted without usable backup", e)
        }
    }

    private fun ackArray(c: Context): JSONArray {
        val raw = p(c).getString("acked", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (e: Exception) {
            EventLog.write(c, "ack_markers_corrupt", "error" to e.javaClass.simpleName)
            JSONArray()
        }
    }

    @Synchronized
    fun all(c: Context): List<Item> {
        val o = objectItems(c)
        return o.keys().asSequence().mapNotNull { k ->
            try {
                val j = o.getJSONObject(k)
                if (!j.has("pkg") || !j.has("t") || j.optString("pkg").isBlank() ||
                    j.optLong("t") <= 0L) {
                    EventLog.write(c, "alert_entry_invalid")
                    null
                } else {
                    Item(k, j.optString("pkg"), j.optString("title"), j.optString("text"), j.optLong("t"))
                }
            } catch (e: Exception) {
                EventLog.write(c, "alert_entry_corrupt", "error" to e.javaClass.simpleName)
                null
            }
        }.sortedBy { it.postTime }.toList()
    }

    @Synchronized
    fun add(c: Context, item: Item) {
        val o = objectItems(c)
        o.put(
            item.key,
            JSONObject().put("pkg", item.pkg).put("t", item.postTime)
        )
        if (!p(c).edit().putString("items", o.toString())
                .putString("items_backup", o.toString()).commit()) {
            EventLog.write(c, "items_commit_failed")
            throw IllegalStateException("Pending alert write failed")
        }
    }

    /** Ignore stale removal callbacks for a reused notification key.
     * Ack marker and pending queue are committed in one preferences transaction.
     */
    @Synchronized
    fun acknowledgeIfMatches(c: Context, key: String, postTime: Long): Boolean {
        val items = objectItems(c)
        val entry = items.optJSONObject(key) ?: return false
        if (entry.optLong("t") != postTime) return false
        val acked = ackArray(c)
        acked.put("$key|$postTime")
        val trimmed = JSONArray()
        for (i in (acked.length() - 200).coerceAtLeast(0) until acked.length()) {
            trimmed.put(acked.get(i))
        }
        items.remove(key)
        if (!p(c).edit().putString("items", items.toString())
                .putString("items_backup", items.toString())
                .putString("acked", trimmed.toString()).commit()) {
            EventLog.write(c, "ack_persist_failed")
            return false
        }
        return true
    }

    @Synchronized
    fun remove(c: Context, key: String): Boolean {
        val o = objectItems(c)
        val had = o.has(key)
        o.remove(key)
        if (!p(c).edit().putString("items", o.toString())
                .putString("items_backup", o.toString()).commit()) {
            EventLog.write(c, "items_commit_failed")
            throw IllegalStateException("Pending alert write failed")
        }
        return had
    }

    /** Commit bulk acknowledgments and queue clearance together so a crash
     * cannot leave an empty queue with missing acknowledgment markers.
     */
    @Synchronized
    fun acknowledgeAll(c: Context): Int {
        val source = objectItems(c)
        val markers = ackArray(c)
        val keys = source.keys()
        var count = 0
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = source.optJSONObject(key) ?: continue
            if (!entry.has("t") || entry.optLong("t") <= 0L) continue
            markers.put("$key|${entry.optLong("t")}")
            count++
        }
        val trimmed = JSONArray()
        for (i in (markers.length() - 200).coerceAtLeast(0) until markers.length()) {
            trimmed.put(markers.get(i))
        }
        if (!p(c).edit().putString("items", "{}")
                .putString("items_backup", "{}")
                .putString("acked", trimmed.toString())
                .putInt("repeats", 0).commit()) {
            throw IllegalStateException("Unable to persist bulk acknowledgment")
        }
        return count
    }

    @Synchronized
    fun clear(c: Context) {
        if (!p(c).edit().putString("items", "{}").putString("items_backup", "{}")
                .putInt("repeats", 0).commit()) {
            EventLog.write(c, "items_commit_failed")
            throw IllegalStateException("Pending alert clear failed")
        }
    }

    fun count(c: Context) = all(c).size

    fun resetRepeats(c: Context) = p(c).edit().putInt("repeats", 0).commit()

    // --- acknowledged markers (key|postTime) so a listener reconnect doesn't re-arm old alerts ---

    @Synchronized
    fun markAcked(c: Context, items: List<Item>) {
        val arr = ackArray(c)
        items.forEach { arr.put("${it.key}|${it.postTime}") }
        val trimmed = JSONArray()
        val start = (arr.length() - 200).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        if (!p(c).edit().putString("acked", trimmed.toString()).commit()) {
            EventLog.write(c, "ack_persist_failed")
            throw IllegalStateException("Acknowledgment markers write failed")
        }
    }

    @Synchronized
    fun isAcked(c: Context, key: String, postTime: Long): Boolean {
        val arr = ackArray(c)
        val needle = "$key|$postTime"
        for (i in 0 until arr.length()) if (arr.getString(i) == needle) return true
        return false
    }

    // --- repeat counter ---
    fun repeats(c: Context) = p(c).getInt("repeats", 0)
    @Synchronized
    fun incRepeats(c: Context): Int {
        val n = repeats(c) + 1
        p(c).edit().putInt("repeats", n).commit()
        return n
    }

    // --- "next alarm" bookkeeping (a PendingIntent still exists after it fires, so track time) ---
    fun nextAt(c: Context) = p(c).getLong("nextAt", 0L)
    fun setNextAt(c: Context, v: Long) = p(c).edit().putLong("nextAt", v).commit()

    // --- discovery: which packages have posted notifications (helps find the MDT package name) ---
    @Synchronized
    fun recordSeen(c: Context, pkg: String) {
        val set = (p(c).getStringSet("seen", emptySet()) ?: emptySet()).toMutableSet()
        if (set.add(pkg)) p(c).edit().putStringSet("seen", set).commit()
    }

    fun seen(c: Context): List<String> = (p(c).getStringSet("seen", emptySet()) ?: emptySet()).sorted()
}
