#!/usr/bin/env bash
# Launches the household_tablet AVD with the performance settings that matter on this
# machine: host GPU passthrough (the AMD Radeon here supports it, and software rendering
# via swiftshader was slow enough to trigger repeated SystemUI ANR dialogs), no boot
# animation, and system animations disabled once booted (removes a common source of jank
# on a 2-core host). Installs/reinstalls the current debug build and launches the app.
#
# Usage: ./run-emulator.sh [avd-name]
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_HOME
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.config/.android/avd}"
export DISPLAY="${DISPLAY:-:0.0}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

AVD_NAME="${1:-household_tablet}"
ADB="$ANDROID_HOME/platform-tools/adb"
APK="$(dirname "$0")/app/build/outputs/apk/debug/app-debug.apk"

if pgrep -f "qemu-system-x86_64.*-avd $AVD_NAME" > /dev/null; then
    echo "Emulator '$AVD_NAME' is already running."
else
    echo "Starting emulator '$AVD_NAME' with host GPU acceleration..."
    # -no-snapshot: this environment has hit corrupted-snapshot boot failures before
    # (goldfish_pipe state errors); always cold-booting trades a slower boot for reliability.
    nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD_NAME" -gpu host -no-boot-anim -no-snapshot \
        > /tmp/emulator_"$AVD_NAME".log 2>&1 &
    disown
fi

echo "Waiting for device..."
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 3
done
echo "Boot complete."

"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0

if [ -f "$APK" ]; then
    echo "Installing $APK..."
    "$ADB" install -r "$APK"
    "$ADB" shell am start -n com.jpdrw.household/.MainActivity
else
    echo "No debug APK found at $APK — build one first with ./gradlew assembleDebug"
fi
