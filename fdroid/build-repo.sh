#!/usr/bin/env bash
# Builds the website with the F-Droid repository in fdroid/site from a signed APK.
# Usage: build-repo.sh APK KEYSTORE
# Requires FDROID_KEYSTORE_PASS, FDROID_KEY_PASS and ANDROID_HOME, and fdroidserver on PATH.
set -euo pipefail

apk=$(realpath "$1")
keystore=$(realpath "$2")
root=$(cd "$(dirname "$0")/.." && pwd)
app_id=me.d3sox.pdfwallet
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cp "$root/fdroid/config.yml" "$work/"
chmod 600 "$work/config.yml"
cp "$keystore" "$work/keystore.jks"
mkdir -p "$work/metadata/$app_id" "$work/repo"
cp "$root/fdroid/metadata/$app_id.yml" "$work/metadata/"
# store texts and screenshots, in the layout fdroidserver expects
cp -r "$root/fastlane/metadata/android/." "$work/metadata/$app_id/"
cp "$root/fastlane/metadata/android/en-US/images/icon.png" "$work/icon.png"
aapt2=$(find "$ANDROID_HOME/build-tools" -name aapt2 | sort -V | tail -1)
version_code=$("$aapt2" dump badging "$apk" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
cp "$apk" "$work/repo/${app_id}_${version_code}.apk"

(cd "$work" && fdroid update --pretty --use-date-from-apk)

rm -rf "$root/fdroid/site"
mkdir -p "$root/fdroid/site"
cp -r "$work/repo" "$root/fdroid/site/repo"
cp -r "$root/website/." "$root/fdroid/site/"
cp -r "$root/fastlane/metadata/android/en-US/images/phoneScreenshots" "$root/fdroid/site/screenshots"
