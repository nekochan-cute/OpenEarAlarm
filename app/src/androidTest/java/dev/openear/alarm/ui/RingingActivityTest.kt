// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
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
                compose.onNodeWithText("スヌーズ", substring = true).assertIsDisplayed()
                compose.onNodeWithText("止める").assertIsDisplayed()
                saveScreenshot("openear-ringing.png")
            }
            store.setSnoozeCount(id, 1)
            ActivityScenario.launch<RingingActivity>(intent).use {
                compose.onNodeWithText("スヌーズは使い切りました").assertIsDisplayed()
                compose.onNodeWithText("スヌーズ  ", substring = true).assertDoesNotExist()
                compose.onNodeWithText("止める").assertIsDisplayed()
                saveScreenshot("openear-ringing-limit.png")
            }
        } finally {
            store.resetSnoozeCount(id)
            store.delete(id)
        }
    }
    private fun saveScreenshot(name: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OpenEarAlarm")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { stream ->
                check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }
}
