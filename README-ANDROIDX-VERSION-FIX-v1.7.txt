MK Movies TWA v1.7 - AndroidX SDK 36 mismatch fix

This version downgrades Android Browser Helper from 2.7.1 to 2.5.0.
Reason: androidbrowserhelper 2.7.x can pull androidx.browser 1.10.0 and androidx.core 1.17.0, which require compileSdk 36 and Android Gradle Plugin 8.9.1+.
This project intentionally stays on compileSdk 35 and Android Gradle Plugin 8.7.3 for safer GitHub builds.

Use this pack by uploading all files to the repository root, keeping the two GitHub secrets:
- MK_KEYSTORE_PASSWORD
- MK_KEY_PASSWORD

Then run the workflow again.
