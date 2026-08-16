# Alpha Movies Android WebView App

This is the Android WebView project for Alpha Movies.

## App details

- App name: Alpha Movies
- Package/Application ID: `com.alphamovies.app`
- Main activity: `com.alphamovies.app.MainActivity`
- Website URL location: `app/src/main/java/com/alphamovies/app/AppConfig.java`

## Important before building

Open this file:

```text
app/src/main/java/com/alphamovies/app/AppConfig.java
```

Confirm the live website URL:

```java
public static final String WEBSITE_URL = "https://alphamovies.com.ng/";
```

If your final domain is different, change only that value and keep the trailing slash.

## What is included

- WebView that keeps the website looking the same as Chrome mobile
- JavaScript, cookies, DOM storage, and login session persistence
- File upload support from the WebView
- Native movie downloads through Android DownloadManager
- Download links keep the app session cookies and start immediately from the in-app Download Movie button
- Fullscreen video support
- Firebase Cloud Messaging setup with `app/google-services.json`
- Pull-to-refresh gesture with a visible refresh spinner
- Safe header handling so the website does not sit under the camera cutout/notch
- Dark Alpha Movies splash screen
- Transparent PNG splash logo
- Splash loading percentage progress
- Offline screen with retry button
- Android launcher icons
- GitHub Actions build workflow

## Build from GitHub

Push this project to GitHub. Then go to:

```text
Actions > Build Alpha Movies APK > Run workflow
```

After the build finishes, download the APK from the workflow artifact named:

```text
alpha-movies-debug-apk
```

## Replace logo later

The temporary included logo is already transparent PNG.

To replace it with your final production logo, replace:

```text
app/src/main/res/drawable/splash_logo.png
app/src/main/res/drawable/ic_launcher_foreground.png
```

Then rebuild the APK.
