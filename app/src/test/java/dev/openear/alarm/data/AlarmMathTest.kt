// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmMathTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private fun instant(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, tokyo).toInstant()

    @Test fun futureToday() {
        assertEquals(instant(2026, 10, 16, 7, 30),
            AlarmMath.nextTrigger(AlarmSpec(1, 7, 30), instant(2026, 10, 16, 6, 0), tokyo))
    }

    @Test fun tomorrowWhenTimeAlreadyPassed() {
        assertEquals(instant(2026, 10, 17, 7, 30),
            AlarmMath.nextTrigger(AlarmSpec(1, 7, 30), instant(2026, 10, 16, 8, 0), tokyo))
    }

    @Test fun weeklySkipsToSelectedDay() {
        // Oct 16 2026 is a Friday; Monday is bit 0.
        assertEquals(instant(2026, 10, 19, 9, 0),
            AlarmMath.nextTrigger(AlarmSpec(1, 9, 0, daysMask = 1), instant(2026, 10, 16, 10, 0), tokyo))
    }

    @Test fun weeklyExactlyAtTriggerSchedulesNextWeek() {
        assertEquals(instant(2026, 10, 23, 9, 0),
            AlarmMath.nextTrigger(AlarmSpec(1, 9, 0, daysMask = 1 shl 4), instant(2026, 10, 16, 9, 0), tokyo))
    }

    @Test fun springDstGapUsesNextValidTime() {
        val ny = ZoneId.of("America/New_York")
        val before = Instant.parse("2026-03-08T06:00:00Z")
        val next = AlarmMath.nextTrigger(AlarmSpec(1, 2, 30), before, ny)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), next)
    }

    @Test fun autumnOverlapSelectsSecondOccurrenceIfFirstAlreadyPassed() {
        val ny = ZoneId.of("America/New_York")
        val after = Instant.parse("2026-11-01T05:45:00Z")
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"),
            AlarmMath.nextTrigger(AlarmSpec(1, 1, 30), after, ny))
    }

    @Test fun exactQuickTimerDoesNotRoundToMinute() {
        val deadline = Instant.parse("2026-10-17T01:10:37Z")
        val timer = AlarmSpec(1, 10, 10, absoluteTriggerMillis = deadline.toEpochMilli())
        assertEquals(deadline, AlarmMath.nextTrigger(timer, deadline.minusSeconds(300), tokyo))
    }

    @Test(expected = IllegalArgumentException::class)
    fun quickTimerInPastIsRejected() {
        val deadline = Instant.parse("2026-10-17T01:10:37Z")
        AlarmMath.nextTrigger(AlarmSpec(1, 10, 10, absoluteTriggerMillis = deadline.toEpochMilli()), deadline, tokyo)
    }

    @Test fun acrossMidnight() {
        assertEquals(instant(2026, 10, 17, 0, 5),
            AlarmMath.nextTrigger(AlarmSpec(1, 0, 5), instant(2026, 10, 16, 23, 59), tokyo))
    }

    @Test fun defaultPoliciesAreIndependent() {
        val a = AlarmSpec(id = 1, hour = 7, minute = 0)
        assertTrue(a.useAlarmVolume)
        assertEquals(false, a.autoAdjustVolume)
        val b = a.copy(useAlarmVolume = false, autoAdjustVolume = true)
        assertEquals(false, b.useAlarmVolume)
        assertTrue(b.autoAdjustVolume)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHourRejected() { AlarmSpec(id = 1, hour = 24, minute = 0) }
}
