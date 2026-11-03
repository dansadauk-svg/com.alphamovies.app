# Alpha Movies Android v1.0.12

## Clean saved movie filenames

- The downloaded file itself now uses the movie title, not only the notification.
- R2 numeric prefixes, timestamps, random keys and trailing object IDs are removed.
- Hyphens and underscores are converted to normal spaces.
- The original video extension is preserved.
- Example: `1049-1793557352-jnaibpjs-KUNNA-WUTA_6925.mp4` becomes `KUNNA WUTA.mp4`.
- Keeps download sound, progress, Pause, Resume and Cancel.
- Keeps `minSdk 24` for Google Play automatic protection.
