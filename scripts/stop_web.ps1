# Stops the Chrome desktop client's local processes: whatever is listening
# on the proxy (8787) and webapp dev server (19001) ports.
#
# Kills by port, not a PID remembered from run_web.ps1, because Gradle
# daemons and the dev server process outlive the windows that started them
# by design -- closing those windows does not necessarily stop the
# underlying java.exe processes. This works regardless of which script
# actually started them.

function Stop-Port {
    param([int]$Port, [string]$Name)
    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $connections) {
        Write-Host "$Name (port $Port): nothing listening."
        return
    }
    $processIds = $connections | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $processIds) {
        Write-Host "Stopping $Name (port $Port, PID $processId)..."
        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
        } catch {
            Write-Host "  could not stop PID $processId (already gone?)"
        }
    }
}

Stop-Port -Port 8787 -Name "proxy"
Stop-Port -Port 19001 -Name "webapp dev server"

Write-Host "Done. Gradle daemons themselves are left running (safe -- they're reused across builds); run '.\gradlew.bat --stop' if you also want those gone."
