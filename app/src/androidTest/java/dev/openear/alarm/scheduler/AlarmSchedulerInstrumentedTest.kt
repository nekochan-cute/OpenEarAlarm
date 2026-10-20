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
    @Test fun snoozeLimitIsEnforcedAndPersistsThroughSchedulerInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val id = store.newId()
        try {
            store.put(AlarmSpec(id, 6, 30, enabled = false, snoozeLimit = 1))
            org.junit.Assert.assertTrue(scheduler.scheduleSnooze(id, 5))
            org.junit.Assert.assertFalse(AlarmScheduler(context).scheduleSnooze(id, 5))
            org.junit.Assert.assertEquals(1, AlarmStore(context).snoozeCount(id))
            store.resetSnoozeCount(id)
            org.junit.Assert.assertTrue(scheduler.scheduleSnooze(id, 5))
        } finally {
            scheduler.cancel(id)
            store.resetSnoozeCount(id)
            store.delete(id)
        }
    }

    @Test fun snoozeSurvivesReschedulingIncludingDisabledOneShot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val id = store.newId()
        val spec = AlarmSpec(id = id, hour = 7, minute = 0, enabled = false)
        try {
            store.put(spec)
            scheduler.scheduleSnooze(id, 5)
            val first = store.snoozeUntil(id)
            assertNotNull(first)
            scheduler.rescheduleAll()
            org.junit.Assert.assertEquals(first, store.snoozeUntil(id))
            val event = PendingIntent.getBroadcast(
                context, id * 2 + 1,
                Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_FIRE
                    data = Uri.parse("openear://alarm/${id * 2 + 1}")
                },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            assertNotNull("Snooze must be scheduled even when one-shot is disabled", event)
            scheduler.cancelSnooze(id)
            assertNull(store.snoozeUntil(id))
        } finally {
            scheduler.cancel(id)
            store.delete(id)
        }
    }

    @Test fun reschedulingRepeatingAlarmDoesNotDiscardPendingSnooze() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val id = store.newId()
        val spec = AlarmSpec(id = id, hour = 7, minute = 0, daysMask = 127)
        try {
            store.put(spec)
            scheduler.update(spec)
            scheduler.scheduleSnooze(id, 5)
            val previous = store.snoozeUntil(id)
            assertNotNull(previous)
            scheduler.update(spec, preserveSnooze = true)
            org.junit.Assert.assertEquals(previous, store.snoozeUntil(id))
            scheduler.rescheduleAll()
            org.junit.Assert.assertEquals(previous, store.snoozeUntil(id))
        } finally {
            scheduler.cancel(id)
            store.delete(id)
        }
    }

    @Test fun invalidReplacementDoesNotCancelExistingRegistration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        val id = store.newId()
        fun pending() = PendingIntent.getBroadcast(
            context, id * 2,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                data = Uri.parse("openear://alarm/${id * 2}")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val valid = AlarmSpec(id = id, hour = 0, minute = 0,
                absoluteTriggerMillis = System.currentTimeMillis() + 120_000L)
            store.put(valid)
            scheduler.update(valid)
            assertNotNull("Existing alarm must be registered", pending())
            val bad = valid.copy(absoluteTriggerMillis = System.currentTimeMillis() - 1000L)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                scheduler.update(bad)
            }
            assertNotNull("Invalid replacement must not erase existing scheduled alarm", pending())
        } finally {
            scheduler.cancel(id)
            store.delete(id)
        }
    }

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
