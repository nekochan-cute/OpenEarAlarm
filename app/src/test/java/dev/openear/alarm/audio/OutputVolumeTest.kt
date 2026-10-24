// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.audio

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutputVolumeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val stream = AudioManager.STREAM_ALARM
    private lateinit var alarm: AlarmSpec

    @Before fun setUp() {
        val store = AlarmStore(context)
        alarm = AlarmSpec(store.newId(), 7, 0, autoAdjustVolume = true, targetVolumePercent = 100, headphoneVolumePercent = 30, vibrate = false)
        store.put(alarm)
        shadowOf(audio).setStreamMaxVolume(10)
        audio.setStreamVolume(stream, 5, 0)
    }

    private fun outputs(vararg types: Int) {
        shadowOf(audio).setOutputDevices(types.map { AudioDeviceInfoBuilder.newBuilder().setType(it).build() })
    }

    private fun start() = Robolectric.buildService(AlarmPlaybackService::class.java,
        Intent().setAction(AlarmPlaybackService.ACTION_START).putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, alarm.id)).create().startCommand(0, 1)

    @Test fun speakerLevelWithoutHeadphonesAndRestoredOnStop() {
        outputs(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        val service = start()
        assertEquals(10, audio.getStreamVolume(stream))
        assertEquals(AlarmPlaybackService.Output.Speaker, AlarmPlaybackService.output.value)
        service.get().onStartCommand(Intent().setAction(AlarmPlaybackService.ACTION_STOP), 0, 2)
        assertEquals(5, audio.getStreamVolume(stream))
        assertEquals(null, AlarmPlaybackService.output.value)
    }

    @Test fun headphoneLevelWhenHeadphonesAreConnected() {
        outputs(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        start()
        assertEquals(3, audio.getStreamVolume(stream))
        assertEquals(AlarmPlaybackService.Output.Headphones, AlarmPlaybackService.output.value)
    }

    @Test fun speakerLevelOnceHeadphonesDisconnectWhileRinging() {
        // Let the shadow MediaPlayer accept the built-in tone so the route listeners are active.
        val tone = android.net.Uri.fromFile(BundledTone.getOrCreate(context.createDeviceProtectedStorageContext()))
        org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(org.robolectric.shadows.util.DataSource.toDataSource(context, tone),
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(2000, 0))
        val speaker = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()
        val headphones = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build()
        shadowOf(audio).setOutputDevices(listOf(speaker, headphones))
        start()
        assertEquals(3, audio.getStreamVolume(stream))
        shadowOf(audio).removeOutputDevice(headphones, true)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AlarmPlaybackService.Output.Speaker, AlarmPlaybackService.output.value)
        assertEquals(10, audio.getStreamVolume(stream))
    }

    @Test fun volumeIsLeftAloneWhenNotRequested() {
        AlarmStore(context).put(alarm.copy(autoAdjustVolume = false))
        outputs(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        start()
        assertEquals(5, audio.getStreamVolume(stream))
    }

    @Test fun alarmsSavedBeforeSeparateLevelsUseTheSameLevelForHeadphones() {
        val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("alarms_v1", Context.MODE_PRIVATE)
        prefs.edit().putString("items", """[{"id":42,"hour":6,"minute":0,"autoAdjust":true,"targetVolume":55}]""").commit()
        val old = AlarmStore(context).byId(42)!!
        assertEquals(55, old.targetVolumePercent)
        assertEquals(55, old.headphoneVolumePercent)
    }
}
