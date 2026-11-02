MK Movies Android v2.7.0
========================

App version
- versionName: 2.7.0
- versionCode: 17
- minSdk: Android 7.0, API 24 (required by current Firebase Messaging)
- compileSdk / targetSdk: Android 16, API 36

Movie downloads
- A foreground Android notification now appears as soon as a download starts.
- The notification displays progress, downloaded size and total size.
- Android DownloadManager keeps the transfer resumable and provides the final
  clickable completion notification.
- The WebView passes the download button's filename, movie card title or page
  title to Android, so saved movies no longer use admin-post.php or a generic name.
- Login cookies, user agent and referrer are preserved for protected links.

Firebase notifications
- Every installation subscribes to topic: mk_movies_all
- Alert channel: mk_movies_alerts_v2 (high importance)
- Firebase, download-start and download-complete channels use the normal Android
  notification sound and vibration instead of being classified as Silent.
- The WordPress/Firebase sender must send one high-priority topic message to
  mk_movies_all for all updated app users to receive the broadcast together.
- Keep existing token batches temporarily for old app versions that have not yet
  installed v2.7.0 and therefore are not subscribed to the topic.

Build versions
- Android Gradle Plugin 9.4.1
- Gradle 9.6.0
- Google Services plugin 4.5.0
- Firebase Android BoM 35.1.0

Important
- Android 13 and newer users must allow notification permission.
- Network/server speed still controls the maximum movie download speed. The app
  now uses the native resumable DownloadManager path on both Wi-Fi and mobile data.
