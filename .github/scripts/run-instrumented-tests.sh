#!/usr/bin/env bash
# Boots a headless x86_64 emulator for the given API level and runs the instrumented tests.
# Usage: run-instrumented-tests.sh <api-level>
set -euo pipefail

API_LEVEL="$1"
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
ADB="$SDK/platform-tools/adb"

# System image package names differ between releases (e.g. android-37 vs android-37.0),
# so look up the newest matching image instead of hard-coding it.
IMAGE=$("$SDKMANAGER" --list 2>/dev/null \
  | grep -oE "system-images;android-${API_LEVEL}(\.[0-9]+)?;google_apis;x86_64" \
  | sort -uV | tail -n 1 || true)
if [ -z "$IMAGE" ]; then
  echo "No google_apis x86_64 system image found for API $API_LEVEL. Available images:" >&2
  "$SDKMANAGER" --list 2>/dev/null | grep "system-images;android-${API_LEVEL}" >&2 || true
  exit 1
fi
echo "Using $IMAGE"

# `yes` is killed by SIGPIPE when sdkmanager exits, so only the last command's status counts here.
set +o pipefail
yes | "$SDKMANAGER" --install "$IMAGE" "emulator" "platform-tools" > /dev/null
echo "no" | "$AVDMANAGER" create avd --force --name ci --package "$IMAGE"
set -o pipefail

"$SDK/emulator/emulator" -avd ci -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -memory 4096 -camera-back none -camera-front none \
  > emulator.log 2>&1 &

"$ADB" wait-for-device
timeout 900 bash -c \
  "until [ \"\$('$ADB' shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')\" = 1 ]; do sleep 5; done"
echo "Emulator booted: $("$ADB" shell getprop ro.build.version.release | tr -d '\r') (API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r'))"

"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell input keyevent 82 || true

"$ADB" logcat -c || true
"$ADB" logcat > logcat.txt 2>&1 &
LOGCAT_PID=$!

set +e
./gradlew --stacktrace connectedDebugAndroidTest
RESULT=$?
set -e

kill "$LOGCAT_PID" 2>/dev/null || true
"$ADB" emu kill || true
exit "$RESULT"
