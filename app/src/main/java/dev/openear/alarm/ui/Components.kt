// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Keeps text columns readable on tablets and in landscape. */
internal fun Modifier.readable() = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp)

@Composable
internal fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
internal fun SettingRow(title: String, value: String?, icon: ImageVector?, trailing: (@Composable () -> Unit)? = null,
                        onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, icon: ImageVector?, value: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        trailingContent = { Switch(checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, dismiss: () -> Unit, choose: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .selectable(value == selected, role = Role.RadioButton) { choose(value) }
                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(value == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(dismiss) { Text("キャンセル") } },
    )
}

/** Seven round weekday toggles in one row; they shrink on narrow screens rather than wrap. */
@Composable
internal fun DayToggles(mask: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DAYS.forEachIndexed { i, day ->
            val on = mask and (1 shl i) != 0
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.widthIn(max = 44.dp).fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                        .background(if (on) colors.primary else Color.Transparent)
                        .border(1.dp, if (on) colors.primary else colors.outlineVariant, CircleShape)
                        .toggleable(on, role = Role.Checkbox) { onChange(mask xor (1 shl i)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(day, style = MaterialTheme.typography.labelLarge, color = if (on) colors.onPrimary else colors.onSurface)
                }
            }
        }
    }
}

/** Weekday letters with the selected ones highlighted, for alarm rows. */
@Composable
internal fun DayLetters(mask: Int, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DAYS.forEachIndexed { i, day ->
            val on = mask and (1 shl i) != 0
            Text(day, style = MaterialTheme.typography.labelMedium,
                color = when { !on -> colors.outlineVariant; enabled -> colors.primary; else -> colors.onSurfaceVariant })
        }
    }
}
