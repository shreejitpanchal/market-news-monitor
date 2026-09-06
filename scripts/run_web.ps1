# Runs the Chrome-viewable UI preview (webapp/ — a sample-data Compose
# Multiplatform Web module, NOT the real app; see CLAUDE.md). Starts the
# Kotlin/Wasm dev server in its own window and opens Chrome pointed at it.
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download. If Chrome loads before the server is ready, just wait
# a few seconds and refresh.

$RepoRoot = Split-Path -Parent $PSScriptRoot
$DevServerUrl = "http://localhost:8080"

Write-Host "Starting the webapp dev server (this window stays open with live logs)..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location `"$RepoRoot`"; & .\gradlew.bat :webapp:wasmJsBrowserDevelopmentRun"
)

Write-Host "Waiting for the dev server to come up..."
Start-Sleep -Seconds 8

Write-Host "Opening $DevServerUrl in Chrome..."
try {
    Start-Process -FilePath "chrome.exe" -ArgumentList $DevServerUrl -ErrorAction Stop
} catch {
    Write-Host "Could not launch chrome.exe directly (not on PATH?) -- opening via the default browser handler instead."
    Start-Process $DevServerUrl
}
