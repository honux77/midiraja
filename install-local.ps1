#Requires -Version 5.1
<#
.SYNOPSIS
    Builds midiraja from source and installs midra to ~/bin.
.DESCRIPTION
    Runs Gradle installDist, copies the distribution to ~/bin/midrax,
    and writes a midra.bat launcher to ~/bin.
.PARAMETER BinDir
    Directory to install the launcher. Defaults to ~/bin.
.EXAMPLE
    .\install-local.ps1
.EXAMPLE
    .\install-local.ps1 -BinDir "C:\tools\bin"
#>

param(
    [string]$BinDir = (Join-Path $env:USERPROFILE "bin")
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$DistDir     = Join-Path $ProjectRoot "build\install\midrax"
$TargetDir   = Join-Path $BinDir "midrax"
$LauncherBat = Join-Path $BinDir "midra.bat"
$JavaHome    = "C:\Program Files\Java\jdk-25.0.2"

Write-Host "=== midiraja local install ===" -ForegroundColor Cyan

# 1. Build
Write-Host "`n[1/3] Building..." -ForegroundColor Yellow
$gradlew = Join-Path $ProjectRoot "gradlew.bat"
& $gradlew installDist -x test -x downloadFreepats -x setupFreepats -x downloadFluidR3Sf3
if ($LASTEXITCODE -ne 0) { Write-Error "Gradle build failed."; exit 1 }

# 2. Copy distribution
Write-Host "`n[2/3] Installing to $TargetDir..." -ForegroundColor Yellow
if (Test-Path $TargetDir) {
    Remove-Item -Recurse -Force $TargetDir
}
Copy-Item -Recurse $DistDir $TargetDir

# 3. Write launcher
Write-Host "[3/3] Writing launcher $LauncherBat..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $BinDir | Out-Null

@"
@echo off
setlocal enabledelayedexpansion

set "JAVA=$JavaHome\bin\java.exe"
set "LIB=%~dp0midrax\lib"

set "CP="
for %%f in ("%LIB%\*.jar") do (
    if "!CP!"=="" (set "CP=%%~f") else (set "CP=!CP!;%%~f")
)

"%JAVA%" --enable-native-access=ALL-UNNAMED --enable-preview -cp "!CP!" com.fupfin.midiraja.MidirajaCommand %*
"@ | Set-Content -Encoding ASCII $LauncherBat

Write-Host "`n=== Done ===" -ForegroundColor Green
Write-Host "  launcher : $LauncherBat"
Write-Host "  dist     : $TargetDir"

# Verify
$version = & $LauncherBat --version 2>&1 | Select-Object -First 1
Write-Host "  version  : $version" -ForegroundColor Cyan
