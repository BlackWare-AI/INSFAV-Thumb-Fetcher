# INSFAV Thumb Fetcher

Standalone native Android helper, version **0.4.0** (version code 7). Java, Android 8.0/API 26+, English UI, AMOLED black. No analytics or account registration.

## Preserved reference

The original 0.3.0 WebView build remains unchanged at [`v0.2.2-working`](https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/releases/tag/v0.2.2-working), commit `a153102dd95243465144efc3ec74b5cb11186e09`. The tag name is an archival label. Development of 0.4.0 is isolated on `development/0.4.0`; it does not replace that tag or the original APK.

## Usage

1. Select an INSFAV JSON backup and a writable output parent through Android's document picker.
2. Paste Netscape cookie text or an HTTP Cookie header, then Save Cookies. There is no cookie file picker or test gate.
3. Set delay, working hours and **Concurrent WebViews (1–30, default 1)**. Save Settings.
4. Start Missing. Pause/Resume and Stop preserve unfinished work. Retry Failed also includes previously skipped accounts.
5. More → Refresh 128 × 128 Thumbnails explicitly redownloads legacy thumbnails. Existing valid files otherwise remain unchanged.
6. Tap the latest thumbnail, or More → Open Profile Photo for an imported username. The local image appears first; an explicitly offered larger profile image is fetched into memory when the queue session is free. Pause the queue before opening HD during a download run.

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

Direct runtime dependency: AndroidX WorkManager 2.10.1 and its transitive AndroidX dependencies. Platform WebView/graphics/network APIs handle the rest. No new runtime libraries were added for 0.4.0.

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
