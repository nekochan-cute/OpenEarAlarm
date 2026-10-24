# How OpenEar Alarm works

## Screens

| Screen | Contents |
|---|---|
| Alarms | Time until the next alarm, 10/20/30-minute timers, alarm rows with weekday letters and an on/off switch. A row appears for each missing permission, with a button that fixes it. |
| Edit | Time (Material time picker), weekdays, label; sound, volume stream, per-output volume, fade-in, vibration; snooze interval and count, automatic stop. Save is in the top bar, delete at the bottom with confirmation and undo. |
| Settings | Defaults for new alarms; exact alarms, notifications, full-screen alarms and current outputs; history; version and source. |
| History | The last 40 events (fired, playback started, stopped, snoozed, failures), stored on the device. |
| Ringing | Current time, label, where the alarm is playing, snooze and stop. Shown over the lock screen. |

Existing alarms keep their own settings when the defaults change.

## Scheduling

- `AlarmScheduler` registers each enabled alarm with `AlarmManager.setAlarmClock()`, so the system shows it as the next alarm and fires it exactly.
- `AlarmMath.nextTrigger` computes the next occurrence. In a daylight-saving gap it moves to the next valid time; in an overlap it takes the earliest instant still in the future.
- A timer stores its absolute deadline and stays exact to the second until its time or days are edited.
- When an alarm fires, the next weekly occurrence is registered before playback starts. A one-off alarm is switched off.
- Snoozes store their deadline and count before they are registered, so a reboot cannot lose or reset them. A snooze that was due less than 15 minutes before boot rings right after boot.
- `RestoreReceiver` registers everything again after `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, time and time-zone changes and app updates. A failure on one alarm does not stop the others.

## Storage

Alarms, snooze state and history live in device-protected storage (`createDeviceProtectedStorageContext`), so they can be read before the first unlock after a reboot. Installs that stored alarms in credential-protected storage are migrated once. Writes are synchronous `commit()`s under a process-wide lock, because the receiver, service and UI each use their own `AlarmStore`. Corrupt entries are skipped rather than failing the whole list.

## Playback

`AlarmPlaybackService` is a foreground service of type `mediaPlayback` that runs only while an alarm rings.

1. It promotes itself to the foreground with the ringing notification (full-screen intent to `RingingActivity`).
2. It requests transient audio focus and plays even if focus is denied or later lost.
3. It sets the volume for the expected output if the alarm asks for one (below).
4. It plays the chosen sound in a loop. If the sound cannot be opened or fails while playing, it switches once to the built-in tone, a two-tone WAV generated on the device. If that fails too, vibration and the notification continue.
5. It raises the player gain over the fade-in time and stops automatically after the configured time, if any.

A second alarm that fires while one rings joins the same notification. If it fails to start, the ringing alarm is not affected.

## Output routing

The service prefers, in order: wired headphones, USB headphones, Bluetooth A2DP and LE headphones, then the built-in speaker. It calls `MediaPlayer.setPreferredDevice` and re-evaluates whenever devices are added or removed or the player's route changes. 400 ms after each change it reads `routedDevice`, so the output shown and the volume applied follow the device the audio actually reached.

## Volume

- **Stream:** `USAGE_ALARM` on the alarm stream (default) or `USAGE_MEDIA` on the media stream.
- **Levels:** Android keeps one volume level per output device. With "音量を指定する", an alarm has a speaker level and a headphone level. The service applies the level for the current output at start and on every route change, recording the level it replaced.
- **Restore:** on stop it restores the level of the output that is still connected, if "元の音量に戻す" is on and the user did not change the level while ringing. Android only lets an app set the level of the connected output, so the level on a disconnected output stays as the alarm set it.
- **Older alarms:** alarms saved before separate levels existed use their single level for both outputs.

## Android behaviour that applies

- Alarms do not fire while the phone is off, or after the app is force-stopped in system settings, until it is opened again.
- Notifications (Android 13+) and full-screen alarms (Android 14+) can be turned off by the user. The alarm still rings, but the ringing screen may not appear over the lock screen. The app shows each missing permission with a button to allow it.
- Do Not Disturb and per-app audio policies on some phones can still silence or reroute the alarm stream.

## Tests

| Where | What |
|---|---|
| JVM | Next-occurrence math including DST, timers, validation |
| Robolectric | Volume per output (speaker, headphones, disconnect while ringing, restore, migration); screen rendering with `-Pscreenshots` |
| Emulators (API 28, 35, 36) | UI flows, scheduling and delivery, snooze limits and restore after restart, Direct Boot storage, concurrent store writes, built-in tone, playback start and stop |
