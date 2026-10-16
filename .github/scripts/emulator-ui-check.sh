#!/usr/bin/env bash
set -u
mkdir -p screenshots
status=0
gradle --no-daemon :app:connectedDebugAndroidTest || status=$?
# The emulator runner executes individual YAML lines in independent shells.
# Keep the test exit code here and capture light/dark screen evidence.
adb shell am force-stop dev.openear.alarm || true
adb shell am start -n dev.openear.alarm/.ui.MainActivity || true
sleep 2
adb exec-out screencap -p > screenshots/openear-home.png || true
adb shell cmd uimode night yes || true
adb shell am force-stop dev.openear.alarm || true
adb shell am start -n dev.openear.alarm/.ui.MainActivity || true
sleep 2
adb exec-out screencap -p > screenshots/openear-dark.png || true
exit "$status"
