#!/usr/bin/env bash
# Runs the Chrome desktop client: the local proxy (server/, injects your API
# keys from server/local.properties -- copy server/local.properties.example
# and fill it in first) and the webapp/ dev server, then opens Chrome.
# See CLAUDE.md's webapp/server decision for what this is and isn't.
#
# Ports are fixed, not auto-picked by webpack: proxy on 8787, webapp dev
# server on 19001 (see webapp/webpack.config.d/devServer.js).
#
# Both Gradle processes' combined output is always captured to
# scripts/logs/run_web_proxy.log and run_web_webapp.log (ANSI stripped),
# in addition to printing live -- read those files (or paste their
# contents back) when something doesn't come up; they hold the actual
# error, not just whatever's still visible in the terminal scrollback.
#
# Pass --debug for more diagnostic detail from Gradle itself
# (--info --stacktrace) when the plain logs aren't enough to see why a
# task failed or hung.
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download, plus Ktor's dependencies for the proxy -- this script
# waits for each port to actually accept connections before moving on.
#
# Ctrl+C here stops this script's own two backgrounded Gradle invocations
# (via the trap below), but Gradle daemons are long-lived by design and
# may keep the actual server/dev-server processes alive regardless --
# run scripts/stop_web.sh afterward if ports 8787/19001 are still in use.
# Git-bash-on-Windows parity script -- run_web.ps1 is the primary one for
# this environment.
set -uo pipefail

trap 'echo; echo "Stopping..."; jobs -p | xargs -r kill 2>/dev/null; exit 0' INT TERM

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
PROXY_PORT=8787
WEB_PORT=19001
# https, not http: webapp/webpack.config.d/devServer.js serves over HTTPS
# with a self-signed cert (scripts/gen_dev_cert.sh) -- Chrome will flag it
# as untrusted the first visit, click through once.
DEV_SERVER_URL="https://localhost:$WEB_PORT"

mkdir -p "$LOG_DIR"
cd "$REPO_ROOT"
export NO_COLOR=1

if [ ! -f "$REPO_ROOT/webapp/certs/localhost-cert.pem" ] || [ ! -f "$REPO_ROOT/webapp/certs/localhost-key.pem" ]; then
    echo "No dev cert found -- generating one via scripts/gen_dev_cert.sh..."
    "$SCRIPT_DIR/gen_dev_cert.sh"
fi

GRADLE_ARGS=()
for arg in "$@"; do
    case "$arg" in
        --debug) GRADLE_ARGS+=(--info --stacktrace) ;;
    esac
done
if [ "${#GRADLE_ARGS[@]}" -gt 0 ]; then
    echo "Debug logging enabled: ${GRADLE_ARGS[*]}"
fi

# Strip ANSI/CSI so the log files stay readable plain text (same approach as dev.sh).
strip_csi() { sed -E $'s/\x1b\\[[0-9;?]*[a-zA-Z]//g'; }

PROXY_LOG="$LOG_DIR/run_web_proxy.log"
WEBAPP_LOG="$LOG_DIR/run_web_webapp.log"
: > "$PROXY_LOG"
: > "$WEBAPP_LOG"

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
    echo "Timed out waiting for $name on port $port -- see $LOG_DIR for its real output."
    return 1
}

echo "Starting the local proxy (server/) on port $PROXY_PORT in the background (log: $PROXY_LOG)..."
"$REPO_ROOT/gradlew.bat" :server:run "${GRADLE_ARGS[@]}" 2>&1 | strip_csi | tee "$PROXY_LOG" &
wait_for_port "$PROXY_PORT" "proxy" || true

echo "Starting the webapp dev server on port $WEB_PORT in the background (log: $WEBAPP_LOG)..."
"$REPO_ROOT/gradlew.bat" :webapp:wasmJsBrowserDevelopmentRun "${GRADLE_ARGS[@]}" 2>&1 | strip_csi | tee "$WEBAPP_LOG" &
wait_for_port "$WEB_PORT" "webapp dev server" 300 || true

echo "Opening $DEV_SERVER_URL in Chrome (webapp dev server is on port $WEB_PORT)..."
cmd.exe /c start chrome "$DEV_SERVER_URL" 2>/dev/null || cmd.exe /c start "$DEV_SERVER_URL"

echo "Logs: $PROXY_LOG and $WEBAPP_LOG (tail -f to follow live)."
echo "Ctrl+C stops this script; run scripts/stop_web.sh afterward if ports $PROXY_PORT/$WEB_PORT are still in use."
wait
