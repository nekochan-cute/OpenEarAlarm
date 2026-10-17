// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.scheduler

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.openear.alarm.audio.AlarmPlaybackService
import dev.openear.alarm.data.AlarmHistory
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end emulator smoke test: an actual AlarmManager clock dispatch, not manual BroadcastReceiver invocation. */
@RunWith(AndroidJUnit4::class)
class AlarmFiringInstrumentedTest {
    @Test fun exactAlarmActuallyFiresAndStartsPlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val id = store.newId()
        try {
            val trigger = System.currentTimeMillis() + 10_000L
            val spec = AlarmSpec(
                id = id,
                hour = 0,
                minute = 0,
                label = "End-to-end smoke test",
                absoluteTriggerMillis = trigger,
                fadeInSeconds = 0,
                vibrate = false
            )
            store.put(spec)
            scheduler.update(spec)
            val deadline = SystemClock.elapsedRealtime() + 45_000L
            var received = false
            var playbackStarted = false
            while (SystemClock.elapsedRealtime() < deadline) {
                val history = AlarmHistory.recent(context).filter { it.id == id }
                received = received || history.any { it.action == "アラーム時刻に到達" }
                playbackStarted = playbackStarted || history.any { it.action == "音声再生を開始" }
                if (received && playbackStarted) break
                Thread.sleep(250L)
            }
            assertTrue("Android must dispatch the actual scheduled alarm", received)
            assertTrue("The service must initialize and start playback", playbackStarted)
        } finally {
            runCatching {
                context.startService(Intent(context, AlarmPlaybackService::class.java)
                    .setAction(AlarmPlaybackService.ACTION_STOP))
            }
            scheduler.cancel(id)
            store.delete(id)
        }
    }
}
