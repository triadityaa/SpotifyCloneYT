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

# Older preinstalled cmdline-tools (e.g. 12.0 on ubuntu-24.04) don't handle "major.minor"
# platform packages such as android-37.0 correctly, so create the AVD with the newest version.
CMDLINE_TOOLS=$("$SDKMANAGER" --list 2>/dev/null \
  | grep -oE "cmdline-tools;[0-9]+\.[0-9]+" | sort -uV | tail -n 1 || true)

# `yes` is killed by SIGPIPE when sdkmanager exits, so only the last command's status counts here.
set +o pipefail
yes | "$SDKMANAGER" --install "$IMAGE" "emulator" "platform-tools" ${CMDLINE_TOOLS:+"$CMDLINE_TOOLS"} > /dev/null
if [ -n "$CMDLINE_TOOLS" ] && [ -x "$SDK/cmdline-tools/${CMDLINE_TOOLS#cmdline-tools;}/bin/avdmanager" ]; then
  AVDMANAGER="$SDK/cmdline-tools/${CMDLINE_TOOLS#cmdline-tools;}/bin/avdmanager"
fi
echo "Creating the AVD with $AVDMANAGER"
echo "no" | "$AVDMANAGER" create avd --force --name ci --package "$IMAGE"
set -o pipefail

# The default userdata partition of recent images is too small to install the APKs after the
# first boot ("Requested internal only, but not enough space"), so give it 6 GB.
AVD_CONFIG="$ANDROID_AVD_HOME/ci.avd/config.ini"
sed -i '/^disk\.dataPartition\.size=/d' "$AVD_CONFIG"
echo "disk.dataPartition.size=6442450944" >> "$AVD_CONFIG"

echo "::group::Emulator diagnostics"
ls -l /dev/kvm || true
"$EMULATOR" -accel-check 2>&1 || true
"$EMULATOR" -list-avds 2>&1 || true
df -h "$HOME" || true
echo "::endgroup::"

"$EMULATOR" -avd ci -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -cores 4 -memory 4096 -camera-back none -camera-front none \
  > emulator.log 2>&1 &
EMULATOR_PID=$!

is_booted() {
  [ "$(timeout 30 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]
}

# sys.boot_completed can be set before system_server is fully up (or survive a runtime
# restart), so also require the package and activity managers to answer.
is_user_unlocked() {
  local ce_available
  ce_available=$(timeout 30 "$ADB" shell getprop sys.user.0.ce_available 2>/dev/null | tr -d '\r')
  { [ -z "$ce_available" ] || [ "$ce_available" = true ]; } &&
    timeout 30 "$ADB" shell ls /sdcard/ > /dev/null 2>&1
}

is_ready() {
  is_booted &&
    timeout 30 "$ADB" shell pm path android 2>/dev/null | grep -q '^package:' &&
    timeout 30 "$ADB" shell am get-current-user 2>/dev/null | tr -d '\r' | grep -qE '^[0-9]+$' &&
    is_user_unlocked
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

print_device_diagnostics() {
  echo "::group::Crash buffer (logcat -b crash)"
  timeout 30 "$ADB" logcat -d -b crash 2>/dev/null | tail -n 200 || true
  echo "::endgroup::"
  echo "::group::System log (restarts and fatal errors)"
  timeout 30 "$ADB" logcat -d 2>/dev/null \
    | grep -E "FATAL|Fatal signal|Abort message|SurfaceFlinger|Watchdog|system_server|Zygote" \
    | tail -n 200 || true
  echo "::endgroup::"
}

system_server_pid() {
  timeout 30 "$ADB" shell pidof system_server 2>/dev/null | tr -d '\r'
}

# The first boot of recent images (seen on API 37) can restart system_server after
# sys.boot_completed is set, which makes APK installs fail with "Can't find service: package".
# Wait until system_server keeps the same PID for 60 seconds with its services answering.
wait_for_stable_system() {
  local deadline=$((SECONDS + 600)) stable_since=$SECONDS last_pid="" pid
  while true; do
    pid=$(system_server_pid || true)
    if [ -z "$pid" ] || [ "$pid" != "$last_pid" ] || ! is_ready; then
      if [ -n "$last_pid" ] && [ -n "$pid" ] && [ "$pid" != "$last_pid" ]; then
        echo "system_server restarted (pid $last_pid -> $pid)"
      fi
      last_pid=$pid
      stable_since=$SECONDS
    elif [ $((SECONDS - stable_since)) -ge 60 ]; then
      echo "system_server is stable (pid $pid)"
      return 0
    fi
    if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
      echo "::error::The emulator exited while waiting for a stable system."
      print_file emulator.log
      exit 1
    fi
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "::error::system_server did not stay up for 60 seconds within 10 minutes."
      print_file emulator.log
      print_device_diagnostics
      exit 1
    fi
    sleep 5
  done
}

wait_for_stable_system
echo "Emulator ready: Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r')" \
  "(API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r'))"
"$ADB" shell df -h /data || true

"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell input keyevent 82 || true

"$ADB" logcat -c || true
"$ADB" logcat > logcat.txt 2>&1 &
LOGCAT_PID=$!

# Prints a summary of the result XML. Exit status: 0 all passed, 1 a test failed, 2 none ran.
# Needed because the Gradle task can succeed even when the APKs could not be installed.
check_results() {
  python3 - <<'EOF'
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
sys.exit(2 if not cases else 1 if failed else 0)
EOF
}

run_tests() {
  rm -rf app/build/outputs/androidTest-results
  set +e
  timeout 1800 ./gradlew --stacktrace connectedDebugAndroidTest
  GRADLE_STATUS=$?
  check_results
  RESULTS_STATUS=$?
  set -e
}

run_tests
# Retry once only when no test ran at all (the APKs could not be installed because
# system_server restarted). A failing test is never retried.
if [ "$RESULTS_STATUS" -eq 2 ]; then
  echo "::warning::No instrumented test ran. Waiting for a stable system and retrying once."
  wait_for_stable_system
  run_tests
fi

kill "$LOGCAT_PID" 2>/dev/null || true

RESULT=0
if [ "$GRADLE_STATUS" -ne 0 ] || [ "$RESULTS_STATUS" -ne 0 ]; then
  echo "::error::Instrumented tests did not run or did not all pass."
  RESULT=1
  echo "::group::Test results"
  find app/build/outputs/androidTest-results -name '*.xml' -exec cat {} \; 2>/dev/null || true
  echo "::endgroup::"
  echo "::group::Crashes, restarts and app log (logcat)"
  grep -E "AndroidRuntime|FATAL EXCEPTION|Fatal signal|crash_dump|Watchdog|system_server|Zygote|lowmemorykiller|spotifycloneyt|TestRunner|MediaSessionService" \
    logcat.txt | tail -n 300 || true
  echo "::endgroup::"
  print_device_diagnostics
fi

"$ADB" emu kill || true
exit "$RESULT"
