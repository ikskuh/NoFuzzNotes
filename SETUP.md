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

## Build and Test

Run the JVM unit test suite:

```bash
gradle testDebugUnitTest
```

Build debug and release variants:

```bash
gradle assembleDebug assembleRelease
```

The project currently contains the Increment 0 skeleton only. Domain behavior and UI behavior are intentionally added in later increments.

## Continuous Integration

GitHub Actions runs the same Increment 0 checks on pull requests and pushes to `main`:

```bash
gradle testDebugUnitTest --no-daemon
gradle assembleDebug assembleRelease --no-daemon
```

The workflow uploads the debug APK as the `nofuzznotes-debug-apk` artifact so it can be downloaded and installed for manual testing.
