# OpenEar Alarm: design decisions

## Reference and adaptation

The supplied `apple-hig-design.zip` targets Apple platforms. This is an Android application, so its *principles* (purpose, agency, responsibility, familiarity, flexibility, simplicity, craft, delight) inform the experience, while Jetpack Compose / Material 3 supply native behavior. This is not an attempt to import iOS navigation, SF Symbols or Liquid Glass onto Android.

Apple's HIG principles: https://developer.apple.com/design/human-interface-guidelines/design-principles

## App anatomy

- **Alarms**: next alarm summary, active alarms, disabled alarms, edit by tapping a row, add button.
- **Edit alarm**: time picker, repeat days, label, A/B independent controls, optional volume target/restore, ringtone, fade-in, snooze, vibration. Save returns to list. Deletion requires confirmation and offers undo.
- **Settings**: default A and B configuration for future alarms (no surprise mutations to existing alarms).
- **Diagnostics**: exact alarm, notification, full-screen capability, current output devices, contextual request for notification permission.
- **Ringing**: primary snooze and stop buttons, each sized for large touch targets, available from lock screen when OS permission permits.

## Experience and state details

| State | User-visible behavior |
|---|---|
| Empty | Explanatory message and add action, with quick 10/20/30-minute timers |
| Saved | Home shows time and repeat cadence |
| Disabled | Distinct but legible disabled alarm time |
| Delete | Explicit confirmation and undo snackbar |
| Exact-alarm restricted | Diagnostics flags capability; UI save reports OS rejection |
| Notification permission denied | Diagnostics contains contextual request |
| Selected ringtone missing | Offline generated WAV fallback (generated in app-private storage) |
| No headphones | Prefer phone speaker (wake-up priority) |
| Audio route change | Re-evaluate device preference, log actual routing mismatch |
| Notification / full-screen restricted | Allow in-app edit and diagnostics; OS can limit ringing UI |
| App offline | No internet dependency or network permission |
| Screen reader | Native Compose semantics and labels; accessibility test pending |

## Audio policy

A: ON uses `USAGE_ALARM`/`STREAM_ALARM`; OFF uses `USAGE_MEDIA`/`STREAM_MUSIC`.
B: ON attempts system stream volume adjustment; OFF leaves system stream volume untouched. On stop, restore only if the level still matches the value the app applied. During ringing, app-level fade-in raises playback gain progressively.

Device routing is advisory under Android's audio policy. `MediaPlayer.setPreferredDevice` can fail to route as requested; logged differences are not proof of audible sound. A lost or broken headphone is not detectable with certainty. Bluetooth, wired, USB and speaker behavior requires per-device validation.

## Current risks / blockers before distribution

1. GitHub Actions now builds a debug APK and runs unit tests and Android Lint. Emulator instrumentation tests are configured; actual device validation remains pending.
2. **Direct Boot** storage and receivers are implemented but before-first-unlock ringing is not hardware-verified. Multi-step snooze policies, per-output-device target volume and an execution-history panel are deferred. Android cannot fire alarms while powered off or after a user force-stop.
3. The first version uses `SharedPreferences` as a compact crash-resilient synchronous store, not the planned Room/DataStore architecture. Corrupt entries are skipped; add backup/import and migration tests before shipping.
4. Android 14+ full-screen intent may be disabled by policy/user. Android 13+ notifications may be denied. A non-notifying foreground service alone does not offer a dependable waking UX.
5. Android's media playback foreground-service and audio-focus limits must be checked on actual Android 15/16 devices before declaring wake-up reliability.
6. A production app must add alarms-overlapping behavior, device routing end-to-end tests, force-stop/Doze/reboot tests, and explain battery policy restrictions before shipping.

## Review gate

| Accessibility / quality item | Status | Evidence |
|---|---|---|
| Font scaling and landscape | Unknown | Compose adaptive layout; device testing pending |
| TalkBack order | Unknown | Standard Compose controls, explicit action descriptions; pending |
| Color-independent status | Implemented | Text state alongside switches |
| Large tap targets | Implemented | 52dp save, 68dp ringing, Material buttons |
| Dark theme | Implemented | Dynamic light/dark color schemes |
| Reduce Motion | Implemented | No UI animations essential to operation |
| RTL | Partial | Compose relative padding; Japanese-only UI strings |
| Offline & privacy | Implemented | No internet permission, SDKs or trackers |
| Scheduling correctness | Tested | GitHub Actions Gradle JVM tests pass |
| Device playback reliability | Unknown | Requires actual devices |

Current distribution decision: **not ready for release**. This is an early implementation, not a validated replacement for a critical wake-up alarm.
