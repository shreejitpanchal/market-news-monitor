#!/usr/bin/env bash
# Builds a debug APK for manual sideload install.
#
# Delegates to scripts/dev.sh's `build` task rather than reimplementing the
# Gradle invocation here -- scripts/dev.sh is the single place that knows how
# to build this repo (see the project's dev-script conventions), so there's
# exactly one build implementation to keep in sync as the Gradle setup
# evolves. This script is just a convenience entry point at the repo root,
# matching coding-adventure's build_apk.sh.
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "No Gradle wrapper jar found at $WRAPPER_JAR."
    echo "Generate it once, then re-run this script:"
    echo "  - Open the project root in Android Studio and let it sync, or"
    echo "  - Run 'gradle wrapper --gradle-version 8.9' if you already have Gradle installed."
    exit 1
fi

echo "Building debug APK via scripts/dev.sh build..."
echo

if ! ./scripts/dev.sh build; then
    echo
    echo "Build failed -- see scripts/logs/build.log for details."
    exit 1
fi

APK="$(ls -t dist/market-news-monitor-v*-build*.apk 2>/dev/null | head -1)"
if [ -z "$APK" ]; then
    echo "Build reported success but no APK was found in dist/ -- something's off."
    exit 1
fi

echo
echo "Done -- APK at $APK"
echo "Install it on a connected device with: adb install \"$APK\""
echo "Or copy the file to your phone and open it there to sideload."
