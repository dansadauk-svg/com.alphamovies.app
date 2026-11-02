MK Movies TWA GitHub Build Pack v1.4

This version is made to avoid GitHub Actions startup failure as much as possible.

What changed from v1.3:
- Removed the external Gradle setup action from the workflow.
- Installs Gradle 8.10.2 directly in a shell step.
- Installs Android SDK platform 35 and build tools 35.0.0 before building.
- Adds clearer error messages if files or GitHub secrets are missing.
- Adds push + manual workflow triggers.
- Builds :app:assembleRelease and :app:bundleRelease with --stacktrace.
- Bumps Android version to 1.3.0, versionCode 4.

Important GitHub secrets required:
- MK_KEYSTORE_PASSWORD
- MK_KEY_PASSWORD

Use the values inside keystore-info.txt.

Recommended update method:
1. Delete the old files in your GitHub repository.
2. Upload all files from this v1.4 pack to the repository root.
3. Keep the repository private if you upload mk-movies-release-key.jks.
4. Add MK_KEYSTORE_PASSWORD and MK_KEY_PASSWORD in Settings > Secrets and variables > Actions.
5. Go to Actions > Build MK Movies TWA APK/AAB > Run workflow.

If your repository is public:
- Do not upload mk-movies-release-key.jks publicly.
- Instead, encode the keystore as base64 and save it as MK_KEYSTORE_BASE64 in GitHub secrets.
