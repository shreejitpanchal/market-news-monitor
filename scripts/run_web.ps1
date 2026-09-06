# Runs the Chrome desktop client: the local proxy (server/, injects your
# API keys from server/local.properties -- copy server/local.properties.example
# and fill it in first) and the webapp/ dev server, then opens Chrome.
# See CLAUDE.md's webapp/server decision for what this is and isn't.
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download, plus Ktor's dependencies for the proxy. If Chrome loads
# before the dev server is ready, just wait a few seconds and refresh.

$RepoRoot = Split-Path -Parent $PSScriptRoot
$DevServerUrl = "http://localhost:8080"

Write-Host "Starting the local proxy (server/) -- this window stays open with live logs..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location `"$RepoRoot`"; & .\gradlew.bat :server:run"
)

Write-Host "Waiting for the proxy to come up..."
Start-Sleep -Seconds 5

Write-Host "Starting the webapp dev server -- this window stays open with live logs..."
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
