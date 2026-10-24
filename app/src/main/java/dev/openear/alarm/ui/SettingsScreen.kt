// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.openear.alarm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openear.alarm.data.AlarmHistory
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.ui.icons.*
import dev.openear.alarm.ui.icons.automirrored.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Defaults for new alarms. */
internal data class Defaults(val useAlarmVolume: Boolean, val autoAdjustVolume: Boolean, val targetVolumePercent: Int, val restoreVolume: Boolean)

/** What alarms depend on; a false value comes with an action that fixes it. */
internal class Readiness(val exact: Boolean, val notifications: Boolean, val fullScreen: Boolean, val outputs: List<String>)

internal class SettingsActions(
    val back: () -> Unit = {}, val defaults: (Defaults) -> Unit = {}, val allowExact: () -> Unit = {},
    val allowNotifications: () -> Unit = {}, val allowFullScreen: () -> Unit = {}, val history: () -> Unit = {}, val source: () -> Unit = {},
)

@Composable
internal fun SettingsScreen(defaults: Defaults, ready: Readiness, version: String, actions: SettingsActions) {
    var choosing by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("設定") }, navigationIcon = { IconButton(actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } })
    }) { padding ->
        Column(Modifier.padding(padding).readable().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionHeader("新しいアラームの初期値")
            SettingRow("使う音量", if (defaults.useAlarmVolume) "アラームの音量" else "メディアの音量", Icons.AutoMirrored.Rounded.VolumeUp) { choosing = true }
            SwitchRow("音量を指定する", defaults.autoAdjustVolume, Icons.Rounded.Tune,
                if (defaults.autoAdjustVolume) "${defaults.targetVolumePercent}%" else "端末の音量のまま") {
                actions.defaults(defaults.copy(autoAdjustVolume = it))
            }
            if (defaults.autoAdjustVolume) {
                Slider(defaults.targetVolumePercent.toFloat(), { actions.defaults(defaults.copy(targetVolumePercent = it.toInt())) },
                    valueRange = 10f..100f, modifier = Modifier.padding(start = 72.dp, end = 24.dp))
                SwitchRow("鳴り終わったら元の音量に戻す", defaults.restoreVolume, null) { actions.defaults(defaults.copy(restoreVolume = it)) }
            }

            SectionHeader("アラームを鳴らすための設定")
            Status("正確な時刻に鳴らす", ready.exact, "許可されています", "許可されていません", Icons.Rounded.Alarm, actions.allowExact)
            Status("通知", ready.notifications, "オン", "オフ", Icons.Rounded.Notifications, actions.allowNotifications)
            Status("ロック画面に表示", ready.fullScreen, "表示できます", "表示できません", Icons.Rounded.Smartphone, actions.allowFullScreen)
            SettingRow("いまの出力先", ready.outputs.joinToString("・").ifEmpty { "なし" }, Icons.Rounded.Headphones)
            SettingRow("履歴", "鳴った・止めた・スヌーズの記録", Icons.Rounded.History,
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }, onClick = actions.history)

            SectionHeader("このアプリについて")
            SettingRow("バージョン", version, Icons.Rounded.Info)
            SettingRow("ソースコード", "github.com/nekochan-cute/OpenEarAlarm · GPL-3.0-or-later", Icons.Rounded.Code, onClick = actions.source)
        }
    }
    if (choosing) ChoiceDialog("使う音量", listOf(true to "アラームの音量", false to "メディアの音量"), defaults.useAlarmVolume, { choosing = false }) {
        actions.defaults(defaults.copy(useAlarmVolume = it)); choosing = false
    }
}

@Composable
private fun Status(title: String, ok: Boolean, yes: String, no: String, icon: androidx.compose.ui.graphics.vector.ImageVector, fix: () -> Unit) {
    SettingRow(title, if (ok) yes else no, icon, trailing = {
        if (ok) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        else FilledTonalButton(fix) { Text("許可する") }
    })
}

@Composable
internal fun HistoryScreen(entries: List<AlarmHistory.Entry>, alarms: Map<Int, AlarmSpec>, back: () -> Unit) {
    val format = remember { DateTimeFormatter.ofPattern("M/d H:mm:ss") }
    Scaffold(topBar = {
        TopAppBar(title = { Text("履歴") }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る") } })
    }) { padding ->
        if (entries.isEmpty()) Text("まだ記録はありません", Modifier.padding(padding).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(Modifier.padding(padding).readable(), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(entries) { e ->
                val alarm = alarms[e.id]
                ListItem(
                    headlineContent = { Text(e.action) },
                    supportingContent = {
                        Text(listOfNotNull(Instant.ofEpochMilli(e.timeMillis).atZone(ZoneId.systemDefault()).format(format),
                            alarm?.let { a -> listOf(time(a.hour, a.minute), a.label).filter(String::isNotBlank).joinToString(" ") }).joinToString("  ·  "))
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}
