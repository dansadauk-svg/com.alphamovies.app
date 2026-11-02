MK Movies Android v2.9.1 Firebase Fix
=====================================

Version
- versionName: 2.9.1
- versionCode: 20
- compileSdk / targetSdk: Android API 36 (Android 16)

Firebase fixes
- Uses a fresh high-importance, audible channel: mk_movies_alerts_v3.
- Displays the text notification immediately before downloading an optional poster.
- Keeps notifications visible even if the poster server is slow or unavailable.
- Explicitly enables Firebase Messaging auto-initialization.
- Refreshes the mk_movies_all topic and saved WordPress token on app resume.
- Saves and refreshes the real Android notification-permission state.
- Supports notification taps sent by older WordPress sender versions.

Required WordPress sender
- Install ksd-movie-push-notifications-v3.9.2-firebase-android-fix.zip.
- Confirm its Firebase Service Account JSON belongs to project mk-movies-44ea0.
- Send one test after opening the updated app and allowing notifications.
