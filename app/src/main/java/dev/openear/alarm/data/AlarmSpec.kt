// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

/** Bit 0 = Monday, bit 6 = Sunday; zero means one-shot. */
data class AlarmSpec(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val daysMask: Int = 0,
    val enabled: Boolean = true,
    val useAlarmVolume: Boolean = true,
    val autoAdjustVolume: Boolean = false,
    val targetVolumePercent: Int = 70,
    val restoreVolume: Boolean = true,
    val snoozeMinutes: Int = 5,
    /** Number of snoozes allowed per normal alarm occurrence; 0 means unlimited. */
    val snoozeLimit: Int = 3,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val fadeInSeconds: Int = 10,
    /** 0 = until explicitly stopped; otherwise minutes. */
    val autoStopMinutes: Int = 15,
    /** Absolute one-shot instant for relative quick timers; absent for wall-clock alarms. */
    val absoluteTriggerMillis: Long? = null
) {
    init {
        require(id in 1..999_999)
        require(hour in 0..23 && minute in 0..59)
        require(daysMask in 0..127)
        require(targetVolumePercent in 0..100)
        require(snoozeMinutes in 1..60)
        require(snoozeLimit in 0..10)
        require(fadeInSeconds in 0..60)
        require(autoStopMinutes in 0..60)
        require(absoluteTriggerMillis == null || (absoluteTriggerMillis > 0 && daysMask == 0))
    }
}
