MK Movies Android v2.6.0 API 36 Play Store Fix

Purpose
-------
This update fixes the Google Play warning:
App must target Android 16 (API level 36) or higher.

Changed files
-------------
- build.gradle
  - Android Gradle Plugin updated from 8.7.3 to 8.12.2.
- app/build.gradle
  - compileSdk changed from 35 to 36.
  - targetSdk changed from 35 to 36.
  - versionCode changed from 13 to 14.
  - versionName changed from 2.5.0 to 2.6.0.
- .github/workflows/build-twa.yml
  - Gradle changed from 8.10.2 to 8.13.
  - Android SDK install changed from android-35/build-tools 35 to android-36/build-tools 36.
- twa-manifest.json
  - appVersionName changed to 2.6.0.
  - appVersionCode changed to 14.

Build notes
-----------
Use Android Studio with Android SDK Platform 36 installed, or run the included GitHub Actions workflow.
Upload the generated AAB from app/build/outputs/bundle/release/ to Google Play.

Important
---------
This package is source code. It is not a signed APK/AAB.
Keep using your existing Play Store signing setup/signing key.
