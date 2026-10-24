// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.openear.alarm.audio.AlarmPlaybackService
import dev.openear.alarm.data.AlarmHistory
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import dev.openear.alarm.scheduler.AlarmScheduler
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the real screens with sample alarms to PNG for review. Runs only with `-Pscreenshots`. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule val compose = createEmptyComposeRule()
    private val dir = System.getProperty("screenshots.dir")?.let(::File)
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun theme() {
        assumeTrue(dir != null)
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
    }

    private fun seed() {
        // Alarms are fully set up in the samples; the empty screen keeps the warnings visible.
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.USE_FULL_SCREEN_INTENT)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val store = AlarmStore(context)
        val scheduler = AlarmScheduler(context)
        listOf(
            AlarmSpec(store.newId(), 6, 30, label = "平日", daysMask = 0b0011111, autoAdjustVolume = true),
            AlarmSpec(store.newId(), 7, 45, label = "", daysMask = 0b1100000),
            AlarmSpec(store.newId(), 13, 0, label = "昼寝", enabled = false),
            AlarmSpec(store.newId(), 23, 10, label = "薬を飲む", daysMask = 127),
        ).forEach { store.put(it); scheduler.update(it) }
        scheduler.scheduleSnooze(2, 5)
        listOf("アラーム時刻に到達", "音声再生を開始", "スヌーズ").forEach { AlarmHistory.record(context, 1, it) }
    }

    private fun save(name: String, activity: Activity) {
        compose.waitForIdle()
        val view = activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        dir!!.mkdirs()
        File(dir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun main(name: String, seeded: Boolean = true, navigate: () -> Unit = {}) {
        if (seeded) seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate()
            scenario.onActivity { save(name, it) }
        }
    }

    @Test fun empty() = main("01-home-empty", seeded = false)
    @Test fun home() = main("02-home")
    @Test fun editor() = main("03-editor") { compose.onNodeWithText("平日").performClick() }
    @Test fun settings() = main("04-settings") { compose.onNodeWithContentDescription("設定").performClick() }
    @Test fun history() = main("06-history") {
        compose.onNodeWithContentDescription("設定").performClick()
        compose.onNodeWithText("履歴").performClick()
    }

    @Test fun ringing() {
        seed()
        val intent = Intent(context, RingingActivity::class.java).putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, 1)
        ActivityScenario.launch<RingingActivity>(intent).use { scenario -> scenario.onActivity { save("05-ringing", it) } }
    }
}
