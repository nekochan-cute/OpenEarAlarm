// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.openear.alarm.ui

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openear.alarm.data.AlarmMath
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.ui.icons.*
import dev.openear.alarm.ui.icons.automirrored.*
import java.time.Instant
import java.time.ZoneId

internal class EditActions(val back: () -> Unit = {}, val save: (AlarmSpec) -> Unit = {}, val delete: () -> Unit = {})

/** Opens the system list of alarm sounds. */
private object PickAlarmSound : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input)
    @Suppress("DEPRECATION")
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
}

internal fun soundName(context: Context, uri: String?): String {
    if (uri == null) return "OpenEarの音"
    val parsed = Uri.parse(uri)
    return runCatching {
        if (parsed.authority == "media" || parsed.authority == "settings") RingtoneManager.getRingtone(context, parsed)?.getTitle(context)
        else context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
    }.getOrNull() ?: "選択した音"
}

private val fades = listOf(0 to "すぐに最大音量", 5 to "5秒", 10 to "10秒", 20 to "20秒", 30 to "30秒", 60 to "60秒")
private val stops = listOf(5 to "5分後", 10 to "10分後", 15 to "15分後", 30 to "30分後", 60 to "60分後", 0 to "止めるまで鳴らす")
private val snoozeMinutes = listOf(1, 3, 5, 10, 15, 20, 30).map { it to "${it}分" }
private val snoozeLimits = listOf(1, 2, 3, 5, 10, 0).map { it to if (it == 0) "制限なし" else "${it}回まで" }
private val streams = listOf(true to "アラームの音量", false to "メディアの音量")

internal fun volumeLabel(speaker: Int, headphones: Int) = "スピーカー ${speaker}%・イヤホン ${headphones}%"
internal fun fadeLabel(seconds: Int) = fades.firstOrNull { it.first == seconds }?.second ?: "${seconds}秒"
internal fun stopLabel(minutes: Int) = stops.firstOrNull { it.first == minutes }?.second ?: "${minutes}分後"
internal fun snoozeLabel(a: AlarmSpec) = "${a.snoozeMinutes}分・" + if (a.snoozeLimit == 0) "制限なし" else "${a.snoozeLimit}回まで"

