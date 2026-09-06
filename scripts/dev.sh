#!/usr/bin/env bash
# Dev tasks. The only place that knows how to build/test/lint this repo.
# Keep dev.ps1 behaviourally identical.
#
# Android/Gradle project — no cross-compile matrix (an APK isn't per-OS/arch),
# no Dockerfile, no compose stack. image/up/down no-op cleanly; scan's
# dependency half is flagged, not faked (see task_scan).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
DIST="$REPO_ROOT/dist"
mkdir -p "$LOG_DIR"
cd "$REPO_ROOT"

export NO_COLOR=1

# --- output helpers ---------------------------------------------------------
c() { printf '\033[%sm%s\033[0m\n' "$1" "$2"; }
step() { c '1;36' "==> $*"; }
ok()   { c '1;32' "ok: $*"; }
warn() { c '1;33' "warn: $*"; }
die()  { c '1;31' "error: $*"; exit 1; }

now() { date +%Y-%m-%dT%H:%M:%S%z; }

# Truncate this task's log with a header, then everything tees onto it.
log_begin() {
  printf '=== %s | %s ===\n' "$(now)" "$1" > "$LOG_DIR/$1.log"
}

# finish <task> <exit-code> <elapsed-seconds>
finish() {
  local task=$1 code=$2 secs=$3 status
  if [ "$code" -eq 0 ]; then status=OK; else status="FAILED (exit $code)"; fi
  printf '%s | %s | %ss | %s\n' "$(now)" "$task" "$secs" "$status" \
    | tee -a "$LOG_DIR/$task.log"
}

# Strip ANSI/CSI so logs stay readable plain text.
strip_csi() { sed -E $'s/\x1b\\[[0-9;?]*[a-zA-Z]//g'; }

# run <task> <cmd...> -- tees combined output, returns the command's code.
run() {
  local task=$1; shift
  "$@" 2>&1 | strip_csi | tee -a "$LOG_DIR/$task.log"
  return "${PIPESTATUS[0]}"
}

GRADLEW="$REPO_ROOT/gradlew"
APK_OUT="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
BUILD_NUMBER_FILE="$REPO_ROOT/BUILD_NUMBER"
APP_NAME="$(basename "$REPO_ROOT")"

# --- tasks ------------------------------------------------------------------
task_build() {
  mkdir -p "$DIST"
  run build "$GRADLEW" assembleDebug || return $?
  [ -f "$APK_OUT" ] || { warn "expected APK not found at $APK_OUT"; return 1; }

  # Auto-incrementing build number + version-tagged filename, so repeated
  # sideload installs of this personal app stay distinguishable (mirrors
  # coding-adventure/build_apk.sh's convention).
  local prev_build=0
  [ -f "$BUILD_NUMBER_FILE" ] && prev_build="$(cat "$BUILD_NUMBER_FILE")"
  local new_build=$((prev_build + 1))
  echo "$new_build" > "$BUILD_NUMBER_FILE"

  local version
  version="$(grep -m1 'versionName = ' "$REPO_ROOT/app/build.gradle.kts" | sed -E 's/.*versionName = "([^"]+)".*/\1/')"
  [ -z "$version" ] && version="0.0.0"

  local tagged="$DIST/${APP_NAME}-v${version}-build${new_build}.apk"
  cp "$APK_OUT" "$tagged"
  ok "wrote $tagged"
}

task_vet() {
  run vet "$GRADLEW" lint
}

task_test() {
  run test "$GRADLEW" test
}

task_cov() {
  run cov "$GRADLEW" testDebugUnitTest jacocoTestReport || return $?
  local xml="$REPO_ROOT/app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
  [ -f "$xml" ] || { warn "no jacoco report at $xml"; return 0; }
  # Sum the report-level <counter type="LINE"> covered/missed into one total.
  local line
  line=$(grep -o '<counter type="LINE"[^/]*/>' "$xml" | tail -1)
  local missed covered
  missed=$(printf '%s' "$line" | grep -o 'missed="[0-9]*"' | grep -o '[0-9]*')
  covered=$(printf '%s' "$line" | grep -o 'covered="[0-9]*"' | grep -o '[0-9]*')
  if [ -n "$covered" ] && [ -n "$missed" ] && [ "$((covered + missed))" -gt 0 ]; then
    local pct=$((covered * 100 / (covered + missed)))
    printf 'line coverage: %s%% (%s/%s)\n' "$pct" "$covered" "$((covered + missed))" \
      | tee -a "$LOG_DIR/cov.log"
  fi
}

task_image() {
  [ -f "$REPO_ROOT/Dockerfile" ] || { warn "no Dockerfile; skipping image"; return 0; }
  warn "Dockerfile present but this project has no container to build; skipping"
  return 0
}

# One task, every applicable check.
task_scan() {
  # No Gradle dependency-vulnerability scanner configured yet (e.g. OWASP
  # dependency-check) — flagged, not faked. Add one deliberately if wanted.
  warn "no dependency-vulnerability scanner configured for this Gradle project; skipping"
  return 0
}

task_up()   { warn "no compose stack in this project"; return 0; }
task_down() { warn "no compose stack in this project"; return 0; }

# Local only: the graph is a developer artifact, not a CI output.
task_graphify() {
  [ -n "${CI:-}" ] && { warn "graphify is local-only; skipping in CI"; return 0; }
  command -v graphify >/dev/null || { warn "graphify not on PATH; skipping"; return 0; }
  run graphify graphify update .
}

# --- dispatch ---------------------------------------------------------------
ALL="build vet test"
FULL="build vet test cov graphify"

usage() {
  cat <<EOF
usage: $(basename "$0") <task>...

  build vet test cov scan image up down graphify
  all   = $ALL
  full  = $FULL
EOF
}

expand() {
  case "$1" in
    all)  echo "$ALL" ;;
    full) echo "$FULL" ;;
    *)    echo "$1" ;;
  esac
}

[ $# -eq 0 ] && { usage; exit 0; }
case "${1:-}" in -h|--help|help) usage; exit 0 ;; esac

TASKS=""
for a in "$@"; do TASKS="$TASKS $(expand "$a")"; done

FAILED=0
for task in $TASKS; do
  type "task_$task" >/dev/null 2>&1 || die "unknown task: $task"
  step "$task"
  log_begin "$task"
  start=$SECONDS
  code=0
  "task_$task" || code=$?
  finish "$task" "$code" "$((SECONDS - start))"
  if [ "$code" -ne 0 ]; then
    FAILED=1
    warn "$task failed; stopping"
    break
  fi
  ok "$task"
done
exit "$FAILED"
