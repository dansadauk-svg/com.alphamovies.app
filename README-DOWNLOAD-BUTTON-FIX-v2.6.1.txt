MK Movies Android v2.6.1

Fixes app download button behavior.

Changes:
- Keeps targetSdk 36 for Play Store.
- Detects MK Movies secure download links inside WebView.
- Starts Android DownloadManager instead of opening the secure download URL as a page.
- Sends WebView cookies to DownloadManager so logged-in/subscribed users keep access.
