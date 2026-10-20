// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.openear.alarm.R
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import dev.openear.alarm.data.AlarmHistory
import dev.openear.alarm.scheduler.AlarmScheduler
import dev.openear.alarm.ui.RingingActivity
import kotlin.math.roundToInt

/** Runs only while an alarm is audibly active; never keeps a polling service alive. */
class AlarmPlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val activeIds = linkedSetOf<Int>()
    private lateinit var audio: AudioManager
    private lateinit var vibrator: Vibrator
    private var player: MediaPlayer? = null
    private var currentSpec: AlarmSpec? = null
    private var focus: AudioFocusRequest? = null
    private var modifiedStream: Int? = null
    private var previousVolume: Int? = null
    private var appliedVolume: Int? = null
    private var fade: Runnable? = null
    private var preferredId: Int? = null
    private var playbackGain = 0f
    private var hasAudioFocus = true
    private var triedPlaybackRecovery = false
    private val autoStop = Runnable { finishAlarm(snooze = false) }
    private val routingListener = AudioRouting.OnRoutingChangedListener { routeOutput() }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = routeOutput()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = routeOutput()
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        vibrator = getSystemService(Vibrator::class.java)
        ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getIntExtra(EXTRA_ALARM_ID, -1)
                val spec = AlarmStore(this).byId(id)
                    ?: return START_NOT_STICKY.also { if (activeIds.isEmpty()) stopSelf() }
                if (!activeIds.add(id)) return START_REDELIVER_INTENT
                // Android 15+ requires foreground state before obtaining audio focus.
                val alreadyRinging = currentSpec != null
                try {
                    promoteToForeground(spec)
                    if (!alreadyRinging) startPlayback(spec)
                    else updateNotification()
                } catch (e: Exception) {
                    Log.e(TAG, "Alarm start failed", e)
                    AlarmHistory.record(this, id, "鳴動開始失敗")
                    postLaunchFailure(this, spec)
                    if (alreadyRinging) {
                        // A broken second alarm must not stop an alarm that is already
                        // audible. Restore the original notification and keep playing.
                        activeIds.remove(id)
                        runCatching { updateNotification() }
                    } else {
                        finishAlarm(snooze = false)
                    }
                }
            }
            ACTION_STOP -> finishAlarm(snooze = false)
            ACTION_SNOOZE -> finishAlarm(snooze = true)
            else -> stopSelf()
        }
        // If Android kills the process while the alarm is ringing, request that
        // it redeliver the alarm-start intent. This is best effort only and
        // does not override a user force-stop or OEM background restrictions.
        return if (intent?.action == ACTION_START && activeIds.isNotEmpty())
            START_REDELIVER_INTENT else START_NOT_STICKY
    }

    private fun promoteToForeground(spec: AlarmSpec) {
        val n = notification(this, spec, activeIds.size)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIFICATION_ID, n)
    }

    private fun updateNotification() {
        val current = currentSpec ?: return
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID, notification(this, current, activeIds.size)
        )
    }

    private fun startPlayback(spec: AlarmSpec) {
        currentSpec = spec
        val stream = if (spec.useAlarmVolume) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
        val usage = if (spec.useAlarmVolume) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        try {
            audio.registerAudioDeviceCallback(devices, handler)
        } catch (failure: RuntimeException) {
            // Device callbacks improve routing diagnostics; they are not a
            // prerequisite for a wake-up alarm to emit sound.
            Log.w(TAG, "Device callback unavailable; continuing alarm", failure)
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        // The app's explicit wake-up-first policy must not mute the alarm
                        // permanently because another player holds transient focus.
                        // Device policy (including DND) can still override this.
                        Log.w(TAG, "Audio focus lost while ringing ($change); retaining alarm gain")
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasAudioFocus = true
                        player?.setVolume(playbackGain, playbackGain)
                    }
                }
            }
            .build()
        focus = request
        val focusResult = try {
            audio.requestAudioFocus(request)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Audio focus request failed; trying alarm playback anyway", failure)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        // When transient audio focus is denied, attempt playback anyway.
        // System DND and routing policies still control final audibility.
        hasAudioFocus = true
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus not granted ($focusResult); attempting alarm playback")
        }

        if (spec.autoAdjustVolume) {
            try {
                val max = audio.getStreamMaxVolume(stream)
                val old = audio.getStreamVolume(stream)
                val target = (max * spec.targetVolumePercent / 100f).roundToInt().coerceIn(0, max)
                if (old != target) {
                    audio.setStreamVolume(stream, target, 0)
                    modifiedStream = stream
                    previousVolume = old
                    appliedVolume = target
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "Audio volume change blocked or unsupported; continuing playback", e)
            }
        }

        val fallback = try { Uri.fromFile(BundledTone.getOrCreate(createDeviceProtectedStorageContext())) } catch (e: Exception) {
            Log.e(TAG, "Bundled alarm tone unavailable", e)
            null
        }
        val selected = spec.soundUri?.let(Uri::parse) ?: fallback
        player = selected?.let { createPlayer(it, attributes) }
            ?: fallback?.let { createPlayer(it, attributes) }
        if (player == null) {
            Log.e(TAG, "Both selected and bundled alarm sound failed")
            AlarmHistory.record(this, spec.id, "音声再生不可・振動のみ")
            // Preserve the vibration / alarm screen and surface notification; never crash.
        } else {
            player?.let { installPlaybackErrorHandler(it) }
            player?.addOnRoutingChangedListener(routingListener, handler)
            routeOutput()
            try {
                player?.start()
            } catch (failure: RuntimeException) {
                // Synchronous start errors do not invoke OnErrorListener.
                Log.e(TAG, "Starting the chosen audio player failed", failure)
                recoverPlaybackWithBuiltInTone()
            }
            if (player != null) {
                AlarmHistory.record(this, spec.id, "音声再生を開始")
                beginFadeIn(spec.fadeInSeconds)
            }
        }
        if (spec.vibrate) {
            try {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
            } catch (e: RuntimeException) { Log.w(TAG, "Vibration unavailable", e) }
        }
        handler.postDelayed(autoStop, 15 * 60_000L)
    }

    private fun createPlayer(uri: Uri, attr: AudioAttributes): MediaPlayer? {
        val candidate = MediaPlayer()
        return try {
            candidate.setAudioAttributes(attr)
            // Keep the CPU awake only while the MediaPlayer is actively playing.
            candidate.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            candidate.setDataSource(this, uri)
            candidate.isLooping = true
            candidate.prepare()
            candidate.setVolume(0f, 0f)
            candidate
        } catch (e: Exception) {
            Log.w(TAG, "Could not open alarm sound $uri", e)
            candidate.release()
            null
        }
    }

    /** Retry a decoder/output failure once using the offline built-in alarm tone.
     * The alarm must keep vibrating and retain its ringing notification even if
     * the second player also fails. All mutations occur on the service main thread.
     */
    private fun installPlaybackErrorHandler(candidate: MediaPlayer) {
        candidate.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
            handler.post {
                if (player === candidate) recoverPlaybackWithBuiltInTone()
            }
            true
        }
    }

    private fun recoverPlaybackWithBuiltInTone() {
        val spec = currentSpec ?: return
        val failed = player
        runCatching { failed?.removeOnRoutingChangedListener(routingListener) }
        runCatching { failed?.reset() }
        runCatching { failed?.release() }
        player = null
        preferredId = null
        if (triedPlaybackRecovery) {
            AlarmHistory.record(this, spec.id, "代替アラーム音の再生にも失敗")
            return
        }
        triedPlaybackRecovery = true
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(if (spec.useAlarmVolume) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val uri = Uri.fromFile(BundledTone.getOrCreate(createDeviceProtectedStorageContext()))
            val recovered = createPlayer(uri, attributes)
            if (recovered == null) {
                AlarmHistory.record(this, spec.id, "代替アラーム音を読み込めません")
                return
            }
            player = recovered
            installPlaybackErrorHandler(recovered)
            recovered.addOnRoutingChangedListener(routingListener, handler)
            routeOutput()
            recovered.start()
            recovered.setVolume(playbackGain, playbackGain)
            AlarmHistory.record(this, spec.id, "代替アラーム音で再生を復旧")
        } catch (failure: Exception) {
            Log.e(TAG, "Failed to recover alarm audio", failure)
            AlarmHistory.record(this, spec.id, "再生復旧に失敗・振動と通知を継続")
            runCatching { player?.release() }
            player = null
        }
    }

    /** Preference is advisory on Android; actual routing remains under the OS audio policy. */
    private fun routeOutput() {
        val p = player ?: return
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val headphones = outputs.firstOrNull { device ->
            device.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET
            )
        }
        val chosen = headphones ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (chosen != null && chosen.id != preferredId) {
            val accepted = p.setPreferredDevice(chosen)
            if (accepted) preferredId = chosen.id
            else Log.w(TAG, "Preferred device rejected: ${chosen.type}")
        }
        // Do not claim a guarantee from setPreferredDevice; routing can still change asynchronously.
        handler.postDelayed({
            val routed = player?.routedDevice
            if (routed != null && chosen != null && routed.id != chosen.id) {
                Log.w(TAG, "Actual route differs: requested=${chosen.type}, actual=${routed.type}")
            }
        }, 400L)
    }

    private fun beginFadeIn(seconds: Int) {
        fade?.let(handler::removeCallbacks)
        val start = android.os.SystemClock.uptimeMillis()
        val duration = seconds * 1000L
        val animation = object : Runnable {
            override fun run() {
                val fraction = if (duration == 0L) 1f else
                    ((android.os.SystemClock.uptimeMillis() - start).toFloat() / duration).coerceIn(0f, 1f)
                playbackGain = fraction
                if (hasAudioFocus) player?.setVolume(fraction, fraction)
                if (fraction < 1f) handler.postDelayed(this, 125L)
            }
        }
        fade = animation
        handler.post(animation)
    }

    private fun finishAlarm(snooze: Boolean) {
        activeIds.forEach { AlarmHistory.record(this, it, if (snooze) "スヌーズ" else "停止") }
        val store = AlarmStore(this)
        if (snooze) {
            activeIds.forEach { id ->
                val minutes = store.byId(id)?.snoozeMinutes ?: 5
                try { AlarmScheduler(this).scheduleSnooze(id, minutes) }
                catch (e: Exception) {
                    Log.e(TAG, "Could not snooze $id", e)
                    AlarmHistory.record(this, id, "スヌーズの予約失敗")
                    postSnoozeFailure(this, id)
                }
            }
        } else activeIds.forEach { AlarmScheduler(this).cancelSnooze(it) }
        activeIds.clear()
        releasePlayback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayback() {
        handler.removeCallbacks(autoStop)
        fade?.let(handler::removeCallbacks)
        fade = null
        try { audio.unregisterAudioDeviceCallback(devices) } catch (_: Exception) {}
        try { player?.removeOnRoutingChangedListener(routingListener) } catch (_: Exception) {}
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        preferredId = null
        triedPlaybackRecovery = false
        playbackGain = 0f
        hasAudioFocus = true
        vibrator.cancel()
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        val stream = modifiedStream
        if (stream != null && currentSpec?.restoreVolume == true) {
            try {
                // Preserve a volume the user adjusted manually while the alarm was ringing.
                if (audio.getStreamVolume(stream) == appliedVolume) {
                    previousVolume?.let { audio.setStreamVolume(stream, it, 0) }
                }
            } catch (e: SecurityException) { Log.w(TAG, "Restore volume restricted", e) }
        }
        modifiedStream = null
        previousVolume = null
        appliedVolume = null
        currentSpec = null
    }

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmPlaybackService"
        private const val CHANNEL_ID = "active_alarms_v1"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_ALARM_ID = "alarm_id"
        const val ACTION_START = "dev.openear.alarm.START"
        const val ACTION_STOP = "dev.openear.alarm.STOP"
        const val ACTION_SNOOZE = "dev.openear.alarm.SNOOZE"

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, context.getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.alarm_channel_desc)
                setSound(null, null) // Service owns sound playback; no double ringing.
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }

        private fun action(context: Context, requestCode: Int, name: String): PendingIntent {
            val intent = Intent(context, AlarmPlaybackService::class.java).setAction(name)
            return PendingIntent.getService(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun notification(context: Context, spec: AlarmSpec, count: Int): Notification {
            ensureChannel(context)
            val activity = PendingIntent.getActivity(
                context, 1, Intent(context, RingingActivity::class.java)
                    .putExtra(EXTRA_ALARM_ID, spec.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(spec.label.ifBlank { "アラーム鳴動中" })
                .setContentText(if (count > 1) "${count}件のアラームが鳴っています" else "停止またはスヌーズ")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(activity)
                .setFullScreenIntent(activity, true)
                .addAction(0, context.getString(R.string.alarm_snooze), action(context, 2, ACTION_SNOOZE))
                .addAction(0, context.getString(R.string.alarm_stop), action(context, 3, ACTION_STOP))
                .build()
        }

        private fun postSnoozeFailure(context: Context, id: Int) {
            ensureChannel(context)
            try {
                context.getSystemService(NotificationManager::class.java).notify(
                    300_000 + id,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("スヌーズを設定できませんでした")
                        .setContentText("アラームが停止しました。もう一度登録してください。")
                        .setAutoCancel(true)
                        .build()
                )
            } catch (failure: Exception) {
                Log.e(TAG, "Could not show snooze failure", failure)
            }
        }

        fun postLaunchFailure(context: Context, spec: AlarmSpec) {
            ensureChannel(context)
            try {
                context.getSystemService(NotificationManager::class.java).notify(
                    200_000 + spec.id,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("アラームを開始できませんでした")
                        .setContentText(spec.label.ifBlank { "%02d:%02d".format(spec.hour, spec.minute) })
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .build()
                )
            } catch (e: SecurityException) { Log.e(TAG, "Notifications unavailable", e) }
        }
    }
}
