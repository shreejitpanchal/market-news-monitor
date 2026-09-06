#!/usr/bin/env bash
# Runs the Chrome desktop client: the local proxy (server/, injects your API
# keys from server/local.properties -- copy server/local.properties.example
# and fill it in first) and the webapp/ dev server, then opens Chrome.
# See CLAUDE.md's webapp/server decision for what this is and isn't.
#
# Ports are fixed, not auto-picked by webpack: proxy on 8787, webapp dev
# server on 19001 (see webDevServerPort in webapp/build.gradle.kts).
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download, plus Ktor's dependencies for the proxy -- this script
# waits for each port to actually accept connections before moving on.
# Git-bash-on-Windows parity script -- run_web.ps1 is the primary one for
# this environment.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROXY_PORT=8787
WEB_PORT=19001
DEV_SERVER_URL="http://localhost:$WEB_PORT"

cd "$REPO_ROOT"

wait_for_port() {
    local port=$1 name=$2 timeout=${3:-180} waited=0
    echo "Waiting for $name on port $port (up to ${timeout}s -- first run downloads a lot)..."
    while [ "$waited" -lt "$timeout" ]; do
        if (exec 3<>"/dev/tcp/localhost/$port") 2>/dev/null; then
            exec 3>&- 3<&- 2>/dev/null || true
            echo "$name is up on port $port."
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    echo "Timed out waiting for $name on port $port -- check its own output for the real error."
    return 1
}

echo "Starting the local proxy (server/) on port $PROXY_PORT in the background..."
"$REPO_ROOT/gradlew.bat" :server:run &
wait_for_port "$PROXY_PORT" "proxy" || true

echo "Starting the webapp dev server on port $WEB_PORT in the background..."
"$REPO_ROOT/gradlew.bat" :webapp:wasmJsBrowserDevelopmentRun &
wait_for_port "$WEB_PORT" "webapp dev server" 300 || true

echo "Opening $DEV_SERVER_URL in Chrome (webapp dev server is on port $WEB_PORT)..."
cmd.exe /c start chrome "$DEV_SERVER_URL" 2>/dev/null || cmd.exe /c start "$DEV_SERVER_URL"

wait
