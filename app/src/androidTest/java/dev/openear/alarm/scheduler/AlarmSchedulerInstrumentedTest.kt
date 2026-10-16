// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.scheduler

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmSchedulerInstrumentedTest {
    @Test fun registersAndCancelsExactAlarmPendingIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val id = store.newId()
        val trigger = System.currentTimeMillis() + 120_000L
        val spec = AlarmSpec(id = id, hour = 0, minute = 0, absoluteTriggerMillis = trigger)
        fun pending(): PendingIntent? = PendingIntent.getBroadcast(
            context, id * 2,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                data = Uri.parse("openear://alarm/${id * 2}")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            store.put(spec)
            scheduler.update(spec)
            assertNotNull("Exact alarm registration should create a PendingIntent", pending())
            scheduler.cancel(id)
            assertNull("Cancelling the alarm should remove the PendingIntent", pending())
        } finally {
            scheduler.cancel(id)
            store.delete(id)
        }
    }
}
