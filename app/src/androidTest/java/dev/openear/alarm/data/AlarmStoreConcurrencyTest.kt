// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmStoreConcurrencyTest {
    @Test fun multipleInstancesDoNotLoseConcurrentUpdates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val executor = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val ids = (1..12).map { store.newId() }
        try {
            val futures = ids.map { id ->
                executor.submit {
                    start.await()
                    AlarmStore(context).put(AlarmSpec(id, 8, 15, label = "concurrent-$id"))
                }
            }
            start.countDown()
            futures.forEach { it.get() }
            val saved = AlarmStore(context).all().map { it.id }.toSet()
            assertEquals(ids.toSet(), saved.intersect(ids.toSet()))
        } finally {
            start.countDown()
            executor.shutdownNow()
            ids.forEach { store.delete(it) }
        }
    }
}
