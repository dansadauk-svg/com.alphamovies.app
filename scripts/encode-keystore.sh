#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
key_path="$project_root/mk-movies-release-key.jks"

if [ ! -f "$key_path" ]; then
  echo "Place mk-movies-release-key.jks in the project root before running this script." >&2
  exit 1
fi

base64 < "$key_path" | tr -d '\n'
echo
