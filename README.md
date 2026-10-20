# OpenEar Alarm

Android 9+ / Kotlin / Jetpack Compose / GPL-3.0-or-later.

A fully open-source, offline alarm-clock project designed around headphone-first playback and **wake-up-priority speaker fallback**. No ads, telemetry, accounts, network permission, or proprietary SDKs.

## What is implemented

- Alarm list, creation, editing, repeat weekdays, 10/20/30-minute exact quick timers, enable/disable, deletion confirmation + undo.
- Native system time picker; offline synthesized fallback alarm sound and user-selected local audio with persisted URI permission.
- Independent A (`USAGE_ALARM` vs `USAGE_MEDIA`) and B (adjust system stream volume) settings, global defaults and per-alarm overrides; app gain fade-in; best-effort restore of volume.
- `AlarmManager.setAlarmClock()` exact scheduling, boot/clock/time-zone/app-update rescheduling, one-off and weekly alarms.
- Foreground ringing service, ongoing notification with stop/snooze actions, full-screen ringing activity, 15-minute automatic stop, vibration.
- Headphone-first device preference with speaker fallback after disconnect; device diagnostics and contextual notification permission request.
- Device-protected snooze state, process-wide serialized alarm storage, 40-event offline execution history and one-time built-in-tone recovery after playback failure.
- CI APK generation, unit tests, Android Lint and emulator instrumentation checks. `docs/DESIGN.md` includes design and release blockers.

## Building

Open the project directory in Android Studio with **SDK 36, JDK 17+ and Gradle 8.13**; sync, then `gradle :app:assembleDebug` (or configure a Gradle wrapper using `gradle wrapper --gradle-version 8.13`). GitHub Actions builds a debug APK, runs JVM unit tests and Android Lint, and runs emulator instrumentation checks on Android 9 (API 28) and Android 15 (API 35). Physical-device hardware tests are still required. `gradle/wrapper/gradle-wrapper.properties` records the intended distribution, but the wrapper JAR/scripts need to be generated in an environment with Gradle installed.

- Min SDK: 28 (Android 9)
- Target / compile: 36 (Android 16)
- AGP: 8.13.2, Kotlin: 2.2.21
- Java: 17

Use a secondary alarm until end-to-end tests on actual devices establish reliability. For OS behavior/limits, see https://developer.android.com/develop/background-work/services/alarms and https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start .

## License

GNU General Public License version 3 or later; see `LICENSE`. Generated in-repo alarm tone is original and freely redistributable under the same license.

The manual audio-routing and device-power test matrix is tracked in [docs/QA.md](docs/QA.md).

## Safety and status

This is a development snapshot, **not** a validated waking alarm. CI compilation, JVM unit tests and lint pass. Emulator UI tests run in CI. Doze, Bluetooth switching, before-first-unlock ringing and device-specific foreground-service behavior still require device verification. The generated fallback tone is local and requires no binary sound assets.
