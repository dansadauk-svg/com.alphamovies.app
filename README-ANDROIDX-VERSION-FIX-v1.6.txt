MK Movies TWA Build Pack v1.6 AndroidX Version Fix

This version fixes the AAR metadata error caused by newer AndroidX dependencies requiring compileSdk 36 and Android Gradle Plugin 8.9.1+.

Fix applied:
- Keeps compileSdk 35 and Android Gradle Plugin 8.7.3.
- Forces AndroidX Browser/Core versions compatible with this build setup:
  androidx.browser:browser:1.8.0
  androidx.core:core:1.13.1
  androidx.core:core-ktx:1.13.1
- Version bumped to 1.4.0 / versionCode 5.

Upload all files to the repository root and run the GitHub Actions workflow again.
