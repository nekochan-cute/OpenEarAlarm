# Manual acceptance tests for OpenEar Alarm

CI is necessary but not sufficient for alarm correctness. This project is **not production-ready as the sole wake-up alarm** until the real-device gates below pass. Report device model, Android version, OEM skin, app commit and selected A/B settings with failures. No network telemetry is collected.

## Critical blockers before shipping

- [ ] Android 9 (API 28) and Android 16 (API 36) physical devices: cold launch, permission onboarding and exact alarm setup
- [ ] 100/100 exact alarms after device sleep, recording planned and observed times (test with and without screen locked)
- [ ] 20/20 alarms in Doze, without a foreground UI (do not test while another app already holds a wakelock)
- [ ] Bluetooth Classic A2DP and LE Audio, wired 3.5mm and USB-C headsets: prefer headphones while connected
- [ ] Disconnect Bluetooth / unplug cable during ringing: verify the phone speaker actually emits sound, not just that setPreferredDevice returns true
- [ ] Reconnect after fallback: no double playback, no unintended volume spike
- [ ] Both A and B ON/OFF combinations: alarm vs media stream, target volume 0/50/100%, fade 0/10/60 sec and best-effort restoration
- [ ] DND, vibration disabled, alarm stream muted: clear warning about Android policy, no false promise of audibility
- [ ] Stop and snooze from notification and locked screen, including repeated alarms and two alarms at identical times
- [ ] Reboot before the first unlock (Direct Boot) and snooze across reboot: both pending notifications and actually audible playback tested
- [ ] Unexpected process death versus Settings → Force stop: distinguish supported from OS-restricted behavior
- [ ] App upgrade keeps alarms scheduled, time-zone/DST changes recompute future recurring times
- [ ] UI in portrait, landscape, 320dp widths, 1.0/1.5/2.0 font scales; no clipped critical actions; TalkBack ordering and Japanese labels reviewed
- [ ] Overnight battery usage, thermal throttling, OEM background restrictions and audio focus competition with video/calls

## Suggested checks

1. Install the debug APK, set one test alarm about 2 minutes in the future, lock the screen, and compare actual ring time against the scheduled time. Do not depend on a newly installed test build as your only alarm.
2. Check the app's **Settings → Diagnostics** before testing. A red notification/full-screen result can affect the visible ringing controls. Use the execution history to separate delivery failures from playback failures.
3. With an actual Bluetooth headset connected, start another test alarm. Disconnect it during ringing and listen for audio from the device's speaker. Repeat with a wired headset. This cannot be established by routing API logs alone.
4. For Doze experiments, use a disposable test alarm and the Android platform tools on a test device. `adb shell dumpsys deviceidle force-idle` alters system state. Restore normal state with `adb shell dumpsys deviceidle unforce` when finished.
5. Test reboot recovery both after unlock and, with supported credentials, before the first unlock. Be aware that a completely powered-off phone cannot run Android alarms.
6. Export redacted `adb logcat` traces if a failure happens. Logs can contain private information; scrub them before filing issues.

## Automated gates

GitHub Actions runs APK compilation, JVM tests, Android Lint, Compose UI tests, concurrent Store write tests, persisted snooze and exact-alarm delivery checks in Android 9, Android 15 and Android 16 emulators. These tests check software state and MediaPlayer start; they do **not** measure sound emitted by headphone or speaker hardware.

The release gate remains **blocked** pending actual device test evidence for the items above.
