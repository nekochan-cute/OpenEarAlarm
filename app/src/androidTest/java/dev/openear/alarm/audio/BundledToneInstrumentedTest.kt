// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.audio

import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledToneInstrumentedTest {
    @Test fun generatedFallbackIsDecodableByAndroidMediaPlayer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
            .createDeviceProtectedStorageContext()
        val file = BundledTone.getOrCreate(context)
        assertTrue("Fallback file is too small", file.length() > 44L)
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            assertTrue("Fallback alarm sound should last about 2 seconds", player.duration in 1800..2200)
        } finally {
            player.release()
        }
    }
}
