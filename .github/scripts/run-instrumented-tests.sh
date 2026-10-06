#!/usr/bin/env bash
# Boots a headless x86_64 emulator for the given API level and runs the instrumented tests.
# Diagnostics are printed to the job log because uploaded artifacts are not always readable.
# Usage: run-instrumented-tests.sh <api-level>
set -euo pipefail

API_LEVEL="$1"
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
# avdmanager and the emulator must agree on where the AVD lives.
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"

print_file() {
  echo "::group::$1"
  cat "$1" 2>/dev/null || echo "($1 not found)"
  echo "::endgroup::"
}

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

echo "::group::Emulator diagnostics"
ls -l /dev/kvm || true
"$EMULATOR" -version 2>&1 | head -n 3 || true
"$EMULATOR" -accel-check 2>&1 || true
"$EMULATOR" -list-avds 2>&1 || true
df -h "$HOME" || true
echo "::endgroup::"

"$EMULATOR" -avd ci -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -memory 4096 -camera-back none -camera-front none \
  > emulator.log 2>&1 &
EMULATOR_PID=$!

# Wait for the boot to complete, but fail fast if the emulator process exits.
BOOT_DEADLINE=$((SECONDS + 900))
until [ "$(timeout 30 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "::error::The emulator exited before it finished booting."
    print_file emulator.log
    exit 1
  fi
  if [ "$SECONDS" -ge "$BOOT_DEADLINE" ]; then
    echo "::error::The emulator did not finish booting within 15 minutes."
    print_file emulator.log
    exit 1
  fi
  sleep 5
done
echo "Emulator booted: Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r')" \
  "(API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r'))"

"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell input keyevent 82 || true

"$ADB" logcat -c || true
"$ADB" logcat > logcat.txt 2>&1 &
LOGCAT_PID=$!

set +e
timeout 1800 ./gradlew --stacktrace connectedDebugAndroidTest
RESULT=$?
set -e

kill "$LOGCAT_PID" 2>/dev/null || true
if [ "$RESULT" -ne 0 ]; then
  echo "::group::Test results"
  find app/build/outputs/androidTest-results -name '*.xml' -exec cat {} \; 2>/dev/null || true
  echo "::endgroup::"
  echo "::group::Crashes and app log (logcat)"
  grep -E "AndroidRuntime|FATAL EXCEPTION|spotifycloneyt|TestRunner|MediaSessionService" logcat.txt \
    | tail -n 300 || true
  echo "::endgroup::"
fi

"$ADB" emu kill || true
exit "$RESULT"
