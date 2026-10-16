// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI regression tests run on an actual Android emulator, not only in a JVM. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun opensHomeAndSettingsWithoutCrashing() {
        rule.onNodeWithText("クイックタイマー").assertIsDisplayed()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "openear-home.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithContentDescription("設定").performClick()
        rule.onNodeWithText("新しいアラームの初期値").assertIsDisplayed()
        rule.onNodeWithText("A · アラーム音量を使用").assertIsDisplayed()
        rule.onNodeWithText("B · 音量を自動調整").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithText("クイックタイマー").assertIsDisplayed()
    }

    @Test fun opensAlarmEditorWithoutCrashing() {
        rule.onNodeWithText("追加").performClick()
        rule.onNodeWithText("アラームを追加").assertIsDisplayed()
        rule.onNodeWithText("アラーム名（任意）").assertIsDisplayed()
        rule.onNodeWithText("A · アラーム音量を使用").assertExists()
        rule.onNodeWithText("B · 音量を自動調整").assertExists()
    }
}
