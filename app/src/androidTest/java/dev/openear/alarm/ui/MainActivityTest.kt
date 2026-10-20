// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI regression tests run on an actual Android emulator, not only in a JVM. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun opensHomeAndSettingsWithoutCrashing() {
        rule.onNodeWithText("クイックタイマー").assertIsDisplayed()
        saveScreenshot("openear-home.png")
        rule.onNodeWithContentDescription("設定").performClick()
        rule.onNodeWithText("新しいアラームの初期値").assertIsDisplayed()
        rule.onNodeWithText("A · アラーム音量を使用").assertIsDisplayed()
        rule.onNodeWithText("B · 音量を自動調整").assertIsDisplayed()
        saveScreenshot("openear-settings.png")
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithText("クイックタイマー").assertIsDisplayed()
    }

    @Test fun opensAlarmEditorWithoutCrashing() {
        rule.onNodeWithContentDescription("アラームを追加").performClick()
        rule.onNodeWithText("アラームを追加").assertIsDisplayed()
        rule.onNodeWithText("アラーム名（任意）").assertIsDisplayed()
        rule.onNodeWithText("A · アラーム音量を使用").assertExists()
        rule.onNodeWithText("B · 音量を自動調整").assertExists()
        // Day chips must stay on a single line instead of wrapping Sunday.
        val monday = rule.onNodeWithText("月").fetchSemanticsNode().boundsInRoot.top
        val sunday = rule.onNodeWithText("日").fetchSemanticsNode().boundsInRoot.top
        assertEquals("Weekday chips should be a single row", monday, sunday, 2f)
        saveScreenshot("openear-editor.png")
    }

    @Test fun pendingSnoozeOfDisabledOneShotStillAppearsAsNextAlarm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = dev.openear.alarm.data.AlarmStore(context)
        val scheduler = dev.openear.alarm.scheduler.AlarmScheduler(context)
        val id = store.newId()
        try {
            store.put(dev.openear.alarm.data.AlarmSpec(
                id = id, hour = 6, minute = 30, label = "One-shot snooze test",
                enabled = false
            ))
            scheduler.scheduleSnooze(id, 5)
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText("One-shot snooze test").assertIsDisplayed()
            rule.onNodeWithText("スヌーズ中", substring = true).assertIsDisplayed()
            rule.onNodeWithText("次のアラーム").assertIsDisplayed()
        } finally {
            scheduler.cancel(id)
            store.delete(id)
        }
    }

    @Test fun createsAndDeletesAlarmThroughUi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = dev.openear.alarm.data.AlarmStore(context)
        val label = "OpenEar UI test " + System.nanoTime()
        try {
            rule.onNodeWithContentDescription("アラームを追加").performClick()
            rule.onNodeWithText("アラーム名（任意）").performTextInput(label)
            rule.onNodeWithText("保存").performClick()
            rule.onNodeWithText(label).assertIsDisplayed()
            saveScreenshot("openear-alarm-list.png")
            rule.onNodeWithText(label).performClick()
            rule.onNodeWithText("アラームを削除").performScrollTo().performClick()
            rule.onNodeWithText("削除").performClick()
            rule.onNodeWithText("アラームがありません").assertIsDisplayed()
        } finally {
            store.all().filter { it.label == label }.forEach { spec ->
                runCatching { dev.openear.alarm.scheduler.AlarmScheduler(context).cancel(spec.id) }
                store.delete(spec.id)
            }
        }
    }

    /** Capture Compose pixels directly: avoids unrelated emulator Launcher ANR dialogs. */
    private fun saveScreenshot(name: String) {
        // Android 9 lacks scoped MediaStore screenshots; UI assertions still run.
        if (Build.VERSION.SDK_INT < 29) return
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OpenEarAlarm")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "Could not create screenshot export"
        }
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { stream ->
                check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }
}
