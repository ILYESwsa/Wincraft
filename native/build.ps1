#!/usr/bin/env pwsh
# Builds the wincraft native DLL locally on Windows and stages it where
# the Gradle build expects it (native/target/release/wincraft.dll).
#
# Requires: Rust toolchain (rustup, with x86_64-pc-windows-msvc target),
# and the MSVC Build Tools (for the linker).
#
# Usage: pwsh native/build.ps1 [-Debug]

param(
    [switch]$Debug
)

$ErrorActionPreference = "Stop"
Push-Location $PSScriptRoot

try {
    if ($Debug) {
        Write-Host "Building wincraft.dll (debug)..." -ForegroundColor Cyan
        cargo build --target x86_64-pc-windows-msvc
        $src = "target/x86_64-pc-windows-msvc/debug/wincraft.dll"
        $dstDir = "target/debug"
    } else {
        Write-Host "Building wincraft.dll (release)..." -ForegroundColor Cyan
        cargo build --release --target x86_64-pc-windows-msvc
        $src = "target/x86_64-pc-windows-msvc/release/wincraft.dll"
        $dstDir = "target/release"
    }

    if (-not (Test-Path $dstDir)) {
        New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
    }
    Copy-Item $src "$dstDir/wincraft.dll" -Force

    Write-Host "Built: native/$dstDir/wincraft.dll" -ForegroundColor Green
    Write-Host "Run '.\gradlew.bat :fabric:build' from the repo root to package it into the mod jar."
} finally {
    Pop-Location
}
