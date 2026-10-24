// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val DAYS = listOf("月", "火", "水", "木", "金", "土", "日")

internal fun time(hour: Int, minute: Int) = "%d:%02d".format(Locale.ROOT, hour, minute)

internal fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).let { time(it.hour, it.minute) }

/** Bit 0 = Monday … bit 6 = Sunday, as in [dev.openear.alarm.data.AlarmSpec.daysMask]. */
internal fun repeatLabel(mask: Int): String = when (mask) {
    0 -> "1回のみ"
    127 -> "毎日"
    0b0011111 -> "平日"
    0b1100000 -> "週末"
    else -> DAYS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString("・")
}

/** "あと7時間12分"; rounds up so an alarm never looks earlier than it is. */
internal fun untilLabel(from: Instant, to: Instant): String {
    val minutes = (Duration.between(from, to).seconds.coerceAtLeast(0) + 59) / 60
    val days = minutes / (24 * 60); val hours = minutes / 60 % 24; val rest = minutes % 60
    return when {
        minutes <= 1 -> "1分以内"
        days > 0 -> "あと${days}日${if (hours > 0) "${hours}時間" else ""}"
        hours > 0 -> "あと${hours}時間${if (rest > 0) "${rest}分" else ""}"
        else -> "あと${rest}分"
    }
}

internal fun dateLabel(instant: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = instant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (day) {
        today -> "今日"
        today.plusDays(1) -> "明日"
        else -> day.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPAN))
    }
}
