// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmStoreInstrumentedTest {
    @Test fun alarmsPersistToDeviceProtectedStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val id = store.newId()
        try {
            store.put(AlarmSpec(id = id, hour = 8, minute = 30, label = "Direct Boot check"))
            val deviceContext = context.createDeviceProtectedStorageContext()
            val raw = deviceContext.getSharedPreferences("alarms_v1", Context.MODE_PRIVATE)
                .getString("items", "") ?: ""
            assertTrue("Alarms must be in device-protected storage", raw.contains("\"id\":$id"))
        } finally {
            store.delete(id)
        }
    }
}
