MK Movies Android Build Pack v2.5 - Native WebView Fullscreen Fix

This version adds proper Android WebView fullscreen support using WebChromeClient onShowCustomView/onHideCustomView.

Why: The video player fullscreen works in browser but not inside the Android app view because the native WebView needs to receive and display the HTML5 video fullscreen custom view.

Fixes:
- Fullscreen video inside Android WebView
- Landscape fullscreen orientation
- Immersive mode hides status/navigation bars during fullscreen
- Back button exits fullscreen first
- Keeps previous notification click fix and native FCM setup

Build in GitHub Actions the same way as v2.4.
