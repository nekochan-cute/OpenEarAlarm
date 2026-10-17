// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.openear.alarm.audio.AlarmPlaybackService
import dev.openear.alarm.data.AlarmStore
import dev.openear.alarm.data.AlarmHistory

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val store = AlarmStore(context)
        val alarm = store.byId(id) ?: return
        val snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)
        if (!snoozed && !alarm.enabled) return
        if (snoozed) {
            // Suppress a superseded snooze event after an edit or cancellation.
            val due = store.snoozeUntil(id) ?: return
            if (due != intent.getLongExtra(EXTRA_TRIGGER_AT, -1L)) return
            try { AlarmScheduler(context).cancelSnooze(id) }
            catch (e: Exception) { Log.e(TAG, "Unable to acknowledge snooze", e) }
        }

        // Schedule the next occurrence first, before any audio initialization can fail.
        if (!snoozed) {
            try {
                if (alarm.daysMask == 0) store.put(alarm.copy(enabled = false))
                else AlarmScheduler(context).update(alarm)
            } catch (e: Exception) { Log.e(TAG, "Failed to update recurrence", e) }
        }
        AlarmHistory.record(context, id, if (snoozed) "スヌーズ時刻に到達" else "アラーム時刻に到達")
        val service = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_START
            putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, id)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service)
            else context.startService(service)
        } catch (e: Exception) {
            Log.e(TAG, "Foreground service launch failed", e)
            AlarmHistory.record(context, id, "再生サービスの起動失敗")
            AlarmPlaybackService.postLaunchFailure(context, alarm)
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_FIRE = "dev.openear.alarm.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZED = "snoozed"
        const val EXTRA_TRIGGER_AT = "trigger_at"
    }
}
