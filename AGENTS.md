# No Fuzz Notes

- Application specification is in [SPEC.md](./SPEC.md)
- Software architecture is in [ARCH.md](./ARCH.md)
- Development environment setup is in [SETUP.md](./SETUP.md)
- Always consult these documents when something is unclear
- Never implement more than a single increment
- Always complete an increment end-to-end unless something is unclear and requires resolution
- Dumb code is good. Write code obvious, don't try to be smart
- All functions/methods require at least a single-line comment explaining the purpose  
  - Never explain what, always explain why
- Assert invariants whereever possible
- Do not handle implementation bugs as errors
  - An implementation bug must never be swallowed, but should be surfaced.
  - Assertions are the right tool for this
 

## Emulator / connected Android tests

- Before running `gradle connectedDebugAndroidTest`, read `SETUP.md` and use the API 26 AVD named `nofuzz_api26` when it is available.
- Ensure the Android SDK tools are on `PATH`:
  ```bash
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
  ```
- If `emulator -avd nofuzz_api26` reports that the AVD is already running but `adb devices` shows no device, clear stale AVD locks before retrying:
  ```bash
  adb kill-server || true
  find ~/.android/avd -name '*.lock' -print -exec rm -rf {} +
  ```
- Start the emulator headlessly in software mode when `/dev/kvm` is unavailable:
  ```bash
  emulator -avd nofuzz_api26 \
    -no-window \
    -no-audio \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    -no-snapshot \
    -no-accel &
  ```
- Wait for boot before invoking Gradle:
  ```bash
  adb wait-for-device
  for attempt in $(seq 1 180); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      break
    fi
    sleep 2
  done
  adb shell input keyevent 82 || true
  ```
- Run the connected suite after the device reports API 26:
  ```bash
  adb shell getprop ro.build.version.sdk
  gradle connectedDebugAndroidTest --no-daemon
  ```
- If Gradle/ddmlib reports `Unknown API Level` on a slow software emulator, leave the emulator running, wait briefly, and retry `gradle connectedDebugAndroidTest --no-daemon` before changing code.
- If the retry still cannot use the device, use the manual instrumentation fallback from `SETUP.md` to separate infrastructure problems from test failures.
