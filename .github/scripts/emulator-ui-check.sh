#!/usr/bin/env bash
set -u
mkdir -p screenshots
status=0
./gradlew --no-daemon :app:connectedDebugAndroidTest || status=$?

# UI images come from Compose pixel capture inside instrumentation tests.
# Save them in MediaStore so they survive the test runner uninstalling its APK.
if [[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 29 ]]; then
  adb pull /sdcard/Download/OpenEarAlarm screenshots/compose || status=1
fi

# Independently check a cold start, but do not use adb screencap as UI evidence
# because emulator Pixel Launcher can display unrelated ANR dialogs.
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
adb shell am force-stop dev.openear.alarm || true
adb logcat -c || true
adb shell am start -W -n dev.openear.alarm/.ui.MainActivity | tee screenshots/launch-output.txt || status=1
sleep 3
adb shell pidof dev.openear.alarm || status=1
adb shell dumpsys window > screenshots/window-light.txt || true
adb logcat -d -t 1200 > screenshots/logcat-light.txt || true
# Screenshot of OS window is diagnostic only; Compose captures are authoritative.
adb exec-out screencap -p > screenshots/system-light.png || true

adb shell cmd uimode night yes || true
adb shell am force-stop dev.openear.alarm || true
adb shell am start -W -n dev.openear.alarm/.ui.MainActivity | tee screenshots/launch-dark-output.txt || status=1
sleep 3
adb shell pidof dev.openear.alarm || status=1
adb shell dumpsys window > screenshots/window-dark.txt || true
adb logcat -d -t 1200 > screenshots/logcat-dark.txt || true
adb exec-out screencap -p > screenshots/system-dark.png || true
exit "$status"
