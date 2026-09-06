#!/usr/bin/env bash
# Runs the Chrome desktop client: the local proxy (server/, injects your API
# keys from server/local.properties -- copy server/local.properties.example
# and fill it in first) and the webapp/ dev server, then opens Chrome.
# See CLAUDE.md's webapp/server decision for what this is and isn't.
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download, plus Ktor's dependencies for the proxy. Git-bash-on-
# Windows parity script -- run_web.ps1 is the primary one for this
# environment.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEV_SERVER_URL="http://localhost:8080"

cd "$REPO_ROOT"

echo "Starting the local proxy (server/) in the background..."
"$REPO_ROOT/gradlew.bat" :server:run &

echo "Waiting for the proxy to come up..."
sleep 5

echo "Starting the webapp dev server in the background..."
"$REPO_ROOT/gradlew.bat" :webapp:wasmJsBrowserDevelopmentRun &

echo "Waiting for the dev server to come up..."
sleep 8

echo "Opening $DEV_SERVER_URL in Chrome..."
cmd.exe /c start chrome "$DEV_SERVER_URL" 2>/dev/null || cmd.exe /c start "$DEV_SERVER_URL"

wait
