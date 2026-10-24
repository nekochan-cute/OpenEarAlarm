// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.openear.alarm.audio.AlarmPlaybackService
import dev.openear.alarm.data.AlarmSpec
import dev.openear.alarm.data.AlarmStore
import kotlinx.coroutines.delay
import java.time.Instant

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = AlarmStore(this)
        val alarm = store.byId(intent.getIntExtra(AlarmPlaybackService.EXTRA_ALARM_ID, -1))
        if (alarm == null) {
            // A stale notification must not show an alarm that no longer exists.
            finish()
            return
        }
        val canSnooze = alarm.snoozeLimit == 0 || store.snoozeCount(alarm.id) < alarm.snoozeLimit
        setContent {
            OpenEarTheme {
                RingingScreen(alarm, canSnooze, snooze = { send(AlarmPlaybackService.ACTION_SNOOZE) }, stop = { send(AlarmPlaybackService.ACTION_STOP) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The full-screen notification reuses this activity; show the alarm that is ringing now.
        setIntent(intent)
        recreate()
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmPlaybackService::class.java).setAction(action))
        finish()
    }
}

@Composable
internal fun RingingScreen(alarm: AlarmSpec, canSnooze: Boolean, snooze: () -> Unit, stop: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000 - System.currentTimeMillis() % 1000); now = Instant.now() } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Text(alarm.label.ifBlank { "アラーム" }, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary)
            Text(time(now), style = ClockStyle)
            Text(now.atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M月d日（E）", java.util.Locale.JAPAN)),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1.4f))
            if (canSnooze) {
                FilledTonalButton(snooze, Modifier.fillMaxWidth().height(72.dp)) {
                    Text("スヌーズ  ${alarm.snoozeMinutes}分", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text("スヌーズは使い切りました", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Button(stop, Modifier.fillMaxWidth().height(72.dp)) { Text("止める", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
