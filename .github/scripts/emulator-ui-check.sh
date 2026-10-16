#!/usr/bin/env bash
set -u
mkdir -p screenshots
status=0
gradle --no-daemon :app:connectedDebugAndroidTest || status=$?

# Gradle's connected test task can remove its installed APK. Install the
# freshly built application explicitly before screenshot capture.
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
adb shell am force-stop dev.openear.alarm || true
adb logcat -c || true
adb shell am start -W -n dev.openear.alarm/.ui.MainActivity | tee screenshots/launch-output.txt || status=1
sleep 3
adb shell pidof dev.openear.alarm || status=1
adb shell dumpsys window > screenshots/window-light.txt || true
adb logcat -d -t 1200 > screenshots/logcat-light.txt || true
adb exec-out screencap -p > screenshots/openear-home.png || status=1

adb shell cmd uimode night yes || true
adb shell am force-stop dev.openear.alarm || true
adb shell am start -W -n dev.openear.alarm/.ui.MainActivity | tee screenshots/launch-dark-output.txt || status=1
sleep 3
adb shell pidof dev.openear.alarm || status=1
adb shell dumpsys window > screenshots/window-dark.txt || true
adb logcat -d -t 1200 > screenshots/logcat-dark.txt || true
adb exec-out screencap -p > screenshots/openear-dark.png || status=1
exit "$status"
