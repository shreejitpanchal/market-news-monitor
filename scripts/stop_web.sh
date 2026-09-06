#!/usr/bin/env bash
# Stops the Chrome desktop client's local processes: whatever is listening
# on the proxy (8787) and webapp dev server (19001) ports.
#
# Kills by port, not by a PID remembered from run_web.sh, because Gradle
# daemons and the dev server process outlive the shell that started them
# by design -- closing the git-bash window that ran run_web.sh does NOT
# stop them, and Ctrl+C on that script only stops the wrapper, not its
# backgrounded Gradle invocations. This works regardless of which script
# or window actually started them.
set -uo pipefail

stop_port() {
    local port=$1 name=$2
    local pids
    pids=$(netstat -ano 2>/dev/null | grep -i "TCP" | grep -E ":$port[[:space:]]" | grep -i "LISTENING" | awk '{print $NF}' | sort -u)
    if [ -z "$pids" ]; then
        echo "$name (port $port): nothing listening."
        return 0
    fi
    for pid in $pids; do
        echo "Stopping $name (port $port, PID $pid)..."
        # MSYS_NO_PATHCONV belt-and-suspenders alongside the doubled
        # slashes -- see run_web.sh's comment on the same class of bug.
        MSYS_NO_PATHCONV=1 taskkill //PID "$pid" //F >/dev/null 2>&1 || echo "  could not stop PID $pid (already gone?)"
    done
}

stop_port 8787 "proxy"
stop_port 19001 "webapp dev server"

echo "Done. Gradle daemons themselves are left running (safe -- they're reused across builds); run './gradlew.bat --stop' if you also want those gone."
