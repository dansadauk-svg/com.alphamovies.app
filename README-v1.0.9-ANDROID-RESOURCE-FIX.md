# Alpha Movies Android v1.0.9

## Android resource linker fix

- Adds the missing `@color/backgroundColor` resource used by Android 12+ merged splash resources.
- Defines it in the default and night resource configurations.
- Fixes the `:app:processDebugResources` failure.
- Retains the v1.0.8 clean movie-title download notifications.
- Retains audible download notifications, progress, Pause, Resume and Cancel.
- Retains the Android SDK setup correction from v1.0.7.
