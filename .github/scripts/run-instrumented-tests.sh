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
"$EMULATOR" -accel-check 2>&1 || true
"$EMULATOR" -list-avds 2>&1 || true
df -h "$HOME" || true
echo "::endgroup::"

"$EMULATOR" -avd ci -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -memory 4096 -camera-back none -camera-front none \
  > emulator.log 2>&1 &
EMULATOR_PID=$!

is_booted() {
  [ "$(timeout 30 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]
}

# sys.boot_completed can be set before system_server is fully up (or survive a runtime
# restart), so also require the package and activity managers to answer.
is_ready() {
  is_booted &&
    timeout 30 "$ADB" shell pm path android 2>/dev/null | grep -q '^package:' &&
    timeout 30 "$ADB" shell am get-current-user 2>/dev/null | tr -d '\r' | grep -qE '^[0-9]+$'
}

# Polls the given check until it succeeds, failing fast if the emulator process exits.
wait_for() {
  local description="$1" check="$2" deadline=$((SECONDS + $3))
  until "$check"; do
    if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
      echo "::error::The emulator exited while waiting until $description."
      print_file emulator.log
      exit 1
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "::error::Timed out waiting until $description."
      print_file emulator.log
      exit 1
    fi
    sleep 5
  done
}

wait_for "the emulator booted" is_booted 900
wait_for "system services were ready" is_ready 300
# Check again after a pause to ride out a system_server restart right after boot.
sleep 15
wait_for "system services were ready (second check)" is_ready 300
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

# The Gradle task can succeed even when the APKs could not be installed, so require results.
if ! python3 - <<'EOF'
import glob, sys, xml.etree.ElementTree as ET

cases = {}
for path in glob.glob("app/build/outputs/androidTest-results/**/*.xml", recursive=True):
    for case in ET.parse(path).getroot().iter("testcase"):
        failed = case.find("failure") is not None or case.find("error") is not None
        key = (case.get("classname"), case.get("name"))
        cases[key] = cases.get(key, False) or failed
failed = sorted(f"{cls}.{name}" for (cls, name), bad in cases.items() if bad)
print(f"Instrumented tests: {len(cases)} run, {len(failed)} failed")
for name in failed:
    print(f"  FAILED {name}")
sys.exit(0 if cases and not failed else 1)
EOF
then
  echo "::error::Instrumented tests did not run or did not all pass."
  RESULT=1
fi

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
