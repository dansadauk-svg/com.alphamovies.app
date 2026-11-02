MK Movies TWA Build Pack v1.8 - Native Firebase Notifications

What changed:
- Added google-services.json for Firebase Android app package ng.com.mkmovies.app.
- Added native Firebase Cloud Messaging SDK.
- Added MainActivity that requests Android notification permission and registers the native app FCM token with WordPress.
- Added MkFirebaseMessagingService to receive Firebase notifications under the MK Movies app, not Chrome.
- Added Android notification channel: mk_movies_channel.
- Kept the TWA website wrapper and signing setup from v1.7.

Important:
- Upload all files to the GitHub repository root.
- Keep the repo private because mk-movies-release-key.jks is included.
- Keep the same GitHub secrets: MK_KEYSTORE_PASSWORD and MK_KEY_PASSWORD.
- Install/update the WordPress push plugin v3.0 before testing native app notifications.
- In the plugin, click Clear Chrome/Web Tokens so old Chrome tokens stop receiving messages.
- Build the APK from GitHub Actions, uninstall the old app, then install the new APK.
- Open the new app and allow notifications once. It will register the native Android token automatically.