@Composable
internal fun EditScreen(alarm: AlarmSpec, isNew: Boolean, actions: EditActions, now: Instant = Instant.now()) {
    val context = LocalContext.current
    var draft by remember(alarm.id) { mutableStateOf(alarm) }
    var dialog by remember { mutableStateOf("") }
    val file = rememberLauncherForActivityResult(remember { ActivityResultContracts.OpenDocument() }) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            draft = draft.copy(soundUri = uri.toString())
        }
    }
    val system = rememberLauncherForActivityResult(PickAlarmSound) { uri -> if (uri != null) draft = draft.copy(soundUri = uri.toString()) }
    // A quick timer keeps its exact deadline unless its time or days are changed.
    val result = draft.copy(enabled = true, absoluteTriggerMillis = alarm.absoluteTriggerMillis?.takeIf {
        it > now.toEpochMilli() && draft.hour == alarm.hour && draft.minute == alarm.minute && draft.daysMask == 0
    })
    val next = runCatching { AlarmMath.nextTrigger(result, now, ZoneId.systemDefault()) }.getOrNull()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isNew) "アラームを追加" else "アラームを編集") },
            navigationIcon = { IconButton(actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } },
            actions = { TextButton({ actions.save(result) }, Modifier.padding(end = 8.dp)) { Text("保存") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).readable().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Column(Modifier.fillMaxWidth().clickable { dialog = "time" }.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(time(draft.hour, draft.minute), style = ClockStyle)
                if (next != null) Text("${dateLabel(next, now)} ${time(next)}に鳴ります · ${untilLabel(now, next)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DayToggles(draft.daysMask, { draft = draft.copy(daysMask = it) }, Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            Text(repeatLabel(draft.daysMask), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp))
            OutlinedTextField(draft.label, { draft = draft.copy(label = it.take(60)) }, label = { Text("ラベル") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))

            SectionHeader("音")
            SettingRow("サウンド", soundName(context, draft.soundUri), Icons.Rounded.MusicNote) { dialog = "sound" }
            SettingRow("使う音量", streams.first { it.first == draft.useAlarmVolume }.second, Icons.AutoMirrored.Rounded.VolumeUp) { dialog = "stream" }
            SwitchRow("音量を指定する", draft.autoAdjustVolume, Icons.Rounded.Tune,
                if (draft.autoAdjustVolume) volumeLabel(draft.targetVolumePercent, draft.headphoneVolumePercent) else "端末の音量のまま") {
                draft = draft.copy(autoAdjustVolume = it)
            }
            if (draft.autoAdjustVolume) {
                VolumeLevels(draft.targetVolumePercent, draft.headphoneVolumePercent) { speaker, headphones ->
                    draft = draft.copy(targetVolumePercent = speaker, headphoneVolumePercent = headphones)
                }
                SwitchRow("鳴り終わったら元の音量に戻す", draft.restoreVolume, null) { draft = draft.copy(restoreVolume = it) }
            }
            SettingRow("だんだん大きく", fadeLabel(draft.fadeInSeconds), Icons.AutoMirrored.Rounded.TrendingUp) { dialog = "fade" }
            SwitchRow("バイブレーション", draft.vibrate, Icons.Rounded.Vibration) { draft = draft.copy(vibrate = it) }

            SectionHeader("止め方")
            SettingRow("スヌーズ", snoozeLabel(draft), Icons.Rounded.Snooze) { dialog = "snooze" }
            SettingRow("自動で止める", stopLabel(draft.autoStopMinutes), Icons.Rounded.TimerOff) { dialog = "stop" }

            if (!isNew) {
                Spacer(Modifier.height(16.dp))
                TextButton({ dialog = "delete" }, Modifier.padding(horizontal = 12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(8.dp)); Text("アラームを削除")
                }
            }
        }
    }

    when (dialog) {
        "time" -> {
            val state = rememberTimePickerState(draft.hour, draft.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { dialog = "" },
                confirmButton = { TextButton({ draft = draft.copy(hour = state.hour, minute = state.minute); dialog = "" }) { Text("OK") } },
                dismissButton = { TextButton({ dialog = "" }) { Text("キャンセル") } },
                text = { TimePicker(state) },
            )
        }
        "sound" -> ChoiceDialog("サウンド", listOf("app" to "OpenEarの音", "system" to "端末のアラーム音から選ぶ", "file" to "ファイルから選ぶ"),
            if (draft.soundUri == null) "app" else "", { dialog = "" }) { choice ->
            dialog = ""
            when (choice) {
                "app" -> draft = draft.copy(soundUri = null)
                "system" -> runCatching { system.launch(draft.soundUri?.let(Uri::parse)) }
                "file" -> file.launch(arrayOf("audio/*"))
            }
        }
        "stream" -> ChoiceDialog("使う音量", streams, draft.useAlarmVolume, { dialog = "" }) { draft = draft.copy(useAlarmVolume = it); dialog = "" }
        "fade" -> ChoiceDialog("だんだん大きく", fades, draft.fadeInSeconds, { dialog = "" }) { draft = draft.copy(fadeInSeconds = it); dialog = "" }
        "stop" -> ChoiceDialog("自動で止める", stops, draft.autoStopMinutes, { dialog = "" }) { draft = draft.copy(autoStopMinutes = it); dialog = "" }
        "snooze" -> SnoozeDialog(draft, { dialog = "" }) { minutes, limit -> draft = draft.copy(snoozeMinutes = minutes, snoozeLimit = limit); dialog = "" }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("このアラームを削除しますか？") },
            confirmButton = { TextButton({ dialog = ""; actions.delete() }) { Text("削除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ dialog = "" }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun SnoozeDialog(alarm: AlarmSpec, dismiss: () -> Unit, done: (Int, Int) -> Unit) {
    var minutes by remember { mutableIntStateOf(alarm.snoozeMinutes) }
    var limit by remember { mutableIntStateOf(alarm.snoozeLimit) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("スヌーズ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("間隔", style = MaterialTheme.typography.labelLarge)
                ChipRow(snoozeMinutes, minutes) { minutes = it }
                Spacer(Modifier.height(8.dp))
                Text("回数", style = MaterialTheme.typography.labelLarge)
                ChipRow(snoozeLimits, limit) { limit = it }
            }
        },
        confirmButton = { TextButton({ done(minutes, limit) }) { Text("OK") } },
        dismissButton = { TextButton(dismiss) { Text("キャンセル") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<Pair<Int, String>>, selected: Int, choose: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(value == selected, { choose(value) }, { Text(label, maxLines = 1, overflow = TextOverflow.Clip) })
        }
    }
}
