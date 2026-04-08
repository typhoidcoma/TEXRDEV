Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Check {
  param(
    [string]$Name,
    [bool]$Ok,
    [string]$Details
  )
  if ($Ok) {
    Write-Host "[PASS] $Name - $Details" -ForegroundColor Green
  } else {
    Write-Host "[FAIL] $Name - $Details" -ForegroundColor Red
  }
}

function Get-JavaMajorVersion {
  $sources = @()
  $sources += @{ Name = "PATH"; Command = { & java -version 2>&1 } }
  if ($env:JAVA_HOME) {
    $javaHomeExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $javaHomeExe) {
      $sources += @{ Name = "JAVA_HOME"; Command = { & $javaHomeExe -version 2>&1 } }
    }
  }

  foreach ($src in $sources) {
    try {
      $output = & $src.Command
      if (-not $output) { continue }
      $first = $output | Select-Object -First 1
      if ($first -match '"(\d+)(\.\d+)?') {
        return [int]$Matches[1]
      }
    } catch {
      continue
    }
  }

  return $null
}

function Resolve-JavaSource {
  $javaCmd = Get-Command java -ErrorAction SilentlyContinue
  if ($javaCmd) { return "PATH ($($javaCmd.Source))" }
  if ($env:JAVA_HOME) {
    $javaHomeExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $javaHomeExe) {
      return "JAVA_HOME ($javaHomeExe)"
    }
  }
  return "Not found"
}

Write-Host "Validating Windows + Android setup for Meta Wearables DAT..." -ForegroundColor Cyan
Write-Host ""

$failures = 0

# 1) Java
$javaMajor = Get-JavaMajorVersion
if ($null -eq $javaMajor) {
  Write-Check "JDK" $false "Java not found in PATH. Install JDK 17 and set PATH/JAVA_HOME."
  $failures++
} else {
  $ok = $javaMajor -ge 17
  $javaSource = Resolve-JavaSource
  $detail = "Detected Java major version $javaMajor from $javaSource (17+ recommended)."
  Write-Check "JDK" $ok $detail
  if (-not $ok) { $failures++ }
}

# 2) Android Studio
$studioPaths = @(
  "C:\Program Files\Android\Android Studio\bin\studio64.exe",
  "C:\Program Files\Android\Android Studio\bin\studio.exe",
  "$env:LOCALAPPDATA\Programs\Android Studio\bin\studio64.exe",
  "$env:LOCALAPPDATA\Programs\Android Studio\bin\studio.exe"
)
$studioFound = $false
foreach ($p in $studioPaths) {
  if (Test-Path $p) {
    $studioFound = $true
    Write-Check "Android Studio" $true "Found at $p"
    break
  }
}
if (-not $studioFound) {
  Write-Check "Android Studio" $false "Not found in common install paths."
  $failures++
}

# 3) adb
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCmd) {
  Write-Check "adb" $true "Found at $($adbCmd.Source)"
} else {
  $androidSdkRoot = $env:ANDROID_SDK_ROOT
  $androidHome = $env:ANDROID_HOME
  $candidate = $null
  if ($androidSdkRoot) {
    $candidate = Join-Path $androidSdkRoot "platform-tools\adb.exe"
  } elseif ($androidHome) {
    $candidate = Join-Path $androidHome "platform-tools\adb.exe"
  }
  if ($candidate -and (Test-Path $candidate)) {
    Write-Check "adb" $true "Found at $candidate"
  } else {
    Write-Check "adb" $false "adb not found. Install Android SDK Platform-Tools and ensure adb is in PATH."
    $failures++
  }
}

# 4) sdkmanager and required packages
$sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { $_ -and (Test-Path $_) }
$sdkManagerPath = $null
foreach ($root in $sdkRoots) {
  $candidates = @(
    (Join-Path $root "cmdline-tools\latest\bin\sdkmanager.bat"),
    (Join-Path $root "cmdline-tools\bin\sdkmanager.bat"),
    (Join-Path $root "tools\bin\sdkmanager.bat")
  )
  foreach ($c in $candidates) {
    if (Test-Path $c) {
      $sdkManagerPath = $c
      break
    }
  }
  if ($sdkManagerPath) { break }
}

if ($sdkManagerPath) {
  Write-Check "sdkmanager" $true "Found at $sdkManagerPath"
  try {
    $installed = & $sdkManagerPath --list_installed 2>&1 | Out-String
    $hasPlatform = $installed -match "platforms;android-35"
    $hasBuildTools = $installed -match "build-tools;35\."
    Write-Check "Android SDK Platform 35" $hasPlatform ($(if ($hasPlatform) { "Installed" } else { "Missing: platforms;android-35" }))
    if (-not $hasPlatform) { $failures++ }
    Write-Check "Android Build-Tools 35.x" $hasBuildTools ($(if ($hasBuildTools) { "Installed" } else { "Missing: build-tools;35.x" }))
    if (-not $hasBuildTools) { $failures++ }
  } catch {
    Write-Check "Android SDK packages" $false "Could not query sdkmanager. Verify command-line tools are configured."
    $failures++
  }
} else {
  Write-Check "sdkmanager" $false "Not found. Install Android command-line tools."
  $failures++
}

# 5) Gradle wrapper
$gradlewBat = Join-Path (Get-Location) "gradlew.bat"
$gradleWrapperJar = Join-Path (Get-Location) "gradle\wrapper\gradle-wrapper.jar"
$hasGradlew = (Test-Path $gradlewBat) -and (Test-Path $gradleWrapperJar)
Write-Check "Gradle wrapper" $hasGradlew ($(if ($hasGradlew) { "gradlew.bat and wrapper jar found." } else { "Missing gradlew.bat and/or gradle-wrapper.jar." }))
if (-not $hasGradlew) { $failures++ }

# 6) DAT token
$tokenFromEnv = $env:GITHUB_TOKEN
$tokenFromLocalProps = $null
$localPropsPath = Join-Path (Get-Location) "local.properties"
if (Test-Path $localPropsPath) {
  $line = Get-Content $localPropsPath | Where-Object { $_ -match "^\s*github_token\s*=" } | Select-Object -First 1
  if ($line) {
    $tokenFromLocalProps = ($line -split "=", 2)[1].Trim()
  }
}

$hasToken = -not [string]::IsNullOrWhiteSpace($tokenFromEnv) -or -not [string]::IsNullOrWhiteSpace($tokenFromLocalProps)
if ($hasToken) {
  Write-Check "GITHUB_TOKEN" $true "Found in environment or local.properties."
} else {
  Write-Check "GITHUB_TOKEN" $false "Missing token. Set env GITHUB_TOKEN or local.properties: github_token=..."
  $failures++
}

Write-Host ""
if ($failures -eq 0) {
  Write-Host "Environment check passed." -ForegroundColor Green
  exit 0
} else {
  Write-Host "Environment check failed with $failures issue(s)." -ForegroundColor Red
  exit 1
}
