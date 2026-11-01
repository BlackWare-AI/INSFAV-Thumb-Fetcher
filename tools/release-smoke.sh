#!/usr/bin/env bash
set -Eeuo pipefail
# Preserve strict checks; reveal which launch assertion failed, without signing material.
diagnose_failure() {
  code=$?
  trap - ERR
  echo "::error::Signed release smoke check failed (exit $code)"
  for report in previous-launch.txt release-launch.txt release-activities.txt; do
    if [[ -f "$report" ]]; then
      echo "::group::$report"
      cat "$report"
      echo "::endgroup::"
    fi
  done
  echo "::group::Current foreground activity"
  adb shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|mFocusedApp|com[.]insfav[.]thumbfetcher' | tail -n 35 || true
  echo "::endgroup::"
  echo "::group::Recent Android process crashes"
  adb logcat -b crash -d 2>/dev/null | tail -n 180 || true
  echo "::endgroup::"
  exit "$code"
}
trap diagnose_failure ERR
package=com.insfav.thumbfetcher
prior=$(find previous -name '*.apk' -print -quit)
test -n "$prior"
adb install "$prior"
adb shell am start -W -n "$package/.MainActivity" > previous-launch.txt
cat previous-launch.txt
grep -q 'Status: ok' previous-launch.txt
adb shell am force-stop "$package"
adb install -r INSFAV-Thumb-Fetcher-6.0.0.apk
adb shell am start -W -n "$package/.MainActivity" > release-launch.txt
cat release-launch.txt
grep -q 'Status: ok' release-launch.txt
sleep 3
adb shell pidof "$package"
adb shell dumpsys activity activities > release-activities.txt
grep -q 'com.insfav.thumbfetcher.MainActivity' release-activities.txt
