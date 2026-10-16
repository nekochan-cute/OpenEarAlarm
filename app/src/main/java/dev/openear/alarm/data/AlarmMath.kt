// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure scheduling logic; resolves DST gaps to the next valid local time. */
object AlarmMath {
    fun nextTrigger(alarm: AlarmSpec, after: Instant, zone: ZoneId): Instant {
        alarm.absoluteTriggerMillis?.let {
            require(it > after.toEpochMilli()) { "Quick timer already elapsed" }
            return Instant.ofEpochMilli(it)
        }
        val now = after.atZone(zone)
        val time = LocalTime.of(alarm.hour, alarm.minute)
        for (daysAhead in 0..7) {
            val date: LocalDate = now.toLocalDate().plusDays(daysAhead.toLong())
            if (alarm.daysMask != 0) {
                val bit = 1 shl (date.dayOfWeek.value - 1)
                if (alarm.daysMask and bit == 0) continue
            }
            val local = LocalDateTime.of(date, time)
            val offsets = zone.rules.getValidOffsets(local)
            // Overlaps have two valid instants; choose the earliest one still in the future.
            // Gaps have none; java.time moves the local time to its next valid instant.
            val candidates = if (offsets.isEmpty()) listOf(local.atZone(zone).toInstant())
                else offsets.map { local.atOffset(it).toInstant() }
            candidates.filter { it > after }.minOrNull()?.let { return it }
        }
        error("No future occurrence found")
    }

    fun formatted(time: Instant, zone: ZoneId): String =
        time.atZone(zone).toLocalDateTime().toString()
}
