# Versioned APK Releases Archive

This directory stores versioned APK archives organized by `versionName` and `versionCode` (for example: `apk-releases/v1.0-c1/`, `apk-releases/v1.1-c2/`).

## How to Archive a New Version APK
Standard Android build outputs remain untouched at `app/build/outputs/apk/` for full AI Studio and Android Gradle Plugin compatibility.

Whenever you bump `versionName` or `versionCode` in `app/build.gradle.kts`, run:
```bash
gradle :app:assembleDebug :app:archiveVersionedApk
```
or for release builds:
```bash
gradle :app:assembleRelease :app:archiveVersionedApk
```
Each built APK is copied into `apk-releases/v<versionName>-c<versionCode>/Atelier-v<versionName>-c<versionCode>-<buildType>.apk` so every historical version stays neatly organized.
