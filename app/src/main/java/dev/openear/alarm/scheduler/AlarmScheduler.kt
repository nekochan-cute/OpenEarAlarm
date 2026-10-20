// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.openear.alarm.data.AlarmHistory
import dev.openear.alarm.data.AlarmMath
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import dev.openear.alarm.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val store = AlarmStore(context)

    fun update(alarm: AlarmSpec, preserveSnooze: Boolean = false) {
        cancelRegular(alarm.id)
        if (!preserveSnooze) {
            cancelSnooze(alarm.id)
            store.resetSnoozeCount(alarm.id)
        }
        if (!alarm.enabled) return
        val trigger = AlarmMath.nextTrigger(alarm, Instant.now(), ZoneId.systemDefault())
        val event = eventIntent(alarm.id, snoozed = false, trigger.toEpochMilli())
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.toEpochMilli(), openApp), event)
    }

    fun scheduleSnooze(id: Int, minutes: Int): Boolean {
        require(minutes in 1..60)
        val alarm = store.byId(id) ?: return false
        val count = store.snoozeCount(id)
        if (alarm.snoozeLimit > 0 && count >= alarm.snoozeLimit) return false
        // Persist the count before registration, so a reboot cannot reset it.
        store.setSnoozeCount(id, count + 1)
        try {
            scheduleSnoozeAt(id, System.currentTimeMillis() + minutes * 60_000L)
        } catch (failure: Exception) {
            store.setSnoozeCount(id, count)
            throw failure
        }
        return true
    }

    private fun scheduleSnoozeAt(id: Int, whenMillis: Long) {
        // Save BEFORE registering so a reboot in this window can still restore it.
        store.setSnoozeUntil(id, whenMillis)
        try {
            val event = eventIntent(id, snoozed = true, whenMillis)
            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(whenMillis, openApp), event)
        } catch (failure: Exception) {
            store.clearSnooze(id)
            throw failure
        }
    }

    private fun cancelRegular(id: Int) {
        val intent = eventIntent(id, false, 0L)
        manager.cancel(intent)
        intent.cancel()
    }

    fun cancel(id: Int) {
        cancelRegular(id)
        cancelSnooze(id)
        store.resetSnoozeCount(id)
    }

    fun cancelSnooze(id: Int) {
        val intent = eventIntent(id, true, 0L)
        manager.cancel(intent)
        intent.cancel()
        store.clearSnooze(id)
    }

    /** Recover registrations from persisted data on boot, clock changes and upgrades. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        store.all().forEach { alarm ->
            try {
                val snoozeAt = store.snoozeUntil(alarm.id)
                if (alarm.enabled && alarm.absoluteTriggerMillis != null && alarm.absoluteTriggerMillis <= now) {
                    store.put(alarm.copy(enabled = false))
                    cancelRegular(alarm.id)
                } else if (alarm.enabled) update(alarm, preserveSnooze = true)
                else cancelRegular(alarm.id)

                // A one-shot alarm may already be disabled after firing,
                // but any outstanding snooze still needs to survive reboot.
                if (snoozeAt != null) {
                    if (snoozeAt >= now) {
                        scheduleSnoozeAt(alarm.id, snoozeAt)
                    } else if (now - snoozeAt <= MISSED_SNOOZE_GRACE_MS) {
                        scheduleSnoozeAt(alarm.id, now + 2_000L)
                    } else {
                        cancelSnooze(alarm.id)
                    }
                }
            } catch (failure: Exception) {
                // One corrupt or unschedulable alarm must not block every other alarm.
                Log.e("AlarmScheduler", "Failed to reschedule alarm ${alarm.id}", failure)
                AlarmHistory.record(context, alarm.id, "再登録に失敗")
            }
        }
    }

    companion object {
        /** Best-effort wake-up if reboot finished shortly after a snooze deadline. */
        const val MISSED_SNOOZE_GRACE_MS = 15 * 60_000L
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
