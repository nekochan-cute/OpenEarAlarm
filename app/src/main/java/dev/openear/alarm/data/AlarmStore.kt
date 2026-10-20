// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small synchronous store: commit before scheduling so a receiver sees durable state. */
class AlarmStore(context: Context) {
    companion object {
        // AlarmReceiver, AlarmService and UI use different instances. An instance
        // monitor does not protect their shared JSON read-modify-write transactions.
        private val storageLock = Any()
    }
    // Direct Boot: schedules must be readable before the first unlock after reboot.
    private val deviceContext = context.createDeviceProtectedStorageContext()
    private val prefs = run {
        val existing = deviceContext.getSharedPreferences("alarms_v1", Context.MODE_PRIVATE)
        val manager = context.getSystemService(android.os.UserManager::class.java)
        if (manager.isUserUnlocked && !existing.contains("items") &&
            context.getSharedPreferences("alarms_v1", Context.MODE_PRIVATE).contains("items")) {
            // One-time migration from credential-encrypted storage on upgraded installs.
            deviceContext.moveSharedPreferencesFrom(context, "alarms_v1")
        }
        deviceContext.getSharedPreferences("alarms_v1", Context.MODE_PRIVATE)
    }

    fun all(): List<AlarmSpec> = synchronized(storageLock) { try {
        val raw = JSONArray(prefs.getString("items", "[]") ?: "[]")
        (0 until raw.length()).mapNotNull { index ->
            try {
                val o = raw.getJSONObject(index)
                AlarmSpec(
                    id = o.getInt("id"), hour = o.getInt("hour"), minute = o.getInt("minute"),
                    label = o.optString("label", ""), daysMask = o.optInt("days", 0),
                    enabled = o.optBoolean("enabled", true),
                    useAlarmVolume = o.optBoolean("alarmVolume", true),
                    autoAdjustVolume = o.optBoolean("autoAdjust", false),
                    targetVolumePercent = o.optInt("targetVolume", 70),
                    restoreVolume = o.optBoolean("restoreVolume", true),
                    snoozeMinutes = o.optInt("snoozeMinutes", 5),
                    soundUri = o.optString("soundUri", "").takeIf { it.isNotEmpty() },
                    vibrate = o.optBoolean("vibrate", true),
                    fadeInSeconds = o.optInt("fadeInSeconds", 10),
                    absoluteTriggerMillis = if (o.has("absoluteTriggerMillis")) o.getLong("absoluteTriggerMillis") else null
                )
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() } }

    fun byId(id: Int): AlarmSpec? = all().firstOrNull { it.id == id }

    fun newId(): Int = synchronized(storageLock) {
        val next = prefs.getInt("next_id", 1)
        require(next in 1..999_999) { "Alarm ID space exhausted" }
        check(prefs.edit().putInt("next_id", next + 1).commit())
        next
    }

    /** Snooze deadlines live in device-protected storage to survive locked reboots. */
    fun snoozeUntil(id: Int): Long? = synchronized(storageLock) {
        prefs.getLong("snooze_$id", 0L).takeIf { it > 0L }
    }

    fun setSnoozeUntil(id: Int, whenMillis: Long) = synchronized(storageLock) {
        require(whenMillis > 0L)
        check(prefs.edit().putLong("snooze_$id", whenMillis).commit())
    }

    fun clearSnooze(id: Int) = synchronized(storageLock) {
        check(prefs.edit().remove("snooze_$id").commit())
    }

    fun put(alarm: AlarmSpec) = synchronized(storageLock) {
        val alarms = all().filterNot { it.id == alarm.id } + alarm
        write(alarms)
    }

    fun delete(id: Int) = synchronized(storageLock) {
        write(all().filterNot { it.id == id })
    }

    private fun write(alarms: List<AlarmSpec>) {
        val json = JSONArray()
        alarms.forEach { a ->
            json.put(JSONObject().apply {
                put("id", a.id); put("hour", a.hour); put("minute", a.minute)
                put("label", a.label); put("days", a.daysMask); put("enabled", a.enabled)
                put("alarmVolume", a.useAlarmVolume); put("autoAdjust", a.autoAdjustVolume)
                put("targetVolume", a.targetVolumePercent); put("restoreVolume", a.restoreVolume)
                put("snoozeMinutes", a.snoozeMinutes); put("soundUri", a.soundUri ?: "")
                put("vibrate", a.vibrate); put("fadeInSeconds", a.fadeInSeconds)
                a.absoluteTriggerMillis?.let { put("absoluteTriggerMillis", it) }
            })
        }
        check(prefs.edit().putString("items", json.toString()).commit())
    }
}
