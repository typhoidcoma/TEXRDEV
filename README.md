# TEXRDEV: Meta Wearables DAT Android Starter

Android starter repository for building apps that connect to Meta AI glasses with the Meta Wearables Device Access Toolkit (DAT).

This repo is sample-first and based on the official `meta-wearables-dat-android` CameraAccess sample so you can get to first device connection quickly.

## 15-Minute Quickstart (Windows + Android)

1. Install prerequisites:
   - Android Studio (latest stable)
   - JDK 17
   - Android SDK Platform 35 + Build-Tools 35.x
   - Android device with Android 12+ and USB debugging enabled
1. Create a GitHub personal access token with at least `read:packages` scope.
1. Configure token using either:
   - Environment variable: `GITHUB_TOKEN`
   - `local.properties` entry: `github_token=YOUR_TOKEN`
1. Run bootstrap checks:
   - `powershell -ExecutionPolicy Bypass -File scripts/bootstrap-windows.ps1`
1. Build:
   - `.\gradlew.bat assembleDebug`
1. Install and run from Android Studio.
1. Enable Developer Mode in the Meta AI app.
1. Launch app and connect to glasses.

## Project Details

- Kotlin + Jetpack Compose app baseline
- AGP `8.6.0`
- Kotlin `2.1.20`
- `compileSdk=35`, `minSdk=31`, `targetSdk=34`
- DAT dependencies:
  - `com.meta.wearable:mwdat-core`
  - `com.meta.wearable:mwdat-camera`
  - `com.meta.wearable:mwdat-mockdevice`

## Registration Mode

This starter uses Developer Mode bring-up by default:

- `com.meta.wearable.mwdat.APPLICATION_ID` is set to `0`
- This is intended for early development and smoke testing
- Replace with your real app ID from Wearables Developer Center for production flows

## CI

GitHub Actions workflow is included at `.github/workflows/android-ci.yml`.

- Runs `assembleDebug` and `test` on push/PR
- Requires a package token exposed as `GITHUB_TOKEN` in workflow env
- Recommended: add repository secret `MWDAT_GITHUB_TOKEN` (`read:packages`)

## Additional Setup Docs

- Full setup guide: `docs/setup/windows-android.md`
- Environment validator: `scripts/bootstrap-windows.ps1`

## Canonical References

- Toolkit docs: https://wearables.developer.meta.com/docs/getting-started-toolkit/
- Android DAT repo: https://github.com/facebook/meta-wearables-dat-android
