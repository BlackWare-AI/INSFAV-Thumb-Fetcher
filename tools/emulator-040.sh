#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-040
apk=$(find builds -name app-debug.apk -print -quit)
test_apk=$(find builds -name '*debug-androidTest.apk' -print -quit)
test -n "$apk" && test -n "$test_apk"
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n com.insfav.thumbfetcher/.MainActivity
sleep 5
adb shell pidof com.insfav.thumbfetcher
adb shell dumpsys activity activities | grep -E 'mResumedActivity.*com.insfav.thumbfetcher/.MainActivity'
adb shell am force-stop com.insfav.thumbfetcher
adb install -r "$test_apk"
adb shell am instrument -w com.insfav.thumbfetcher.test/com.insfav.thumbfetcher.PoolInstrumentation | tee emulator-040/pool.txt
grep -q POOL_MATRIX_OK emulator-040/pool.txt
adb shell am start -W -n com.insfav.thumbfetcher/.MainActivity
adb shell screencap -p /sdcard/fetcher040.png
adb pull /sdcard/fetcher040.png emulator-040/screen.png
adb logcat -b crash -d > emulator-040/crash.txt
