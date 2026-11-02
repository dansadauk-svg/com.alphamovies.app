MK Movies Android v2.6.2 API 36 Force Download Fix

This version fixes movie download links opening the video player instead of starting Android downloads.

Changes:
- Keeps targetSdk 36 and compileSdk 36.
- Updates versionName to 2.6.2 and versionCode to 16.
- Adds a JavaScript bridge that intercepts movie download button clicks before WebView navigates.
- Intercepts theme button classes: mk-watch-white__download and mkmc-theme-download-button.
- Intercepts Core plugin button classes: mkmc-watch-download and mkmc-download-button.
- Intercepts admin-post mkmc_download_movie URLs.
- Intercepts direct R2/CDN video file URLs.
- Sends the file to Android DownloadManager instead of opening the video player.
- Uses public Downloads folder when Android allows it, with fallback to app Downloads.
- Passes website cookies to DownloadManager for protected download links.

Use this source for GitHub/Android Studio. Do not upload your signing key to public GitHub. Keep your existing Play signing key/secrets.
