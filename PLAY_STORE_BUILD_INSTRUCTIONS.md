# Alpha Movies Play Store Release Build

App name: Alpha Movies  
Package name: com.alphamovies.app  
Website URL: https://alphamovies.com.ng/

## GitHub Secrets to Add

Go to your GitHub repository:

Settings → Secrets and variables → Actions → New repository secret

Add these 4 secrets exactly:

1. ALPHA_MOVIES_KEYSTORE_BASE64
2. ALPHA_MOVIES_KEYSTORE_PASSWORD
3. ALPHA_MOVIES_KEY_ALIAS
4. ALPHA_MOVIES_KEY_PASSWORD

The values are inside the private file named:

PRIVATE_SIGNING_KEYS_DO_NOT_SHARE.txt

## How to Build

After adding the secrets:

1. Push the project to GitHub.
2. Open the repository on GitHub.
3. Go to Actions.
4. Open Build Play Store Release.
5. Click Run workflow.
6. Download the artifact named alpha-movies-playstore-aab.

Upload the .aab file to Google Play Console.

## Important

Do not lose the signing key. Google Play updates must be signed with the same key unless you use Play App Signing key reset.
Do not share PRIVATE_SIGNING_KEYS_DO_NOT_SHARE.txt publicly.
Do not commit the JKS keystore file to a public GitHub repository.
