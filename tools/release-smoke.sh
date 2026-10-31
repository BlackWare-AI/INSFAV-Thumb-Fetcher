#!/usr/bin/env bash
set -euo pipefail
package=com.insfav.thumbfetcher
adb install -r INSFAV-Thumb-Fetcher-6.0.0.apk
adb shell am start -W -n "$package/.MainActivity" > release-launch.txt
grep -q 'Status: ok' release-launch.txt
sleep 3
adb shell pidof "$package"
adb shell dumpsys activity activities > release-activities.txt
grep -q 'com.insfav.thumbfetcher.MainActivity' release-activities.txt
