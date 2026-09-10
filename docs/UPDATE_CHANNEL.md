# Selyro TV QA update channel

## Installed updater base

Starting with versionCode 6 (`0.2.5-updater`), Selyro TV checks this public manifest on startup:

`https://raw.githubusercontent.com/aseel90/FeatherFury-LaB/main/selyro-updates/latest.json`

The public manifest points to:

`https://raw.githubusercontent.com/aseel90/FeatherFury-LaB/main/selyro-updates/Selyro-TV-latest.apk`

## Update behavior

1. App compares `latest.json.versionCode` with `BuildConfig.VERSION_CODE`.
2. If newer, an **Update available** card appears.
3. User presses **UPDATE NOW** with the TV remote.
4. Selyro downloads the APK using Android DownloadManager.
5. SHA-256 is verified before install.
6. On Android 8+, the first update may require enabling **Install unknown apps** for Selyro TV.
7. Selyro launches the Android package installer. The user confirms the OS install prompt.

## QA signing

QA updater builds are re-signed in CI with the fixed public AOSP test key. This is deliberately QA-only and guarantees that successive internal APKs can update each other. Before production distribution, move to a private production signing key and reinstall the production base build.

## Publishing a future update

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Build/export through `.github/workflows/qa-apk-export.yml`.
3. CI writes the signed APK to `ci-artifacts/` and creates transport chunks under `relay/selyro/` plus `manifest.env`.
4. Copy the relay files to the public repository folder `selyro-updates/incoming/`.
5. `FeatherFury-LaB/.github/workflows/selyro-publish-update.yml` reconstructs and verifies the APK and updates `latest.json`.
6. Installed Selyro clients detect the new `versionCode` automatically.

Never publish Xtream/M3U credentials or private source files into the public update folder.
