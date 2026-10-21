**JSON backup compatibility: This app reads `.json` backup files from SamMods InstaPro version 15.30 and earlier.**

# INSFAV Thumb Fetcher

Standalone native Android helper, version **0.5.0** (version code 8). Java, Android 8.0/API 26+, English UI, AMOLED black. No analytics or account registration.

## Preserved reference

The 0.4.0 release and the earlier `v0.2.2-working` reference remain unchanged. Version 0.5.0 carries forward the integrated Fetcher's durable queue, output index, clearer notifications, cooldown scheduling, and resolution audit while retaining the standalone JSON import and WebView retrieval engine.

## What the app does

INSFAV Thumb Fetcher is a small, standalone Android companion for preparing profile thumbnails from an INSFAV JSON backup. It reads account usernames from the selected backup, opens public Instagram profile pages with the user's own saved session when available, finds an account-matched profile image, and writes a centered 256 × 256 WebP thumbnail. It does not edit or upload the JSON backup.

It is limited to profile pictures. It does not download posts, stories, or videos, manage collections, search Instagram, or sync data to a cloud service.

## How to use

1. Install and open the app. Choose **Select JSON** and select the `.json` backup. The app reports the number of unique usernames it found. The selected file is read locally and is never rewritten.
2. Choose **Select Output Folder** and grant access to a writable parent folder. The app creates or reuses `.thumbnails` inside it and stores files as `.thumbnails/<normalized-username>.webp`.
3. Enter your Instagram session cookies in the Cookies section and choose **Save Cookies**. The app accepts Netscape `cookies.txt` text or a standard HTTP `Cookie` header pasted into the text area. Cookies are needed for pages requiring your session; saved cookie values are encrypted in app-private storage and are not shown again. There is no separate cookie test gate.
4. Set **Minimum Delay** and **Maximum Delay**, **Start Time** and **End Time**, and optionally **Concurrent WebViews** (1–30; default 1). Save Settings. A higher pool setting is only a ceiling; the app may lower it to fit device memory. Keep concurrency at 1 unless the device can handle more.
5. Choose **Start Missing**. The queue and output-file index persist privately, so re-importing the same backup does not discard completed work. Start Missing schedules only pending or rate-limit-deferred accounts; previously successful or existing thumbnails are not re-opened or re-validated on each run. The output folder is fully indexed on first use, after a detected external folder change, or with **More → Verify Thumbnail Index**. Use that explicit verification if files were changed by a storage provider that does not report folder changes. Valid existing WebP thumbnails are kept. Use **Pause / Resume** to hold and continue, or **Stop** to stop safely. Failed and skipped accounts can be retried later using **Retry Failed**.
6. Check live counts for completed, failed, skipped, remaining, active WebViews, indexed thumbnails and out-of-target image sizes. Notifications show queue progress and terminal status. Missing or unavailable pages are skipped with a reason so one bad account does not stop the batch. Login challenges, CAPTCHA, rate limits, or explicit access restrictions pause new work; Retry-After is honored and background continuation is scheduled within working hours when enabled. Android may defer scheduled work.
7. Existing valid 128 × 128 and 256 × 256 thumbnails are always preserved. Legacy refresh has been removed. Use **More → Export All Settings / Import All Settings** for a password-encrypted portable backup of preferences and cookies; account lists are excluded. To view a profile photo, tap a thumbnail or use **More → Open Profile Photo**. The local thumbnail appears first; if offered and available, a larger current image is fetched in memory. It is not saved permanently.

The backup format parser supports the three INSFAV JSON layouts documented below and has a 32 MiB input limit. Only usernames are normalized for matching; the source JSON and collection/account order are left untouched.

New files: `.thumbnails/<normalized-username>.webp`, 256 × 256, quality 78, center crop. Fixed-size output can scale small sources; this creates no new detail. Legacy 128 × 128 WebP remains valid. The JSON is read-only, with a 32 MiB limit and three supported INSFAV formats.

## Queue and access behavior

A bounded reusable WebView pool shares one cookie session. Each assignment has its own callbacks and result. All browser operations use the main looper. A single coordinator commits SQLite state and SAF output. Active results remain pending until committed, so interruption cannot lose their queue entries.

The requested pool size is a ceiling. Available RAM, low-RAM classification and memory pressure can reduce it. Profile launches share one random delay gate, independent of the pool count. Native image transfers have a separate maximum of two; each browser page has a 600-resource request budget. Chromium manages its internal subresource connections, so a WebView count is **not** an exact HTTP connection count.

404, routed unavailable/error pages, missing profile photos and page timeouts/network failures are **skipped for this run**, with a reason. They are never proof that an account was deleted. Account/image failures do not stop the batch after three failures. Login, CAPTCHA/challenge, HTTP 429 and explicit access restrictions stop new assignments. Storage permission loss stops safely. Retry-After is persisted; repeated random retries do not bypass it.

Foreground service and WorkManager are subject to Android restrictions. Scheduled work processes one bounded profile per job and may be deferred. Background Processing, a user-started armed queue and working hours are required for automatic continuation. Swiping away, process death and force-stop are not equivalent; Android force-stop prevents scheduled execution until the app is reopened.

## Privacy

The authoritative cookie jar is AES-GCM encrypted with Android Keystore in private storage. Session-only browser copies are shared during a run and purged when the pool closes/startup/Remove Cookies. Chromium has platform-managed private browser storage; abrupt death can leave data until the next startup purge. This is not a secure-erasure guarantee. Cookies are host-scoped to Instagram HTTPS; native CDN downloads carry no cookies. HD source bytes are not written to files or caches by the app. Logs contain fixed error codes and aggregate technical data, not cookie values, URLs or usernames.

## Build and verification

Android Studio / JDK 17 / Gradle Wrapper 8.13; compile SDK 35, target SDK 37. `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` and `node --test tools/profile-probe.test.cjs`.

CI builds debug and R8/resource-shrunk unsigned release APKs. It also launches the debug APK and tests real Chromium pool reuse on anonymous **offline fixtures**, for requested sizes 1, 2, 5, 10 and 30 under the emulator's effective RAM cap. These checks do not establish live Instagram or physical-device stability. Default concurrency stays 1. See [TESTING.md](TESTING.md).

Direct runtime dependency: AndroidX WorkManager 2.10.1 and its transitive AndroidX dependencies. Platform WebView/graphics/network APIs handle the rest. No additional runtime libraries were added for 0.5.0.

## Main INSFAV integration map

| Component | Responsibility |
| --- | --- |
| `CookieJar`, `CookieVault`, `SessionState` | Parse, encrypt, replace and invalidate session state |
| `WebViewProfileProvider`, `WebProfilePolicy` | Shared session lease, reusable browser workers, URL and identity checks |
| `assets/profile-probe.js` | Account-bound avatar discovery; optional explicitly offered HD candidate |
| `PublicProfileProvider` | Bounded HTTPS image download without CDN cookies |
| `ThumbnailCodec` | Validate source and encode 256 × 256 WebP; accept legacy 128 × 128 |
| `QueueEngine`, `ConcurrencyPolicy`, `FailurePolicy` | Bounded dispatch, pacing, memory reduction and skip/pause policy |
| `QueueStore`, `QueueControl`, `FetchService`, `WorkScheduler` | Durable queue and user/background lifecycle |
| `OutputFolder`, `Identity` | SAF staging/readback/backup recovery and exact username filenames |
| `PhotoViewer` | In-memory uncropped source, actual resolution, pinch/double-tap/pan |

Main INSFAV code is not modified by this helper branch. See [COMPATIBILITY.md](COMPATIBILITY.md) for the inspected source revision and limitations.
