// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.openear.alarm.BuildConfig
import dev.openear.alarm.data.AlarmHistory
import dev.openear.alarm.data.AlarmMath
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import dev.openear.alarm.data.DefaultSettings
import dev.openear.alarm.scheduler.AlarmScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private enum class Page { Home, Edit, Settings, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { OpenEarTheme { App() } }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val store = remember { AlarmStore(context) }
    val defaults = remember { DefaultSettings(context) }
    val scheduler = remember { AlarmScheduler(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var alarms by remember { mutableStateOf(sorted(store.all())) }
    var page by remember { mutableStateOf(Page.Home) }
    var editing by remember { mutableStateOf<AlarmSpec?>(null) }
    var isNew by remember { mutableStateOf(false) }
    // Bumped on resume and after permission changes so status is read again.
    var refresh by remember { mutableIntStateOf(0) }
    var defaultValues by remember { mutableStateOf(defaults.values()) }

    fun reload() { alarms = sorted(store.all()); refresh++ }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun save(alarm: AlarmSpec) {
        val previous = store.byId(alarm.id)
        store.put(alarm)
        try {
            scheduler.update(alarm)
        } catch (failure: Exception) {
            // Keep the stored alarm in step with what the system has registered.
            if (previous == null) store.delete(alarm.id) else store.put(previous)
            if (previous != null) runCatching { scheduler.update(previous) }
            throw failure
        }
        reload()
        page = Page.Home
        if (alarm.enabled) runCatching { AlarmMath.nextTrigger(alarm, Instant.now(), ZoneId.systemDefault()) }.getOrNull()
            ?.let { untilLabel(Instant.now(), it).let { left -> message(if (left.startsWith("あと")) "${left}で鳴ります" else "${left}に鳴ります") } }
    }
    fun trySave(alarm: AlarmSpec) = try { save(alarm) } catch (e: Exception) { message("保存できませんでした: ${e.message}") }
    fun delete(alarm: AlarmSpec) {
        try {
            scheduler.cancel(alarm.id)
            store.delete(alarm.id)
            reload()
            page = Page.Home
            scope.launch {
                if (snackbar.showSnackbar("アラームを削除しました", "元に戻す") == SnackbarResult.ActionPerformed) trySave(alarm)
            }
        } catch (failure: Exception) {
            // A failed delete must not leave the alarm unscheduled.
            runCatching { scheduler.update(alarm) }
            message("削除できませんでした: ${failure.message}")
        }
    }
    fun edit(alarm: AlarmSpec, new: Boolean) { editing = alarm; isNew = new; page = Page.Edit }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        refresh++
    }
    val ready = remember(refresh) { readiness(context) }
    val fixes = Fixes(
        exact = { if (Build.VERSION.SDK_INT >= 31) open(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) },
        notifications = { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
        fullScreen = { if (Build.VERSION.SDK_INT >= 34) open(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) },
    )

    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(page != Page.Home) { page = if (page == Page.History) Page.Settings else Page.Home }

    when (page) {
        Page.Home -> {
            val now = Instant.now()
            val snoozes = remember(alarms, refresh) {
                alarms.mapNotNull { a -> store.snoozeUntil(a.id)?.takeIf { it > now.toEpochMilli() }?.let { a.id to Instant.ofEpochMilli(it) } }.toMap()
            }
            val next = (alarms.filter { it.enabled }.mapNotNull { runCatching { AlarmMath.nextTrigger(it, now, ZoneId.systemDefault()) }.getOrNull() } +
                snoozes.values).minOrNull()
            HomeScreen(HomeState(alarms, snoozes, next, now, problems(ready, fixes)), HomeActions(
                add = { LocalTime.now().plusMinutes(5).let { edit(defaults.newAlarm(store.newId(), it.hour, it.minute), true) } },
                edit = { edit(it, false) },
                toggle = { trySave(it.copy(enabled = !it.enabled)) },
                quick = { minutes ->
                    val at = ZonedDateTime.now().plusMinutes(minutes)
                    trySave(defaults.newAlarm(store.newId(), at.hour, at.minute)
                        .copy(label = "${minutes}分タイマー", absoluteTriggerMillis = at.toInstant().toEpochMilli()))
                },
                settings = { page = Page.Settings },
            ), snackbar)
        }
        Page.Edit -> editing?.let { alarm ->
            EditScreen(alarm, isNew, EditActions(back = { page = Page.Home }, save = ::trySave, delete = { delete(alarm) }))
        }
        Page.Settings -> SettingsScreen(defaultValues, ready, BuildConfig.VERSION_NAME, SettingsActions(
            back = { page = Page.Home },
            defaults = { defaults.save(it); defaultValues = defaults.values() },
            allowExact = fixes.exact, allowNotifications = fixes.notifications, allowFullScreen = fixes.fullScreen,
            history = { page = Page.History },
            source = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nekochan-cute/OpenEarAlarm"))) } },
        ))
        Page.History -> HistoryScreen(remember(refresh) { AlarmHistory.recent(context) }, alarms.associateBy { it.id }) { page = Page.Settings }
    }
}

private fun sorted(alarms: List<AlarmSpec>) = alarms.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))

private class Fixes(val exact: () -> Unit, val notifications: () -> Unit, val fullScreen: () -> Unit)

private fun problems(ready: Readiness, fixes: Fixes) = buildList {
    if (!ready.exact) add(Problem("正確な時刻に鳴らせません", "許可する", fixes.exact))
    if (!ready.notifications) add(Problem("通知がオフです", "許可する", fixes.notifications))
    if (!ready.fullScreen) add(Problem("ロック画面にアラームを表示できません", "許可する", fixes.fullScreen))
}

private fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action, Uri.parse("package:${context.packageName}"))) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
}

internal fun readiness(context: Context): Readiness {
    val audio = context.getSystemService(AudioManager::class.java)
    return Readiness(
        exact = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        notifications = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        fullScreen = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
        outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }.mapNotNull { outputName(it.type) }.distinct(),
    )
}

internal fun outputName(type: Int): String? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "本体スピーカー"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有線イヤホン"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetoothイヤホン"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LEイヤホン"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USBイヤホン"
    else -> null
}

private fun DefaultSettings.values() = Defaults(useAlarmVolume, autoAdjustVolume, targetVolumePercent, restoreVolume)
private fun DefaultSettings.save(d: Defaults) {
    useAlarmVolume = d.useAlarmVolume; autoAdjustVolume = d.autoAdjustVolume
    targetVolumePercent = d.targetVolumePercent; restoreVolume = d.restoreVolume
}
