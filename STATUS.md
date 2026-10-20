# 0.3.0 — hidden WebView development build

Default production photo discovery now uses a JavaScript-enabled, unattached Android WebView with main-thread lifecycle management, scoped cookie installation, bounded DOM/image readiness polling, exact-account photo selection and per-operation teardown. The existing native HTTPS downloader, 128 × 128 WebP converter, SAF output, JSON parser and scheduling queue remain in use. The main INSFAV app is unchanged.

Added More → Test One Profile, followed by Test Cookies for up to five profiles. Previous provider proof is invalidated on upgrade. The single-profile test never unlocks the batch queue. Five-profile testing no longer ends automatically at three successes.

No new runtime dependency. System WebView is supplied by Android; its renderer memory is outside APK size and remains to be measured. Chromium's internal private storage is not the encrypted vault: session/storage purges occur around each page and on startup, but abrupt termination may leave private browser data until next startup. No browser-store encryption or secure-erasure guarantee is claimed.

Validation completed for application commit 3fc7a40a6d7aa7f30e264b776a3a87df568f1285:
- CI succeeded: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39463080746
- Build artifact: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39463080746/artifacts/12282914963
- 72 JVM/Robolectric tests: zero failures/errors. 12 Node DOM-probe fixture tests passed locally and in CI. These do not execute a real Android WebView/Instagram session.
- Debug and R8/resource-shrunk release builds succeeded. Lint: zero errors, 19 warnings (17 ApplySharedPref durable-checkpoint suggestions, one pinned WorkManager update and one TargetApi/RequiresApi annotation suggestion).
- Debug APK: 2,493,548 bytes (about 2.38 MiB); SHA-256 1f03bfb584ce951bc0c7da67f3df81f5a130071302ba29e2c8106f1241cb24fa.
- Unsigned shrunk release: 292,514 bytes; SHA-256 19227450b20aaa0ee6bf41f395b914aaa73f049d62a194ec27cbf94f10453369.
- Binary manifest verified: package com.insfav.thumbfetcher, versionCode 6, versionName 0.3.0, min 26, compile 35, target 37. The DOM asset and WebView provider are present in the APK.
- APK signature v2 verified. Debug certificate SHA-256 df133086e89b09e3b264927235dada19bfd69fe4c1671e2aeab0a4425ef396f7 differs from 0.2.3. Android cannot update an old installation with a different certificate. Reinstalling clears the helper's private queue/settings/cookies; external original JSON and thumbnail files remain. No signing key is committed.
- The local Gradle fallback could not resolve AGP dependencies. The delivered APK is the successful CI output; local SDK tools independently verified its manifest and signature. No local full-build or physical-device result is claimed.

**Live acceptance remains outstanding. No connected Android device/emulator or fresh private session is available here. No authenticated one-account/five-account Instagram download or hidden-renderer background success is claimed.** The two in-app diagnostic steps are provided for those device checks. No real account identifiers, credentials or personal backup content are included in GitHub.
