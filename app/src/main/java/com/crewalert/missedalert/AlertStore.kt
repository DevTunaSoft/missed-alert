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

    @Synchronized
    fun all(c: Context): List<Item> {
        val o = JSONObject(p(c).getString("items", "{}") ?: "{}")
        return o.keys().asSequence().map { k ->
            val j = o.getJSONObject(k)
            Item(k, j.optString("pkg"), j.optString("title"), j.optString("text"), j.optLong("t"))
        }.sortedBy { it.postTime }.toList()
    }

    @Synchronized
    fun add(c: Context, item: Item) {
        val o = JSONObject(p(c).getString("items", "{}") ?: "{}")
        o.put(
            item.key,
            JSONObject().put("pkg", item.pkg).put("t", item.postTime)
        )
        p(c).edit().putString("items", o.toString()).commit()
    }

    @Synchronized
    fun remove(c: Context, key: String): Boolean {
        val o = JSONObject(p(c).getString("items", "{}") ?: "{}")
        val had = o.has(key)
        o.remove(key)
        p(c).edit().putString("items", o.toString()).commit()
        return had
    }

    @Synchronized
    fun clear(c: Context) {
        p(c).edit().putString("items", "{}").putInt("repeats", 0).commit()
    }

    fun count(c: Context) = all(c).size

    fun resetRepeats(c: Context) = p(c).edit().putInt("repeats", 0).commit()

    // --- acknowledged markers (key|postTime) so a listener reconnect doesn't re-arm old alerts ---

    @Synchronized
    fun markAcked(c: Context, items: List<Item>) {
        val arr = JSONArray(p(c).getString("acked", "[]") ?: "[]")
        items.forEach { arr.put("${it.key}|${it.postTime}") }
        val trimmed = JSONArray()
        val start = (arr.length() - 200).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        p(c).edit().putString("acked", trimmed.toString()).commit()
    }

    @Synchronized
    fun isAcked(c: Context, key: String, postTime: Long): Boolean {
        val arr = JSONArray(p(c).getString("acked", "[]") ?: "[]")
        val needle = "$key|$postTime"
        for (i in 0 until arr.length()) if (arr.getString(i) == needle) return true
        return false
    }

    // --- repeat counter ---
    fun repeats(c: Context) = p(c).getInt("repeats", 0)
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
        val set = p(c).getStringSet("seen", emptySet())!!.toMutableSet()
        if (set.add(pkg)) p(c).edit().putStringSet("seen", set).commit()
    }

    fun seen(c: Context): List<String> = p(c).getStringSet("seen", emptySet())!!.sorted()
}
