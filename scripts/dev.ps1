#requires -Version 5.1
# Dev tasks. Behaviourally identical to dev.sh -- same task names, same
# gating, same footer format.
#
# Android/Gradle project — no cross-compile matrix, no Dockerfile, no compose
# stack. image/up/down no-op cleanly; scan's dependency half is flagged, not
# faked (see Task-scan).
[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Tasks)

$ErrorActionPreference = 'Continue'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot  = Split-Path -Parent $ScriptDir
$LogDir    = Join-Path $ScriptDir 'logs'
$Dist      = Join-Path $RepoRoot 'dist'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Set-Location $RepoRoot
$env:NO_COLOR = '1'

# --- output helpers ---------------------------------------------------------
function Step { param($m) Write-Host "==> $m" -ForegroundColor Cyan }
function Ok   { param($m) Write-Host "ok: $m"    -ForegroundColor Green }
function Warn { param($m) Write-Host "warn: $m"  -ForegroundColor Yellow }
function Die  { param($m) Write-Host "error: $m" -ForegroundColor Red; exit 1 }

function Get-Now { (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz') }
function Get-Log { param($Task) Join-Path $LogDir "$Task.log" }

function Start-TaskLog {
  param($Task)
  Set-Content -Path (Get-Log $Task) -Encoding utf8 `
    -Value ("=== {0} | {1} ===" -f (Get-Now), $Task)
}

function Write-Finish {
  param([string]$Task, [int]$Code, [int]$Seconds)
  $status = if ($Code -eq 0) { 'OK' } else { "FAILED (exit $Code)" }
  $line = '{0} | {1} | {2}s | {3}' -f (Get-Now), $Task, $Seconds, $status
  Add-Content -Path (Get-Log $Task) -Value $line -Encoding utf8
  Write-Host $line
}

# Capture once, write once. "$_" flattens stderr ErrorRecords; -Width stops
# column wrap; the CSI strip keeps the file readable plain text.
function Invoke-Logged {
  param([string]$Task, [string]$Exe, [string[]]$CmdArgs)
  $out = (& $Exe @CmdArgs 2>&1 | ForEach-Object { "$_" } | Out-String -Width 4096)
  $code = $LASTEXITCODE
  $out = $out -replace "\x1b\[[0-9;?]*[a-zA-Z]", ""
  Add-Content -Path (Get-Log $Task) -Value $out -Encoding utf8
  Write-Host $out
  return $code
}

$GradleW = Join-Path $RepoRoot 'gradlew.bat'
$ApkOut  = Join-Path $RepoRoot 'app\build\outputs\apk\debug\app-debug.apk'
$AppName = Split-Path -Leaf $RepoRoot
$BuildNumberFile = Join-Path $RepoRoot 'BUILD_NUMBER'

# --- tasks ------------------------------------------------------------------
# PowerShell returns EVERY uncaptured pipeline value, not just `return`: route
# commands through Invoke-Logged, or pipe anything you don't return to Out-Null.
function Task-build {
  New-Item -ItemType Directory -Force -Path $Dist | Out-Null
  $code = Invoke-Logged 'build' $GradleW @('assembleDebug')
  if ($code -ne 0) { return $code }
  if (-not (Test-Path $ApkOut)) { Warn "expected APK not found at $ApkOut"; return 1 }

  # Auto-incrementing build number + version-tagged filename, so repeated
  # sideload installs of this personal app stay distinguishable (mirrors
  # coding-adventure/build_apk.sh's convention).
  $prevBuild = 0
  if (Test-Path $BuildNumberFile) { $prevBuild = [int](Get-Content $BuildNumberFile -Raw).Trim() }
  $newBuild = $prevBuild + 1
  Set-Content -Path $BuildNumberFile -Value $newBuild -Encoding utf8

  $versionLine = Select-String -Path (Join-Path $RepoRoot 'app\build.gradle.kts') -Pattern 'versionName = "([^"]+)"' | Select-Object -First 1
  $version = if ($versionLine) { $versionLine.Matches[0].Groups[1].Value } else { '0.0.0' }

  $tagged = Join-Path $Dist "$AppName-v$version-build$newBuild.apk"
  Copy-Item -Path $ApkOut -Destination $tagged -Force
  Ok "wrote $tagged"
  return 0
}

function Task-vet  { return (Invoke-Logged 'vet'  $GradleW @('lint')) }
function Task-test { return (Invoke-Logged 'test' $GradleW @('test')) }

function Task-cov {
  $code = Invoke-Logged 'cov' $GradleW @('testDebugUnitTest', 'jacocoTestReport')
  if ($code -ne 0) { return $code }
  $xmlPath = Join-Path $RepoRoot 'app\build\reports\jacoco\jacocoTestReport\jacocoTestReport.xml'
  if (-not (Test-Path $xmlPath)) { Warn "no jacoco report at $xmlPath"; return 0 }
  $content = Get-Content $xmlPath -Raw
  $matches = [regex]::Matches($content, '<counter type="LINE"[^/]*/>')
  if ($matches.Count -eq 0) { return 0 }
  $last = $matches[$matches.Count - 1].Value
  $missed  = [int]([regex]::Match($last, 'missed="(\d+)"').Groups[1].Value)
  $covered = [int]([regex]::Match($last, 'covered="(\d+)"').Groups[1].Value)
  $totalLines = $covered + $missed
  if ($totalLines -gt 0) {
    $pct = [math]::Floor(($covered * 100) / $totalLines)
    $line = "line coverage: $pct% ($covered/$totalLines)"
    Add-Content -Path (Get-Log 'cov') -Value $line -Encoding utf8
    Write-Host $line
  }
  return 0
}

function Task-image {
  if (-not (Test-Path (Join-Path $RepoRoot 'Dockerfile'))) {
    Warn 'no Dockerfile; skipping image'; return 0
  }
  Warn 'Dockerfile present but this project has no container to build; skipping'
  return 0
}

function Task-scan {
  # No Gradle dependency-vulnerability scanner configured yet (e.g. OWASP
  # dependency-check) — flagged, not faked. Add one deliberately if wanted.
  Warn 'no dependency-vulnerability scanner configured for this Gradle project; skipping'
  return 0
}

function Task-up   { Warn 'no compose stack in this project'; return 0 }
function Task-down { Warn 'no compose stack in this project'; return 0 }

function Task-graphify {
  if ($env:CI) { Warn 'graphify is local-only; skipping in CI'; return 0 }
  if (-not (Get-Command graphify -ErrorAction SilentlyContinue)) {
    Warn 'graphify not on PATH; skipping'; return 0
  }
  return (Invoke-Logged 'graphify' 'graphify' @('update', '.'))
}

# --- dispatch ---------------------------------------------------------------
$All  = @('build', 'vet', 'test')
$Full = @('build', 'vet', 'test', 'cov', 'graphify')

function Show-Usage {
  @"
usage: dev.ps1 <task>...

  build vet test cov scan image up down graphify
  all   = $($All -join ' ')
  full  = $($Full -join ' ')
"@ | Write-Host
}

if (-not $Tasks -or $Tasks[0] -in @('-h', '--help', 'help')) { Show-Usage; exit 0 }

$queue = @()
foreach ($t in $Tasks) {
  switch ($t) { 'all' { $queue += $All } 'full' { $queue += $Full } default { $queue += $t } }
}

$failed = 0
foreach ($task in $queue) {
  if (-not (Get-Command "Task-$task" -ErrorAction SilentlyContinue)) { Die "unknown task: $task" }
  Step $task
  Start-TaskLog $task
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $code = 0
  try { $code = & "Task-$task" } catch { $code = 1 }
  if ($null -eq $code) { $code = 0 }
  $sw.Stop()
  Write-Finish -Task $task -Code $code -Seconds ([int]$sw.Elapsed.TotalSeconds)
  if ($code -ne 0) { $failed = 1; Warn "$task failed; stopping"; break }
  Ok $task
}
exit $failed
