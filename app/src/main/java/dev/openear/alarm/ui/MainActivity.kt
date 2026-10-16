// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.openear.alarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.openear.alarm.data.AlarmMath
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import dev.openear.alarm.data.DefaultSettings
import dev.openear.alarm.scheduler.AlarmScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class Page { Home, Edit, Settings, Diagnostics }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val store = remember { AlarmStore(context) }
            val defaults = remember { DefaultSettings(context) }
            val scheduler = remember { AlarmScheduler(context) }
            val alarms = remember { mutableStateListOf<AlarmSpec>().apply { addAll(store.all()) } }
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var page by remember { mutableStateOf(Page.Home) }
            var editing by remember { mutableStateOf<AlarmSpec?>(null) }
            var isNew by remember { mutableStateOf(false) }
            var defaultsVersion by remember { mutableIntStateOf(0) }

            fun reload() { alarms.clear(); alarms.addAll(store.all().sortedWith(compareBy({ it.hour }, { it.minute }))) }
            fun save(alarm: AlarmSpec) {
                val previous = store.byId(alarm.id)
                store.put(alarm)
                try {
                    scheduler.update(alarm)
                } catch (failure: Exception) {
                    // Keep persisted state aligned with the OS alarm registration on failure.
                    if (previous == null) store.delete(alarm.id) else store.put(previous)
                    if (previous != null) runCatching { scheduler.update(previous) }
                    throw failure
                }
                reload()
                page = Page.Home
            }
            fun quickAlarm(minutes: Long) {
                val time = java.time.ZonedDateTime.now().plusMinutes(minutes)
                val alarm = defaults.newAlarm(store.newId(), time.hour, time.minute)
                    .copy(label = "${minutes}分タイマー", absoluteTriggerMillis = time.toInstant().toEpochMilli())
                try { save(alarm) }
                catch (e: Exception) { scope.launch { snackbar.showSnackbar("登録できませんでした: ${e.message}") } }
            }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) reload()
                }
                this@MainActivity.lifecycle.addObserver(observer)
                onDispose { this@MainActivity.lifecycle.removeObserver(observer) }
            }
            BackHandler(page != Page.Home) {
                page = if (page == Page.Diagnostics) Page.Settings else Page.Home
            }
            fun newAlarm() {
                val now = LocalTime.now().plusMinutes(5)
                editing = defaults.newAlarm(store.newId(), now.hour, now.minute)
                isNew = true
                page = Page.Edit
            }

            OpenEarTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(when (page) {
                                Page.Home -> "アラーム"
                                Page.Edit -> if (isNew) "アラームを追加" else "アラームを編集"
                                Page.Settings -> "設定"
                                Page.Diagnostics -> "診断"
                            }) },
                            navigationIcon = {
                                if (page != Page.Home) IconButton(onClick = { page = if (page == Page.Diagnostics) Page.Settings else Page.Home }) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "戻る")
                                }
                            },
                            actions = {
                                if (page == Page.Home) IconButton(onClick = { page = Page.Settings }) {
                                    Icon(Icons.Rounded.Settings, contentDescription = "設定")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        if (page == Page.Home) {
                            ExtendedFloatingActionButton(
                                onClick = ::newAlarm,
                                modifier = Modifier.semantics { contentDescription = "アラームを追加" },
                                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                text = { Text("追加") }
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    when (page) {
                        Page.Home -> AlarmHome(
                            alarms = alarms,
                            modifier = Modifier.padding(padding),
                            onNew = ::newAlarm,
                            onQuick = ::quickAlarm,
                            onEdit = { editing = it; isNew = false; page = Page.Edit },
                            onToggle = { a ->
                                try { save(a.copy(enabled = !a.enabled)) }
                                catch (e: Exception) { scope.launch { snackbar.showSnackbar("保存できませんでした: ${e.message}") } }
                            }
                        )
                        Page.Edit -> editing?.let { spec ->
                            AlarmEdit(
                                alarm = spec,
                                isNew = isNew,
                                modifier = Modifier.padding(padding),
                                onSave = {
                                    try { save(it) }
                                    catch (e: Exception) { scope.launch { snackbar.showSnackbar("登録できませんでした: ${e.message}") } }
                                },
                                onDelete = {
                                    try {
                                        scheduler.cancel(spec.id)
                                        store.delete(spec.id)
                                        reload()
                                        page = Page.Home
                                        scope.launch {
                                            if (snackbar.showSnackbar("アラームを削除しました", "元に戻す") == SnackbarResult.ActionPerformed) {
                                                try { save(spec) }
                                                catch (failure: Exception) {
                                                    snackbar.showSnackbar("復元できませんでした: ${failure.message}")
                                                }
                                            }
                                        }
                                    } catch (failure: Exception) {
                                        // A failed persistence commit must not silently unschedule an alarm.
                                        runCatching { scheduler.update(spec) }
                                        scope.launch { snackbar.showSnackbar("削除できませんでした: ${failure.message}") }
                                    }
                                }
                            )
                        }
                        Page.Settings -> GlobalSettings(
                            defaults = defaults, version = defaultsVersion,
                            modifier = Modifier.padding(padding),
                            onChange = { defaultsVersion++ },
                            onDiagnostics = { page = Page.Diagnostics }
                        )
                        Page.Diagnostics -> Diagnostics(modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
internal fun OpenEarTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun AlarmHome(
    alarms: List<AlarmSpec>, modifier: Modifier = Modifier,
    onNew: () -> Unit, onQuick: (Long) -> Unit,
    onEdit: (AlarmSpec) -> Unit, onToggle: (AlarmSpec) -> Unit
) {
    val context = LocalContext.current
    val notificationBlocked = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val next = remember(alarms.toList()) {
        val now = Instant.now()
        alarms.filter { it.enabled }.mapNotNull { alarm ->
            runCatching { alarm to AlarmMath.nextTrigger(alarm, now, ZoneId.systemDefault()) }.getOrNull()
        }.minByOrNull { it.second }?.first
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 106.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (notificationBlocked) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("通知が無効です。鳴動画面を表示できない可能性があります。設定→診断で確認してください。",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("次のアラーム", style = MaterialTheme.typography.labelLarge)
                    Text(next?.let {
                        val instant = AlarmMath.nextTrigger(it, Instant.now(), ZoneId.systemDefault())
                        instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    } ?: "設定されていません",
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(next?.let { a ->
                        val instant = AlarmMath.nextTrigger(a, Instant.now(), ZoneId.systemDefault())
                        "${instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 EEEE"))} に鳴ります"
                    } ?: "＋からアラームを追加できます", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("クイックタイマー", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10L, 20L, 30L).forEach { minutes ->
                    OutlinedButton(onClick = { onQuick(minutes) }, modifier = Modifier.weight(1f)) {
                        Text("${minutes}分")
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text("登録済み", style = MaterialTheme.typography.titleMedium)
        }
        if (alarms.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(16.dp))
                    Text("アラームがありません", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Text("追加ボタンから最初のアラームを設定してください", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = onNew) { Text("アラームを追加") }
                }
            }
        } else items(alarms, key = { it.id }) { alarm ->
            ElevatedCard(onClick = { onEdit(alarm) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("%02d:%02d".format(alarm.hour, alarm.minute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(alarm.label.ifBlank { repeatLabel(alarm.daysMask) },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (alarm.label.isNotBlank()) Text(repeatLabel(alarm.daysMask), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = alarm.enabled, onCheckedChange = { onToggle(alarm) },
                        modifier = Modifier.semantics { contentDescription = "${alarm.hour}時${alarm.minute}分のアラーム切替" })
                }
            }
        }
    }
}

private fun repeatLabel(mask: Int): String {
    if (mask == 0) return "1回のみ"
    if (mask == 127) return "毎日"
    return "月火水木金土日".filterIndexed { index, _ -> mask and (1 shl index) != 0 }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AlarmEdit(
    alarm: AlarmSpec, isNew: Boolean, modifier: Modifier = Modifier,
    onSave: (AlarmSpec) -> Unit, onDelete: () -> Unit
) {
    val context = LocalContext.current
    var hour by remember(alarm.id) { mutableIntStateOf(alarm.hour) }
    var minute by remember(alarm.id) { mutableIntStateOf(alarm.minute) }
    var label by remember(alarm.id) { mutableStateOf(alarm.label) }
    var mask by remember(alarm.id) { mutableIntStateOf(alarm.daysMask) }
    var useAlarm by remember(alarm.id) { mutableStateOf(alarm.useAlarmVolume) }
    var autoAdjust by remember(alarm.id) { mutableStateOf(alarm.autoAdjustVolume) }
    var target by remember(alarm.id) { mutableFloatStateOf(alarm.targetVolumePercent.toFloat()) }
    var restore by remember(alarm.id) { mutableStateOf(alarm.restoreVolume) }
    var snooze by remember(alarm.id) { mutableFloatStateOf(alarm.snoozeMinutes.toFloat()) }
    var vibrate by remember(alarm.id) { mutableStateOf(alarm.vibrate) }
    var fade by remember(alarm.id) { mutableFloatStateOf(alarm.fadeInSeconds.toFloat()) }
    var soundUri by remember(alarm.id) { mutableStateOf(alarm.soundUri) }
    var showDelete by remember { mutableStateOf(false) }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Reopening falls back to bundled sound. */ }
            soundUri = uri.toString()
        }
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Spacer(Modifier.height(5.dp))
            FilledTonalButton(
                onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() },
                modifier = Modifier.fillMaxWidth().height(74.dp)
            ) { Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.headlineLarge) }
            OutlinedTextField(value = label, onValueChange = { label = it.take(60) }, label = { Text("アラーム名（任意）") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("繰り返し", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                listOf("月", "火", "水", "木", "金", "土", "日").forEachIndexed { index, text ->
                    val bit = 1 shl index
                    FilterChip(
                        selected = mask and bit != 0,
                        onClick = { mask = mask xor bit },
                        label = { Text(text) },
                        modifier = Modifier.widthIn(min = 42.dp)
                    )
                }
            }
            Text(if (mask == 0) "曜日を選ばない場合は1回のみ鳴ります" else repeatLabel(mask),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("音声", style = MaterialTheme.typography.titleMedium)
            SettingSwitch("A · アラーム音量を使用", "OFFの場合はメディア音量を使用します", useAlarm) { useAlarm = it }
            SettingSwitch("B · 音量を自動調整", "指定音量への変更を試みます", autoAdjust) { autoAdjust = it }
            if (autoAdjust) {
                Text("目標音量  ${target.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(value = target, onValueChange = { target = it }, valueRange = 0f..100f, steps = 19)
                if (target < 1f) Text("音量0では起床用アラームが無音になります", color = MaterialTheme.colorScheme.error)
                SettingSwitch("終了時に音量を戻す", "手動変更された音量は上書きしません", restore) { restore = it }
            }
            Text("フェードイン  ${fade.toInt()}秒", style = MaterialTheme.typography.bodyMedium)
            Slider(value = fade, onValueChange = { fade = it }, valueRange = 0f..60f, steps = 11)
            OutlinedButton(onClick = { soundPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (soundUri == null) "アラーム音を変更" else "選択済みの音声を変更")
            }
            if (soundUri != null) TextButton(onClick = { soundUri = null }) { Text("内蔵アラーム音に戻す") }
            HorizontalDivider()
            Text("スヌーズ・通知", style = MaterialTheme.typography.titleMedium)
            Text("スヌーズ  ${snooze.toInt()}分")
            Slider(value = snooze, onValueChange = { snooze = it }, valueRange = 1f..60f, steps = 58)
            SettingSwitch("バイブレーション", "音声と同時に振動します", vibrate) { vibrate = it }
            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("アラームを削除")
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        HorizontalDivider()
        Button(
            onClick = {
                onSave(alarm.copy(hour = hour, minute = minute, label = label.trim(), daysMask = mask,
                    absoluteTriggerMillis = null,
                    enabled = true, useAlarmVolume = useAlarm, autoAdjustVolume = autoAdjust,
                    targetVolumePercent = target.toInt(), restoreVolume = restore,
                    snoozeMinutes = snooze.toInt(), vibrate = vibrate,
                    fadeInSeconds = fade.toInt(), soundUri = soundUri))
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(52.dp)
        ) { Icon(Icons.Rounded.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("保存") }
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false },
        title = { Text("アラームを削除しますか？") },
        text = { Text("削除直後は元に戻せます。") },
        confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) { Text("削除") } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("キャンセル") } })
}

@Composable
private fun GlobalSettings(defaults: DefaultSettings, version: Int, modifier: Modifier = Modifier,
                           onChange: () -> Unit, onDiagnostics: () -> Unit) {
    // version triggers recomposition when preferences change.
    @Suppress("UNUSED_VARIABLE") val v = version
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("新しいアラームの初期値", style = MaterialTheme.typography.titleLarge)
        Text("既存のアラームには影響しません。個々のアラームでも変更できます。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch("A · アラーム音量を使用", "ON：アラーム音量／OFF：メディア音量", defaults.useAlarmVolume) {
            defaults.useAlarmVolume = it; onChange()
        }
        HorizontalDivider()
        SettingSwitch("B · 音量を自動調整", "アラーム開始時に指定音量へ変更", defaults.autoAdjustVolume) {
            defaults.autoAdjustVolume = it; onChange()
        }
        if (defaults.autoAdjustVolume) {
            Text("目標音量 ${defaults.targetVolumePercent}%")
            Slider(value = defaults.targetVolumePercent.toFloat(),
                onValueChange = { defaults.targetVolumePercent = it.toInt(); onChange() },
                valueRange = 0f..100f, steps = 19)
            SettingSwitch("終了後に音量を戻す", "ユーザーの手動変更を優先", defaults.restoreVolume) {
                defaults.restoreVolume = it; onChange()
            }
        }
        HorizontalDivider()
        Text("接続が切れたら本体スピーカーで鳴動します。OSの経路制御によって実際の出力先が変わる場合があります。",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("権限と出力先を診断")
        }
    }
}

@Composable
private fun Diagnostics(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val audio = context.getSystemService(AudioManager::class.java)
    val exact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
    val notificationAllowed = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val fullScreen = Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()
    @Suppress("UNUSED_VARIABLE") val recalc = refresh
    val connected = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.isSink }.joinToString("、") { audioDeviceName(it.type) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("アラームの実行状態", style = MaterialTheme.typography.titleLarge)
        DiagnosticRow("正確なアラーム", exact)
        DiagnosticRow("通知", notificationAllowed)
        DiagnosticRow("全画面アラーム", fullScreen)
        if (!notificationAllowed && Build.VERSION.SDK_INT >= 33) {
            Button(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("通知を許可") }
        }
        if (!fullScreen && Build.VERSION.SDK_INT >= 34) {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                })
            }) { Text("全画面通知の設定を開く") }
        }
        HorizontalDivider()
        Text("現在の利用可能な音声出力", style = MaterialTheme.typography.titleMedium)
        Text(connected.ifBlank { "音声デバイスを確認できません" }, style = MaterialTheme.typography.bodyMedium)
        Text("接続一覧は、実際のアラーム音の出力先を保証するものではありません。イヤホン切断時には本体スピーカーへの切替を試みます。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { refresh++ }) { Text("再読み込み") }
    }
}

@Composable
private fun DiagnosticRow(title: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(if (ok) "利用可能" else "要確認", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
    }
}

private fun audioDeviceName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "本体スピーカー"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有線イヤホン"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USBオーディオ"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LEイヤホン"
    else -> "その他 ($type)"
}
