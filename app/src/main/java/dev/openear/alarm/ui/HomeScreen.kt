// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.openear.alarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openear.alarm.data.AlarmSpec
import java.time.Instant

/** Something that stops alarms from working as expected, with the action that fixes it. */
internal class Problem(val text: String, val action: String, val fix: () -> Unit)

internal class HomeState(
    val alarms: List<AlarmSpec>,
    val snoozes: Map<Int, Instant> = emptyMap(),
    val next: Instant? = null,
    val now: Instant = Instant.now(),
    val problems: List<Problem> = emptyList(),
)

internal class HomeActions(
    val add: () -> Unit = {}, val edit: (AlarmSpec) -> Unit = {}, val toggle: (AlarmSpec) -> Unit = {},
    val quick: (Long) -> Unit = {}, val settings: () -> Unit = {},
)

@Composable
internal fun HomeScreen(state: HomeState, actions: HomeActions, snackbar: SnackbarHostState) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("アラーム") }, actions = {
                IconButton(actions.settings) { Icon(Icons.Rounded.Settings, "設定") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(actions.add, containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary) { Icon(Icons.Rounded.Add, "アラームを追加") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).readable().fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(state.problems, key = { it.text }) { problem ->
                ListItem(
                    headlineContent = { Text(problem.text) },
                    leadingContent = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
                    trailingContent = { TextButton(problem.fix) { Text(problem.action) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
            item { NextAlarm(state.next, state.now) }
            item {
                Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    listOf(10L, 20L, 30L).forEach { minutes ->
                        SuggestionChip({ actions.quick(minutes) }, label = { Text("${minutes}分後") },
                            modifier = Modifier.semantics { contentDescription = "${minutes}分後に鳴らす" })
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant) }
            if (state.alarms.isEmpty()) item {
                Text("アラームはありません", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp))
            }
            items(state.alarms, key = { it.id }) { alarm -> AlarmRow(alarm, state.snoozes[alarm.id], actions) }
        }
    }
}

@Composable
private fun NextAlarm(next: Instant?, now: Instant) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)) {
        if (next == null) {
            Text("鳴る予定のアラームはありません", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(untilLabel(now, next), style = MaterialTheme.typography.headlineMedium)
            Text("次のアラーム  ${dateLabel(next, now)} ${time(next)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlarmRow(alarm: AlarmSpec, snooze: Instant?, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { actions.edit(alarm) }.padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(time(alarm.hour, alarm.minute), style = ListTimeStyle, color = if (alarm.enabled) colors.onSurface else colors.outline)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (alarm.daysMask != 0) DayLetters(alarm.daysMask, alarm.enabled)
                else Text("1回のみ", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                if (alarm.label.isNotBlank()) Text(alarm.label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (snooze != null) Text("スヌーズ中  ${time(snooze)}に再開", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
        }
        Switch(alarm.enabled, { actions.toggle(alarm) },
            modifier = Modifier.semantics { contentDescription = "${time(alarm.hour, alarm.minute)}のアラーム" })
    }
}
