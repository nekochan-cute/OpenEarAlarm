// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.openear.alarm.data.AlarmMath
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import dev.openear.alarm.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val store = AlarmStore(context)

    fun update(alarm: AlarmSpec) {
        cancel(alarm.id)
        if (!alarm.enabled) return
        val trigger = AlarmMath.nextTrigger(alarm, Instant.now(), ZoneId.systemDefault())
        val event = eventIntent(alarm.id, snoozed = false, trigger.toEpochMilli())
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.toEpochMilli(), openApp), event)
    }

    fun scheduleSnooze(id: Int, minutes: Int) {
        val whenMillis = System.currentTimeMillis() + minutes * 60_000L
        val event = eventIntent(id, snoozed = true, whenMillis)
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(whenMillis, openApp), event)
    }

    fun cancel(id: Int) {
        for (snoozed in listOf(false, true)) {
            val intent = eventIntent(id, snoozed, 0L)
            manager.cancel(intent)
            intent.cancel()
        }
    }

    fun cancelSnooze(id: Int) {
        val intent = eventIntent(id, true, 0L)
        manager.cancel(intent)
        intent.cancel()
    }

    /** Recover registrations from persisted data on boot, clock changes and upgrades. */
    fun rescheduleAll() {
        store.all().forEach { alarm ->
            if (alarm.enabled && alarm.absoluteTriggerMillis != null && alarm.absoluteTriggerMillis <= System.currentTimeMillis()) {
                store.put(alarm.copy(enabled = false))
                cancel(alarm.id)
            } else if (alarm.enabled) update(alarm) else cancel(alarm.id)
        }
    }

    private fun eventIntent(id: Int, snoozed: Boolean, timestamp: Long): PendingIntent {
        val requestCode = id * 2 + if (snoozed) 1 else 0
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = Uri.parse("openear://alarm/$requestCode")
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
            putExtra(AlarmReceiver.EXTRA_SNOOZED, snoozed)
            putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, timestamp)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
