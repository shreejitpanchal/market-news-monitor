# Runs the Chrome desktop client: the local proxy (server/, injects your
# API keys from server/local.properties -- copy server/local.properties.example
# and fill it in first) and the webapp/ dev server, then opens Chrome.
# See CLAUDE.md's webapp/server decision for what this is and isn't.
#
# Ports are fixed, not auto-picked by webpack, so this script and Chrome
# always agree on where things are: proxy on 8787, webapp dev server on
# 19001 (see webDevServerPort in webapp/build.gradle.kts).
#
# First run will take a while: the Kotlin/Wasm toolchain + webpack tooling
# have to download, plus Ktor's dependencies for the proxy -- this script
# waits for each port to actually accept connections before moving on,
# rather than guessing a fixed delay.

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ProxyPort = 8787
$WebPort = 19001
$DevServerUrl = "http://localhost:$WebPort"

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
    Write-Host "Timed out waiting for $Name on port $Port -- check its own window for the real error."
    return $false
}

Write-Host "Starting the local proxy (server/) on port $ProxyPort -- this window stays open with live logs..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location `"$RepoRoot`"; & .\gradlew.bat :server:run"
)
Wait-ForPort -Port $ProxyPort -Name "proxy" | Out-Null

Write-Host "Starting the webapp dev server on port $WebPort -- this window stays open with live logs..."
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location `"$RepoRoot`"; & .\gradlew.bat :webapp:wasmJsBrowserDevelopmentRun"
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
    Write-Host "Note: the dev server never reported ready on port $WebPort before Chrome opened -- if the page is blank, check that window's output, then refresh $DevServerUrl once it's up."
}
