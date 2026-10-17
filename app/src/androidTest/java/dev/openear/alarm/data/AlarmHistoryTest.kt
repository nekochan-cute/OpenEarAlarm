// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmHistoryTest {
    @Test fun eventHistoryStoredInDeviceProtectedStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = "test-event-" + System.nanoTime()
        AlarmHistory.record(context, 1, marker)
        val found = AlarmHistory.recent(context).firstOrNull { it.action == marker }
        assertTrue("History must survive process restart through device storage", found != null)
        assertEquals(1, found?.id)
    }
}
