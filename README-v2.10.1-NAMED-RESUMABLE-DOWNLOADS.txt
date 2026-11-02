MK Movies Android v2.10.1 - Named Resumable Downloads

PLAY STORE RELEASE
- versionName: 2.10.1
- versionCode: 22
- compileSdk: 36
- targetSdk: 36 (Android 16)
- package: ng.com.mkmovies.app

MOVIE FILENAMES
- Gets the title from the current movie page, movie heading, movie card, download metadata and Open Graph title.
- Rejects generated timestamp names and random storage object names.
- Uses a meaningful server filename only when one is supplied.
- Saves the final movie using its real movie title and video extension.

DOWNLOAD CONTROLS
- Running notification: Pause and Cancel.
- Paused notification: Resume and Cancel.
- Resume continues from the saved byte position when R2/server range requests are available.
- Cancel deletes the incomplete file.
- Completed movies are stored in Downloads/MK Movies.
- Uses the new mk_movie_downloads_v4 Android notification channel.

GITHUB ACTIONS
- Builds MK-Movies-v2.10.1-release.apk.
- Builds MK-Movies-v2.10.1-play-console.aab.
- Keeps the existing Play upload-key SHA1 verification.
- Keep the current GitHub repository secrets; do not create a new signing key.
