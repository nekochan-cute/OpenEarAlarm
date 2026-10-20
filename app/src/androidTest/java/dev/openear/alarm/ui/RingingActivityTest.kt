// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.openear.alarm.audio.AlarmPlaybackService
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RingingActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun snoozeIsAvailableUntilLimitAndHiddenAfterwards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AlarmStore(context)
        val id = store.newId()
        try {
            store.put(AlarmSpec(id, 7, 45, label = "Ringing UI",
                snoozeLimit = 1, vibrate = false))
            val intent = Intent(context, RingingActivity::class.java)
                .putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<RingingActivity>(intent).use {
                compose.onNodeWithText("Ringing UI").assertIsDisplayed()
                compose.onNodeWithText("スヌーズ").assertIsDisplayed()
                compose.onNodeWithText("停止").assertIsDisplayed()
            }
            store.setSnoozeCount(id, 1)
            ActivityScenario.launch<RingingActivity>(intent).use {
                compose.onNodeWithText("スヌーズ回数の上限に達しました").assertIsDisplayed()
                compose.onNodeWithText("スヌーズ").assertDoesNotExist()
                compose.onNodeWithText("停止").assertIsDisplayed()
            }
        } finally {
            store.resetSnoozeCount(id)
            store.delete(id)
        }
    }
}
