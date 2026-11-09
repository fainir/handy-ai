#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -x /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi
if [[ -z "${ANDROID_HOME:-}" && -d /opt/homebrew/share/android-commandlinetools/platforms ]]; then
  export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
fi
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug assembleRelease bundleRelease
