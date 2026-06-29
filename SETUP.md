# Development Environment Setup

## Prerequisites

Install these tools before building NoFuzzNotes:

- JDK 21
- Android Studio or the Android SDK command-line tools
- Android SDK Platform 36
- Android SDK Build-Tools 36.x
- Gradle 8.14.x

## Clone and Open

```bash
git clone <repo-url>
cd NoFuzzNotes
```

Open the repository root in Android Studio. Let Android Studio sync the Gradle project and install any missing Android SDK packages it requests.

## Command-Line Setup

If you use the Android SDK command-line tools, set `ANDROID_HOME` to your SDK directory and ensure the SDK tools are on `PATH`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Install the required Android packages:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

For a clean Linux environment without an Android SDK, this copy-pastable setup installs the command-line tools, accepts SDK licenses, installs the required packages, and writes `local.properties` for this checkout:

```bash
set -euo pipefail

SDK_ROOT="$HOME/Android/Sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
TMP_DIR="$(mktemp -d)"

mkdir -p "$SDK_ROOT/cmdline-tools"
cd "$TMP_DIR"
curl -fL -o commandlinetools-linux.zip "$CMDLINE_TOOLS_URL"
unzip -q commandlinetools-linux.zip
rm -rf "$SDK_ROOT/cmdline-tools/latest"
mv cmdline-tools "$SDK_ROOT/cmdline-tools/latest"

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

set +o pipefail
yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null
yes | sdkmanager --sdk_root="$SDK_ROOT" "platforms;android-36" "build-tools;36.0.0" "platform-tools"
set -o pipefail

cd /workspace/NoFuzzNotes
printf 'sdk.dir=%s\n' "$SDK_ROOT" > local.properties

gradle testDebugUnitTest --no-daemon
```

## Instrumented Test Emulator Setup

Increment 11 adds Room integration tests under `app/src/androidTest`. These tests need an Android emulator or device because Room is an Android library.

The app's oldest supported Android version is API 26, from `minSdk = 26`. Use API 26 when validating minimum-version behavior.

For a clean Linux environment, this copy-pastable setup installs the emulator, API 26 platform, and a lightweight API 26 system image:

```bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

set +o pipefail
yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null
yes | sdkmanager --sdk_root="$SDK_ROOT" \
  "emulator" \
  "platforms;android-26" \
  "system-images;android-26;default;x86_64"
set -o pipefail

echo no | avdmanager create avd \
  -n nofuzz_api26 \
  -k "system-images;android-26;default;x86_64" \
  --device pixel \
  --force
```

Start the emulator headlessly:

```bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

emulator -avd nofuzz_api26 \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -gpu swiftshader_indirect \
  -no-snapshot \
  -no-accel &

adb wait-for-device

for attempt in $(seq 1 180); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
    echo "emulator booted"
    break
  fi
  sleep 2
done

if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; then
  echo "emulator did not boot in time" >&2
  exit 1
fi

adb shell input keyevent 82 || true
```

If the host has `/dev/kvm`, remove `-no-accel` for much faster emulator startup. If the host has no `/dev/kvm`, keep `-no-accel`; the emulator can still run, but Gradle's device detection may time out on slow software emulation.

Linux containers may also need graphics libraries before the emulator can start headlessly. Install them with:

```bash
apt-get update
apt-get install -y \
  libx11-xcb1 \
  libxcb1 \
  libxcb-render0 \
  libxcb-shape0 \
  libxcb-xfixes0 \
  libxrender1 \
  libxi6 \
  libxext6 \
  libgl1
```

## Running Instrumented Tests

Prefer the Gradle task when the emulator is hardware-accelerated and responsive:

```bash
gradle connectedDebugAndroidTest
```

On slow software-emulated containers, Gradle/ddmlib may report the API 26 emulator as `Unknown API Level` even after boot. In that case, build and run the instrumented APK manually:

```bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

gradle assembleDebug assembleDebugAndroidTest

adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.nofuzznotes.test/androidx.test.runner.AndroidJUnitRunner
```

Expected successful output ends with:

```text
OK (13 tests)
```

Stop the emulator after the run:

```bash
adb emu kill
```

Future agents should use the instrumented path for any code that depends on Android runtime behavior, especially Room database behavior in `app/src/androidTest/java/com/nofuzznotes/data/room`. JVM tests remain sufficient for pure Kotlin domain and service behavior in `app/src/test`.

## Build and Test

Run the JVM unit test suite:

```bash
gradle testDebugUnitTest
```

Build debug and release variants:

```bash
gradle assembleDebug assembleRelease
```

The project currently contains the core increments through Room-backed persistence. UI behavior is intentionally added in later increments.

## Continuous Integration

GitHub Actions runs the JVM and build checks on pull requests and pushes to `main`:

```bash
gradle testDebugUnitTest --no-daemon
gradle assembleDebug assembleRelease --no-daemon
```

The workflow uploads the debug APK as the `nofuzznotes-debug-apk` artifact so it can be downloaded and installed for manual testing.
