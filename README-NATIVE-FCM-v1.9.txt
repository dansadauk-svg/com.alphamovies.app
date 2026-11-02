MK Movies TWA Build Pack v1.9 Native FCM BuildConfig Fix

Fixes GitHub build failure in MkTokenRegistrar.java where BuildConfig.APPLICATION_ID and BuildConfig.VERSION_NAME could not be found.

Change made:
- Enabled Android BuildConfig generation in app/build.gradle using buildFeatures { buildConfig true }.
- Bumped APK versionCode to 8 and versionName to 1.7.0.

Use this ZIP in GitHub the same way as v1.8. Keep the same secrets: MK_KEYSTORE_PASSWORD and MK_KEY_PASSWORD.
