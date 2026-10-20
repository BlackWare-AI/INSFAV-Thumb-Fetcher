# INSFAV Thumb Fetcher 0.2

An independent native Java Android helper. It reads a selected local INSFAV backup and creates `.thumbnails/username.webp` profile thumbnails. **Live authenticated Instagram retrieval has not been verified by the developer in this environment.** The app requires a real-device preflight before it unlocks batch or automatic downloading. No change to the main INSFAV app is required.

## First use and live preflight

1. Select JSON and a writable parent folder (or an existing `.thumbnails` folder).
2. Select Cookies expands an inline multiline text field. Use Paste Cookies or paste manually. Supported text formats are Netscape cookies.txt *contents* and a standard HTTP Cookie header. There is no cookie file chooser/upload.
3. Save Cookies encrypts the jar with AES-256-GCM and an Android Keystore key in private storage. The editor is cleared/hidden; saved cookie values are never shown again. Remove Cookies deletes the encrypted jar and revokes verification/scheduling.
4. Set delays and working hours; Save Settings. Defaults: 15–30 seconds, 02:00–07:00 local time, Background Processing off. Delays support 1–86400 seconds, maximum ≥ minimum; start/end must differ. Overnight windows are supported.
5. Test Cookies first checks server-returned viewer identity on the Instagram homepage. Then, with at least 3 imported accounts and an output folder, it attempts up to 5 distinct accounts, sequentially, respecting the configured delays and Retry-After. Three actual successful profile image downloads, decoding and verified output writes/skips are required. The test does not run the entire backup. Existing valid files are not overwritten; preflight still retrieves their image for live verification.
6. Only after that preflight succeeds does Start Missing unlock. Test Cookies is an explicit foreground diagnostic and may run outside the scheduled hours. Normal batch/automatic profile processing is confined to the chosen window.

A 200 response, pasted sessionid, og:image or synthetic fixture does not mean a valid session or a working live download. COOKIE_AUTH_UNVERIFIED and PROFILE_IMAGE_URL_MISSING are explicit outcomes. A login/challenge is not bypassed. Resolve it through Instagram normally and paste a fresh session if necessary. Never send cookies in chat, issues or diagnostics.

## Queue and background behavior

One profile is processed at a time. New outputs are normalized `username.webp`, 128 × 128, center-cropped, WebP quality 78. JSON is never modified or uploaded. Existing valid outputs are skipped. Old `.thumnails/<hash>.webp` files are left untouched; choose their parent for the new output folder.

User-started work uses a dataSync foreground service and a notification with downloaded/existing/missing/failed counts and Pause/Stop. Background Processing must be enabled for continuing after the Activity leaves the foreground and for automatic scheduled work. It does not start a queue by itself: the user must first verify cookies and explicitly Start Missing/Resume.

A window ending finishes the current request/write and pauses. WorkManager schedules bounded one-network-profile jobs and a 15-minute recovery guard. Each job rechecks hours, current cookie verification, user pause/stop, server cooldown and persisted queue state. Android can defer/quota/stop this work; this is not an exact alarm or a guarantee of continuous execution. WorkManager restores eligible work after reboot/unlock; force-stop requires reopening the app. Work outside permitted hours makes no Instagram request.

Pause blocks automatic continuation until manual Resume. Stop disarms it. Cookie/session errors, challenges, 429, storage failures and repeated download errors block automatic retries until the user resolves the cause and explicitly resumes/retests. Retry-After is retained across restarts and never replaced with random retry loops. Failed rows remain available through More → Retry Failed.

## Security and troubleshooting

The cookie editor is excluded from saved view state/autofill/IME learning; screenshots are blocked while it is visible. Clipboard access occurs only on the user's Paste Cookies action. The app does not clear or manage the system clipboard. Android backup/device transfer of private data is excluded. Cookies are sent only on appropriate Instagram HTTPS requests, never to image CDNs or redirects to other hosts. No global CookieHandler, browser scraping, WebView, private Instagram API, CAPTCHA solver, ads or telemetry is included.

UI error details distinguish cookie invalid/expired/unverified, login, unavailable profile, private/restricted, rate limit, missing URL, download, decode and storage failures. More → Export Diagnostics writes bounded, redacted technical event/error codes and aggregate counts. It never exports cookie/header/URL/body/credential values or raw exception messages. See TESTING.md and STATUS.md for the checks actually performed.

## Build

JDK 17, Gradle 8.13, compile SDK 35, minimum 26, target 37:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The only direct production dependency is `androidx.work:work-runtime:2.10.1` and its transitive AndroidX/Kotlin runtime dependencies. This increases APK size relative to the 0.1 Java-only prototype. JUnit and Robolectric are test-only. Release uses R8/resource shrinking and remains unsigned. No signing key or real cookies/JSON are committed. Debug builds use a development certificate; updating an older test APK requires the same certificate. If unavailable, reinstalling loses private queue/settings but leaves external JSON and thumbnail files unchanged.
