# Runs the Chrome desktop client: the local proxy (server/, injects your
# API keys from server/local.properties -- copy server/local.properties.example
# and fill it in first) and the webapp/ dev server, then opens Chrome.
# See CLAUDE.md's webapp/server decision for what this is and isn't.
#
# Ports are fixed, not auto-picked by webpack: proxy on 8787, webapp dev
# server on 19001 (see webapp/webpack.config.d/devServer.js).
#
# Each spawned window's full session is captured via Start-Transcript to
# scripts/logs/run_web_proxy.log and run_web_webapp.log -- NOT Tee-Object,
# which doubles lines and can garble encoding for native command output
# (see this repo's own dev-script logging conventions). Read those files
# (or paste their contents back) when something doesn't come up; they hold
# the actual error, not just whatever's still visible on screen.
#
# Pass -Debug for more diagnostic detail from Gradle itself
# (--info --stacktrace) when the plain logs aren't enough to see why a
# task failed or hung.
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download, plus Ktor's dependencies for the proxy -- this script
# waits for each port to actually accept connections before moving on.

param(
    [switch]$Debug
)

$RepoRoot = Split-Path -Parent $PSScriptRoot
$LogDir = Join-Path $PSScriptRoot "logs"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$ProxyLog = Join-Path $LogDir "run_web_proxy.log"
$WebappLog = Join-Path $LogDir "run_web_webapp.log"

$ProxyPort = 8787
$WebPort = 19001
# https, not http: webapp/webpack.config.d/devServer.js serves over HTTPS
# with a self-signed cert (scripts/gen_dev_cert.sh) -- Chrome will flag it
# as untrusted the first visit, click through once.
$DevServerUrl = "https://localhost:$WebPort"

$GradleArgs = ""
if ($Debug) {
    $GradleArgs = "--info --stacktrace"
    Write-Host "Debug logging enabled: $GradleArgs"
}

$CertDir = Join-Path $RepoRoot "webapp\certs"
if (-not (Test-Path (Join-Path $CertDir "localhost-cert.pem")) -or -not (Test-Path (Join-Path $CertDir "localhost-key.pem"))) {
    Write-Host "No dev cert found -- generating one via scripts/gen_dev_cert.sh (requires git-bash's openssl on PATH)..."
    & bash "$PSScriptRoot\gen_dev_cert.sh"
}

function Test-PortOpen {
    param([int]$Port)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect("localhost", $Port, $null, $null)
        $ok = $async.AsyncWaitHandle.WaitOne(500)
        $connected = $ok -and $client.Connected
        $client.Close()
        return $connected
    } catch {
        return $false
    }
}

function Wait-ForPort {
    param([int]$Port, [string]$Name, [int]$TimeoutSeconds = 180)
    Write-Host "Waiting for $Name on port $Port (up to ${TimeoutSeconds}s -- first run downloads a lot)..."
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen -Port $Port) {
            Write-Host "$Name is up on port $Port."
            return $true
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "Timed out waiting for $Name on port $Port -- see $LogDir for its real output."
    return $false
}

Write-Host "Starting the local proxy (server/) on port $ProxyPort -- logging to $ProxyLog ..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location `"$RepoRoot`"; Start-Transcript -Path `"$ProxyLog`" -Append | Out-Null; & .\gradlew.bat :server:run $GradleArgs"
)
Wait-ForPort -Port $ProxyPort -Name "proxy" | Out-Null

Write-Host "Starting the webapp dev server on port $WebPort -- logging to $WebappLog ..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location `"$RepoRoot`"; Start-Transcript -Path `"$WebappLog`" -Append | Out-Null; & .\gradlew.bat :webapp:wasmJsBrowserDevelopmentRun $GradleArgs"
)
$ready = Wait-ForPort -Port $WebPort -Name "webapp dev server" -TimeoutSeconds 300

Write-Host "Opening $DevServerUrl in Chrome (webapp dev server is on port $WebPort)..."
try {
    Start-Process -FilePath "chrome.exe" -ArgumentList $DevServerUrl -ErrorAction Stop
} catch {
    Write-Host "Could not launch chrome.exe directly (not on PATH?) -- opening via the default browser handler instead."
    Start-Process $DevServerUrl
}

if (-not $ready) {
    Write-Host "Note: the dev server never reported ready on port $WebPort before Chrome opened -- check $WebappLog for the real error, then refresh $DevServerUrl once it's up."
}

Write-Host "Logs: $ProxyLog and $WebappLog"
