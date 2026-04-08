# Windows + Android Setup Guide (Meta Wearables DAT)

This guide sets up a new Windows machine to build and run this repository.

## 1. Install Required Tools

1. Install Android Studio (latest stable).
1. Install JDK 17.
1. In Android Studio SDK Manager, install:
   - Android SDK Platform 35
   - Android SDK Build-Tools 35.x
   - Android SDK Platform-Tools
1. Confirm `adb` works from terminal:
   - `adb version`

## 2. Configure GitHub Packages Access

The DAT artifacts are hosted on GitHub Packages:
`https://maven.pkg.github.com/facebook/meta-wearables-dat-android`

Create a GitHub Personal Access Token (classic) with:
- `read:packages`

Then choose one of:

1. Environment variable (recommended):
   - PowerShell: `$env:GITHUB_TOKEN="YOUR_TOKEN"`
2. `local.properties` fallback:
   - Add `github_token=YOUR_TOKEN`

Do not commit tokens.

## 3. Validate Local Environment

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/bootstrap-windows.ps1
```

The script checks:
- JDK presence and major version (17 recommended)
- Android Studio install presence
- `adb` availability
- SDK Platform 35 + Build-Tools availability (when `sdkmanager` is available)
- Gradle wrapper presence
- `GITHUB_TOKEN` (env or `local.properties`)

## 4. Build and Run

1. Build from terminal:
   - `.\gradlew.bat assembleDebug`
1. Open project in Android Studio.
1. Run `app` on Android 12+ device.

## 5. Device Bring-up for Meta AI Glasses

1. Enable Developer Mode in Meta AI app.
1. Launch app and connect.
1. Confirm session startup and camera streaming.

Default manifest behavior in this repo:
- `com.meta.wearable.mwdat.APPLICATION_ID=0` (Developer Mode bring-up)
- `com.meta.wearable.mwdat.ANALYTICS_OPT_OUT=true`

For production registration:
- Create org/project/channel in Wearables Developer Center
- Replace `APPLICATION_ID` with your registered app ID

## 6. CI Setup

Workflow file:
- `.github/workflows/android-ci.yml`

Set repository secret:
- `MWDAT_GITHUB_TOKEN` with `read:packages`

The workflow maps `MWDAT_GITHUB_TOKEN` to `GITHUB_TOKEN` for Gradle dependency resolution.
