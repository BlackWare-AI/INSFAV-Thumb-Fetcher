#!/usr/bin/env bash
set -euo pipefail
package=com.insfav.thumbfetcher
for apk in app/build/outputs/apk/debug/app-debug.apk original-working/INSFAV-Thumb-Fetcher-0.3.0.apk; do
  echo "Checking APK: $apk"
  adb uninstall "$package" >/dev/null 2>&1 || true
  adb install "$apk"
  adb logcat -c
  adb shell am start -W -n "$package/.MainActivity"
  sleep 5
  adb shell pidof "$package"
  adb shell dumpsys activity activities | grep -E 'mResumedActivity.*com.insfav.thumbfetcher/.MainActivity'
  if adb logcat -d -b crash | grep 'com.insfav.thumbfetcher'; then
    echo "Application crash detected"
    exit 1
  fi
  adb shell am force-stop "$package"
done
