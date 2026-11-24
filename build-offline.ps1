# Offline build for Eat Speed Tuner (NeoForge 1.21.1).
#
# Compiles against the NeoForge / Minecraft jars that are already present in a
# launcher's .minecraft folder, so no network access to maven.neoforged.net is needed.
#
# Point it at your own installation either by setting MC_DIR or by passing -McDir:
#
#   pwsh -File build-offline.ps1 -McDir "C:\Users\you\.minecraft"
#   $env:MC_DIR = "D:\somewhere\.minecraft"; pwsh -File build-offline.ps1

param(
    [string]$McDir,
    [string]$VersionName = '1.21.1-NeoForge_21.1.250',
    [string]$JdkHome
)

$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$srcDir      = Join-Path $projectRoot 'src\main\java'
$resDir      = Join-Path $projectRoot 'src\main\resources'
$buildDir    = Join-Path $projectRoot 'build'
$classesDir  = Join-Path $buildDir 'classes'
$libsDir     = Join-Path $buildDir 'libs'
$modVersion  = '1.0.0'
$jarName     = "eatspeedtuner-$modVersion.jar"

# --- locate a JDK 21 -----------------------------------------------------------
if (-not $JdkHome) { $JdkHome = $env:JAVA_HOME }
if (-not $JdkHome -or -not (Test-Path (Join-Path $JdkHome 'bin\javac.exe'))) {
    $candidates = @(
        'C:\Program Files\Java\jdk-21',
        'C:\Program Files\Eclipse Adoptium\jdk-21*',
        'C:\Program Files\Microsoft\jdk-21*'
    )
    foreach ($c in $candidates) {
        $hit = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit -and (Test-Path (Join-Path $hit.FullName 'bin\javac.exe'))) {
            $JdkHome = $hit.FullName
            break
        }
    }
}
if (-not $JdkHome) { throw "No JDK 21 found. Set JAVA_HOME or pass -JdkHome." }

$javac   = Join-Path $JdkHome 'bin\javac.exe'
$jarTool = Join-Path $JdkHome 'bin\jar.exe'

# --- locate the Minecraft installation -----------------------------------------
if (-not $McDir) { $McDir = $env:MC_DIR }
if (-not $McDir) {
    $guesses = @(
        (Join-Path $env:APPDATA '.minecraft'),
        (Join-Path $env:USERPROFILE '.minecraft')
    )
    foreach ($g in $guesses) {
        if (Test-Path $g) { $McDir = $g; break }
    }
}
if (-not $McDir) {
    throw "Minecraft folder not found. Set MC_DIR or pass -McDir <path to .minecraft>."
}

$mcRoot     = $McDir
$versionDir = Join-Path $mcRoot "versions\$VersionName"

if (-not (Test-Path $javac)) { throw "javac not found at $javac" }
if (-not (Test-Path $versionDir)) {
    throw "Version folder not found: $versionDir`nPass -VersionName with the exact installed NeoForge version."
}

# ---------------------------------------------------------------------------
# 1. Build the classpath from the launcher's version JSON (client + libraries)
# ---------------------------------------------------------------------------
$jsonPath = Join-Path $versionDir '1.21.1-NeoForge_21.1.250.json'
$meta = Get-Content $jsonPath -Raw | ConvertFrom-Json

$classpath = New-Object System.Collections.Generic.List[string]
$seen = New-Object 'System.Collections.Generic.HashSet[string]'

function Add-Jar([string]$fullPath) {
    if (-not $fullPath) { return }
    if (-not (Test-Path $fullPath)) { return }
    if ($seen.Add($fullPath)) { $classpath.Add($fullPath) | Out-Null }
}

# IMPORTANT: the patched NeoForge jars must come FIRST. The version folder's jar contains
# obfuscated vanilla classes (e.g. an obfuscated MinecraftServer), and the classpath is
# order-sensitive: whichever jar is listed first wins for a duplicate class.
$clientJar = Join-Path $mcRoot 'libraries\net\neoforged\neoforge\21.1.250\neoforge-21.1.250-client.jar'
$universalJar = Join-Path $mcRoot 'libraries\net\neoforged\neoforge\21.1.250\neoforge-21.1.250-universal.jar'
Add-Jar $clientJar
Add-Jar $universalJar

