$ErrorActionPreference = "Stop"
$keyPath = Join-Path $PSScriptRoot "..\mk-movies-release-key.jks"

if (-not (Test-Path $keyPath)) {
    throw "Place mk-movies-release-key.jks in the project root before running this script."
}

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath))
Set-Clipboard -Value $base64
Write-Host "MK_KEYSTORE_BASE64 copied to the clipboard. Paste it into the GitHub Actions secret."
