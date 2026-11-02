# Alpha Movies Android WebView App

This is the Android WebView project for Alpha Movies.

## App details

- App name: Alpha Movies
- Package/Application ID: `com.alphamovies.app`
- Version: `1.0.6` / `versionCode 7`
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
- Pull-to-refresh gesture with a visible spinner only
- Safe header handling so the website does not sit under the camera cutout/notch
- Dark Alpha Movies splash screen
- Splash screen uses the same Alpha Movies app icon
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

## Replace app icon later

The splash screen uses the same launcher icon as the app.

To replace it with your final production icon, update the launcher icon files:

```text
app/src/main/res/drawable/ic_launcher_foreground.png
app/src/main/res/mipmap-mdpi/ic_launcher.png
app/src/main/res/mipmap-hdpi/ic_launcher.png
app/src/main/res/mipmap-xhdpi/ic_launcher.png
app/src/main/res/mipmap-xxhdpi/ic_launcher.png
app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

Then rebuild the APK.
# Alpha Movies Android v1.0.7

## GitHub Actions Android SDK fix

- Updated `android-actions/setup-android` from v3 to v4.
- Explicitly installs `platform-tools` instead of the removed legacy `tools` package.
- Installs `platforms;android-36` and `build-tools;36.0.0` with `sdkmanager --install`.
- Applies the same Android 36 setup to debug APK and signed Play Store builds.
- Pins Gradle 8.10.2 and disables the Gradle daemon in both workflows for consistent CI builds.

## Proper Android movie downloads

- Uses a foreground Android download service instead of a silent browser-style transfer.
- Shows the movie title, progress and downloaded size in the notification.
- Includes Pause/Resume and Cancel actions in the notification bar.
- Uses HTTP Range requests to continue a paused transfer without starting again when the server supports resume.
- Saves completed movies under Downloads/Alpha Movies.
- Uses an audible, vibrating Android notification channel for download start and completion.
- Passes the movie page title into Android so downloaded files keep a readable movie name.

