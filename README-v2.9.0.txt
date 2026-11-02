MK Movies Android v2.9.0 API 36 GitHub Build
=============================================

Version
- versionName: 2.9.0
- versionCode: 19
- compileSdk: Android API 36
- targetSdk: Android API 36
- minSdk: Android API 24

GitHub build
- Installs Android platform 36 and build-tools 36.0.0.
- Validates compileSdk 36, targetSdk 36, versionCode 19 and versionName 2.9.0 before compiling.
- Builds both a signed release APK and Play Console AAB.
- Names the files MK-Movies-v2.9.0-release.apk and MK-Movies-v2.9.0-play-console.aab.
- Uses the existing GitHub Actions signing secrets and verifies the Play upload certificate.

Preserved app improvements
- Audible native Android download progress and completion notifications.
- Movie-title filenames for saved downloads.
- Login cookies and WebView headers passed to DownloadManager.
- High-priority Firebase alerts through the mk_movies_all topic.
