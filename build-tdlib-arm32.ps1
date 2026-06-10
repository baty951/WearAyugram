# TDLib Android Build Script - armeabi-v7a (32-bit ARM watches)
param(
    [string]$TdlibSrc  = "$env:TEMP\tdlib-src",
    [string]$OutputDir = "$env:TEMP\tdlib-out-arm32",
    [string]$ProjectDir = $PSScriptRoot
)

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host "" ; Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    OK: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "    FAIL: $msg" -ForegroundColor Red; exit 1 }

Write-Step "Checking prerequisites"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Write-Fail "Docker not found." }
try { docker info 2>&1 | Out-Null; Write-Ok "Docker is running" }
catch { Write-Fail "Docker is not running. Start Docker Desktop." }
if (-not (Test-Path "$TdlibSrc\.git")) { Write-Fail "TDLib source not found at $TdlibSrc. Run build-tdlib.ps1 first." }
Write-Ok "TDLib source found"

Write-Step "Preparing output directory"
if (Test-Path $OutputDir) { Remove-Item $OutputDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutputDir | Out-Null
Write-Ok "Output dir: $OutputDir"

Write-Step "Patching Dockerfile for armeabi-v7a"
$AndroidExampleDir = "$TdlibSrc\example\android"
$DockerfilePath    = "$AndroidExampleDir\Dockerfile"
$original = Get-Content $DockerfilePath -Raw
$patched  = $original -replace '(ARG ABIS=)[^\r\n]+', '$1"armeabi-v7a"'
if ($patched -eq $original) {
    $patched = $original -replace '"arm64-v8a"', '"armeabi-v7a"'
}
Set-Content $DockerfilePath $patched -Encoding UTF8
Write-Ok "Dockerfile patched to armeabi-v7a"

Write-Step "Running Docker build for armeabi-v7a (30-45 min)"
try {
    docker build --output "type=local,dest=$OutputDir" $AndroidExampleDir
    if ($LASTEXITCODE -ne 0) { throw "Docker exited with code $LASTEXITCODE" }
    Write-Ok "Docker build completed"
} finally {
    Set-Content $DockerfilePath $original -Encoding UTF8
    Write-Ok "Dockerfile restored"
}

Write-Step "Locating libtdjni.so"
$zipFile = Get-ChildItem $OutputDir -Recurse -Filter "tdlib.zip" | Select-Object -First 1
if ($zipFile) {
    Write-Host "    Found ZIP, extracting..." -ForegroundColor Yellow
    $extractDir = "$OutputDir\extracted"
    Expand-Archive $zipFile.FullName -DestinationPath $extractDir -Force
    $so = Get-ChildItem $extractDir -Recurse -Filter "libtdjni.so" |
          Where-Object { $_.FullName -match "armeabi-v7a" } | Select-Object -First 1
} else {
    $so = Get-ChildItem $OutputDir -Recurse -Filter "libtdjni.so" |
          Where-Object { $_.FullName -match "armeabi-v7a" } | Select-Object -First 1
}
if (-not $so) { Write-Fail "libtdjni.so (armeabi-v7a) not found in $OutputDir" }
Write-Ok "Found: $($so.FullName)"

Write-Step "Copying to project"
$jniLibDir = "$ProjectDir\app\src\main\jniLibs\armeabi-v7a"
New-Item -ItemType Directory -Force -Path $jniLibDir | Out-Null
Copy-Item $so.FullName "$jniLibDir\libtdjni.so" -Force
Write-Ok "Copied libtdjni.so -> app/src/main/jniLibs/armeabi-v7a/"

Write-Host ""
Write-Host "Build complete! Now run:" -ForegroundColor Green
Write-Host "  .\gradlew assembleDebug --no-configuration-cache" -ForegroundColor Yellow
Write-Host "  adb install -r app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Yellow
