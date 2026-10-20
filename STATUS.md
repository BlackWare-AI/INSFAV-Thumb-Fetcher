# 0.3.0 — hidden WebView development build

Default production photo discovery now uses a JavaScript-enabled, unattached Android WebView with main-thread lifecycle management, scoped cookie installation, bounded DOM/image readiness polling, exact-account photo selection and per-operation teardown. The existing native HTTPS downloader, 128 × 128 WebP converter, SAF output, JSON parser and scheduling queue remain in use. The main INSFAV app is unchanged.

Added More → Test One Profile, followed by Test Cookies for up to five profiles. Previous provider proof is invalidated on upgrade. The single-profile test never unlocks the batch queue. Five-profile testing no longer ends automatically at three successes.

No new runtime dependency. System WebView is supplied by Android; its renderer memory is outside APK size and remains to be measured. Chromium's internal private storage is not the encrypted vault: session/storage purges occur around each page and on startup, but abrupt termination may leave private browser data until next startup. No browser-store encryption or secure-erasure guarantee is claimed.

Validation: 12 Node DOM-probe fixture tests passed locally. Android build, lint, regression tests and APK measurements are pending CI for this commit.

**Live acceptance remains outstanding. No connected Android device/emulator or fresh private session is available here. No authenticated one-account/five-account Instagram download or hidden-renderer background success is claimed.** The two in-app diagnostic steps are provided for those device checks. No real account identifiers, credentials or personal backup content are included in GitHub.
