// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.openear.alarm.audio.AlarmPlaybackService
import dev.openear.alarm.data.AlarmStore

class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val store = AlarmStore(this)
        val alarm = store.byId(intent.getIntExtra(AlarmPlaybackService.EXTRA_ALARM_ID, -1))
        if (alarm == null) {
            // An expired notification must not display a fake 00:00 alarm.
            finish()
            return
        }
        val canSnooze = alarm.snoozeLimit == 0 ||
            store.snoozeCount(alarm.id) < alarm.snoozeLimit
        setContent {
            OpenEarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(alarm?.label?.ifBlank { "アラーム" } ?: "アラーム", style = MaterialTheme.typography.titleLarge)
                            Text("%02d:%02d".format(alarm?.hour ?: 0, alarm?.minute ?: 0),
                                style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                            Text("音声出力先の状態は端末によって異なります",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (canSnooze) {
                                Button(
                                    onClick = { sendAction(AlarmPlaybackService.ACTION_SNOOZE) },
                                    modifier = Modifier.fillMaxWidth().height(68.dp)
                                ) { Text("スヌーズ", style = MaterialTheme.typography.titleMedium) }
                            } else {
                                Text("スヌーズ回数の上限に達しました",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(
                                onClick = { sendAction(AlarmPlaybackService.ACTION_STOP) },
                                modifier = Modifier.fillMaxWidth().height(68.dp)
                            ) { Text("停止", style = MaterialTheme.typography.titleMedium) }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
    }

    private fun sendAction(action: String) {
        startService(Intent(this, AlarmPlaybackService::class.java).setAction(action))
        finish()
    }
}
