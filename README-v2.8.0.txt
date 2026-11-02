MK Movies Android v2.8.0 GitHub Build
=====================================

Version
- versionName: 2.8.0
- versionCode: 18
- compileSdk / targetSdk: Android API 36
- minSdk: Android API 24

GitHub build
- Builds both a release APK and Play Console AAB.
- Names the artifacts MK-Movies-v2.8.0-release.apk and MK-Movies-v2.8.0-play-console.aab.
- Reads the private upload key only from GitHub Actions secrets.
- Stops before compiling when the upload key does not match Google Play's expected SHA-1.
- Verifies the completed AAB certificate after compilation.

Preserved app improvements
- Native Android movie download progress and completion notifications.
- Movie-title filenames for saved downloads.
- Login cookies and WebView headers passed to DownloadManager.
- High-priority, audible Firebase alerts through the mk_movies_all topic.
- Android API 36 Play Console target.
