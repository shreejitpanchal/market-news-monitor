#!/usr/bin/env bash
# Runs the Chrome-viewable UI preview (webapp/ -- a sample-data Compose
# Multiplatform Web module, NOT the real app; see CLAUDE.md). Starts the
# Kotlin/Wasm dev server and opens Chrome pointed at it.
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download. Git-bash-on-Windows parity script -- run_web.ps1 is the
# primary one for this environment.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEV_SERVER_URL="http://localhost:8080"

cd "$REPO_ROOT"

echo "Starting the webapp dev server in the background..."
"$REPO_ROOT/gradlew.bat" :webapp:wasmJsBrowserDevelopmentRun &

echo "Waiting for the dev server to come up..."
sleep 8

echo "Opening $DEV_SERVER_URL in Chrome..."
cmd.exe /c start chrome "$DEV_SERVER_URL" 2>/dev/null || cmd.exe /c start "$DEV_SERVER_URL"

wait
