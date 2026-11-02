MK Movies TWA GitHub Build Pack v1.5

This version fixes the AndroidX build error:
"android.useAndroidX property is not enabled"

Changes:
1. Added root gradle.properties with:
   android.useAndroidX=true
   android.enableJetifier=true
   org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

2. Updated GitHub Actions workflow to recreate gradle.properties before building, so GitHub cannot miss the AndroidX setting.

Upload all files to the root of your GitHub repository and run the workflow again.
