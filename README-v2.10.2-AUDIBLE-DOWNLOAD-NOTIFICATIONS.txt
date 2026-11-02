MK Movies Android v2.10.2 - Audible Download Notifications

Release:
- versionName: 2.10.2
- versionCode: 23
- compileSdk / targetSdk: 36 (Android 16)

Download notification changes:
- A movie download now begins with a normal alerting Android notification.
- The first download notification uses the device's default notification sound and vibration.
- Progress-bar refreshes do not repeatedly make sound.
- Completion and failure use a separate high-importance channel, with sound and vibration.
- Pause, Resume and Cancel controls remain available.
- Movie-title filename handling remains enabled.

New Android channels:
- mk_movie_downloads_progress_v5
- mk_movie_downloads_complete_v5

The new channel IDs are intentional. Android does not let an app reliably turn an
existing silent channel into an audible channel after that channel has already been
created on a user's device.

GitHub Actions outputs:
- MK-Movies-v2.10.2-release.apk
- MK-Movies-v2.10.2-play-console.aab
