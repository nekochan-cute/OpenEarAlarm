// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, offline-only execution history. Never used as a prerequisite to ringing. */
object AlarmHistory {
    data class Entry(val timeMillis: Long, val id: Int, val action: String)

    private const val STORAGE = "alarm_history_v1"
    private const val LIMIT = 40

    fun record(context: Context, alarmId: Int, action: String) {
        try {
            synchronized(this) {
                val prefs = context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
                val existing = JSONArray(prefs.getString("events", "[]") ?: "[]")
                val next = JSONArray()
                val start = (existing.length() - LIMIT + 1).coerceAtLeast(0)
                for (i in start until existing.length()) next.put(existing.getJSONObject(i))
                next.put(JSONObject().apply {
                    put("t", System.currentTimeMillis())
                    put("id", alarmId)
                    put("event", action)
                })
                prefs.edit().putString("events", next.toString()).commit()
            }
        } catch (_: Exception) {
            // Logging must never prevent an alarm from starting, stopping or snoozing.
        }
    }

    fun recent(context: Context): List<Entry> = try {
        synchronized(this) {
            val prefs = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
            val raw = JSONArray(prefs.getString("events", "[]") ?: "[]")
            (0 until raw.length()).mapNotNull { index ->
                runCatching {
                    val e = raw.getJSONObject(index)
                    Entry(e.getLong("t"), e.getInt("id"), e.getString("event"))
                }.getOrNull()
            }.asReversed()
        }
    } catch (_: Exception) { emptyList() }
}
