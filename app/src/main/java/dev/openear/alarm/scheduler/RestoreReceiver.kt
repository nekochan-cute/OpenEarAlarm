// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            )) return
        val pending = goAsync()
        Thread {
            try { AlarmScheduler(context).rescheduleAll() }
            catch (e: Exception) { Log.e("RestoreReceiver", "Reschedule failed", e) }
            finally { pending.finish() }
        }.start()
    }
}
