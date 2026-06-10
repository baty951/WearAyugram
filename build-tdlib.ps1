# TDLib Android Build Script for WearAyugram
# Requires: Docker Desktop (running), Git
# Target ABI: arm64-v8a (all modern WearOS devices)
# Estimated build time: 30-60 minutes

param(
    [string]$TdlibSrc  = "$env:TEMP\tdlib-src",
    [string]$OutputDir = "$env:TEMP\tdlib-out",
    [string]$ProjectDir = $PSScriptRoot
)

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    OK: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "    FAIL: $msg" -ForegroundColor Red; exit 1 }

# ── 1. Prerequisites ────────────────────────────────────────────────────────
Write-Step "Checking prerequisites"

if (-not (Get-Command git -ErrorAction SilentlyContinue))  { Write-Fail "Git not found. Install from https://git-scm.com" }
Write-Ok "Git found"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Write-Fail "Docker not found. Install Docker Desktop." }
try {
    docker info 2>&1 | Out-Null
    Write-Ok "Docker is running"
} catch {
    Write-Fail "Docker is not running. Start Docker Desktop and try again."
}

# ── 2. Clone TDLib ──────────────────────────────────────────────────────────
Write-Step "Cloning TDLib source"

if (Test-Path "$TdlibSrc\.git") {
    Write-Host "    Source already exists, pulling latest..." -ForegroundColor Yellow
    git -C $TdlibSrc pull --ff-only
} else {
    git clone --depth 1 https://github.com/tdlib/td.git $TdlibSrc
}
Write-Ok "TDLib source ready at $TdlibSrc"

# ── 3. Prepare output directory ─────────────────────────────────────────────
Write-Step "Preparing output directory"

if (Test-Path $OutputDir) { Remove-Item $OutputDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutputDir | Out-Null
Write-Ok "Output dir: $OutputDir"

# ── 4. Docker build ──────────────────────────────────────────────────────────
Write-Step "Running Docker build (this will take 30-60 minutes on first run)"
Write-Host "    Docker will download the build image and compile TDLib for arm64-v8a." -ForegroundColor Gray
Write-Host "    Subsequent runs reuse the Docker cache and finish in ~5 minutes." -ForegroundColor Gray

$AndroidExampleDir = "$TdlibSrc\example\android"

# Patch Dockerfile to build only arm64-v8a to save time
$DockerfilePath = "$AndroidExampleDir\Dockerfile"
$dockerfile = Get-Content $DockerfilePath -Raw

# The official Dockerfile builds all 4 ABIs by default.
# We patch it to build only arm64-v8a for WearOS.
if ($dockerfile -notmatch "ABIS=arm64-v8a") {
    Write-Host "    Patching Dockerfile to build arm64-v8a only..." -ForegroundColor Yellow
    $patched = $dockerfile -replace `
        '(ARG ABIS=)"arm64-v8a armeabi-v7a x86 x86_64"', `
        '$1"arm64-v8a"'
    # If the above pattern doesn't match, try alternate forms
    if ($patched -eq $dockerfile) {
        $patched = $dockerfile -replace `
            '(ARG ABIS=)[^\r\n]+', `
            '$1"arm64-v8a"'
    }
    Set-Content $DockerfilePath $patched -Encoding UTF8
    Write-Ok "Dockerfile patched"
} else {
    Write-Ok "Dockerfile already targets arm64-v8a"
}

# Run the Docker build
docker build `
    --output "type=local,dest=$OutputDir" `
    $AndroidExampleDir

if ($LASTEXITCODE -ne 0) { Write-Fail "Docker build failed. Check the output above." }
Write-Ok "Docker build completed"

# ── 5. Locate output files ───────────────────────────────────────────────────
Write-Step "Locating build artifacts"

$jar = Get-ChildItem $OutputDir -Recurse -Filter "td-android.jar" | Select-Object -First 1
$so  = Get-ChildItem $OutputDir -Recurse -Filter "libtdjni.so"    |
       Where-Object { $_.FullName -match "arm64-v8a" }            | Select-Object -First 1

if (-not $jar) { Write-Fail "td-android.jar not found in $OutputDir" }
if (-not $so)  { Write-Fail "libtdjni.so (arm64-v8a) not found in $OutputDir" }

Write-Ok "td-android.jar : $($jar.FullName)"
Write-Ok "libtdjni.so    : $($so.FullName)"

# ── 6. Copy to project ───────────────────────────────────────────────────────
Write-Step "Copying files to project"

$libsDir   = "$ProjectDir\app\libs"
$jniLibDir = "$ProjectDir\app\src\main\jniLibs\arm64-v8a"

New-Item -ItemType Directory -Force -Path $libsDir   | Out-Null
New-Item -ItemType Directory -Force -Path $jniLibDir | Out-Null

Copy-Item $jar.FullName "$libsDir\td-android.jar"          -Force
Copy-Item $so.FullName  "$jniLibDir\libtdjni.so"           -Force

Write-Ok "Copied td-android.jar → app/libs/"
Write-Ok "Copied libtdjni.so    → app/src/main/jniLibs/arm64-v8a/"

# ── 7. Done ──────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  TDLib build complete! Now open the project in Android     " -ForegroundColor Green
Write-Host "  Studio and run a Gradle sync (File -> Sync Project).      " -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