# vanilla-ish jars shipped inside the version folder (assets / data / bootstrap only)
Get-ChildItem $versionDir -Filter '*.jar' -File | ForEach-Object { Add-Jar $_.FullName }

foreach ($lib in $meta.libraries) {
    $artifact = $lib.downloads.artifact
    if (-not $artifact) { continue }
    $rel = $artifact.path
    if (-not $rel) { continue }
    Add-Jar (Join-Path $mcRoot ("libraries\" + ($rel -replace '/', '\')))
}

# (patched jars already added above, before the version folder jar)

# Make sure every known NeoForge / Minecraft dependency jar in libraries is present
$mustHave = @(
    'net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar',
    'net\neoforged\bus\8.0.5\bus-8.0.5.jar',
    'net\neoforged\coremods\7.0.3\coremods-7.0.3.jar',
    'net\neoforged\mergetool\2.0.0\mergetool-2.0.0-api.jar',
    'net\neoforged\accesstransformers\10.0.1\accesstransformers-10.0.1.jar',
    'net\neoforged\fancymodloader\loader\4.0.44\loader-4.0.44.jar',
    'net\neoforged\fancymodloader\earlydisplay\4.0.44\earlydisplay-4.0.44.jar',
    'com\electronwill\night-config\toml\3.6.7\toml-3.6.7.jar',
    'com\electronwill\night-config\core\3.6.7\core-3.6.7.jar'
)
foreach ($rel in $mustHave) {
    Add-Jar (Join-Path $mcRoot ("libraries\" + $rel))
}

# Guarantee night-config + brigadier + commons-lang3 + slf4j + gson + authlib + netty are on the path
foreach ($pattern in @(
        'com\electronwill\night-config\**\*.jar',
        'com\mojang\brigadier\**\*.jar',
        'org\apache\commons\commons-lang3\**\*.jar',
        'org\slf4j\slf4j-api\**\*.jar',
        'com\google\code\gson\**\*.jar',
        'com\mojang\authlib\**\*.jar',
        'io\netty\netty-buffer\**\*.jar',
        'io\netty\netty-common\**\*.jar',
        'org\lwjgl\lwjgl\**\*.jar',
        'org\lwjgl\lwjgl-glfw\**\*.jar',
        'org\joml\joml\**\*.jar',
        'com\mojang\datafixerupper\**\*.jar',
        'it\unimi\dsi\fastutil\**\*.jar',
        'org\apache\logging\log4j\log4j-api\**\*.jar'
    )) {
    Get-ChildItem (Join-Path $mcRoot 'libraries') -Filter '*' -Directory -Recurse -ErrorAction SilentlyContinue | Out-Null
    Get-ChildItem (Join-Path $mcRoot ('libraries\' + $pattern)) -File -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 |
        ForEach-Object { Add-Jar $_.FullName }
}

$cp = ($classpath -join ';')
Write-Host "[build] classpath entries: $($classpath.Count)"

# ---------------------------------------------------------------------------
# 2. Compile
# ---------------------------------------------------------------------------
if (Test-Path $classesDir) { Remove-Item $classesDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $libsDir | Out-Null

$sources = Get-ChildItem $srcDir -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
Write-Host "[build] compiling $($sources.Count) source files"
$argFile = Join-Path $buildDir 'javac-args.txt'
$quoted = $sources | ForEach-Object { '"' + ($_ -replace '\\', '\\') + '"' }
Set-Content -Path $argFile -Value ($quoted -join "`n") -Encoding UTF8

& $javac -encoding UTF-8 -proc:none -nowarn -Xlint:none -source 21 -target 21 `
    -classpath $cp -d $classesDir "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

# copy resources
Copy-Item (Join-Path $resDir '*') -Destination $classesDir -Recurse -Force

# ---------------------------------------------------------------------------
# 3. Package
# ---------------------------------------------------------------------------
$jarPath = Join-Path $libsDir $jarName
if (Test-Path $jarPath) { Remove-Item $jarPath -Force }
& $jarTool --create --file $jarPath -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

Write-Host "[build] OK -> $jarPath"

