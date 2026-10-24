// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import android.content.Context

/** Global defaults used only for new alarms; existing alarms retain their own settings. */
class DefaultSettings(context: Context) {
    private val prefs = context.getSharedPreferences("defaults_v1", Context.MODE_PRIVATE)

    var useAlarmVolume: Boolean
        get() = prefs.getBoolean("useAlarmVolume", true)
        set(value) { check(prefs.edit().putBoolean("useAlarmVolume", value).commit()) }
    var autoAdjustVolume: Boolean
        get() = prefs.getBoolean("autoAdjustVolume", false)
        set(value) { check(prefs.edit().putBoolean("autoAdjustVolume", value).commit()) }
    var targetVolumePercent: Int
        get() = prefs.getInt("targetVolumePercent", 70)
        set(value) { check(prefs.edit().putInt("targetVolumePercent", value.coerceIn(0, 100)).commit()) }
    var headphoneVolumePercent: Int
        get() = prefs.getInt("headphoneVolumePercent", 40)
        set(value) { check(prefs.edit().putInt("headphoneVolumePercent", value.coerceIn(0, 100)).commit()) }
    var restoreVolume: Boolean
        get() = prefs.getBoolean("restoreVolume", true)
        set(value) { check(prefs.edit().putBoolean("restoreVolume", value).commit()) }

    fun newAlarm(id: Int, hour: Int, minute: Int) = AlarmSpec(
        id = id, hour = hour, minute = minute,
        useAlarmVolume = useAlarmVolume,
        autoAdjustVolume = autoAdjustVolume,
        targetVolumePercent = targetVolumePercent,
        headphoneVolumePercent = headphoneVolumePercent,
        restoreVolume = restoreVolume
    )
}
