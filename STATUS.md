# 0.2.2 — distinguish profile access from session identity

A successful HTTP response without supported homepage viewer metadata previously stopped the entire preflight as COOKIE_AUTH_UNVERIFIED before any profile could be tested. 0.2.2 records UNVERIFIED session evidence for that specific outcome and tests real target-bound profile images instead. It never reports unknown cookie acceptance as valid: public photo access does not prove authentication.

At least three actual distinct profile image downloads, native image decoding and verified output handling remain required before batch/automatic work can unlock. HTTP/login/challenge/cookie/network failures still stop. Missing or ambiguous profile metadata cannot unlock anything. Original JSON, username output naming, existing media and main INSFAV remain unchanged. Test prerequisites (at least three accounts and an output folder) are now checked before network requests.

Session evidence (VERIFIED, UNVERIFIED, INVALID, UNKNOWN) is shown separately in UI/diagnostics and reset on cookie changes. No raw server response or session values are logged or committed. No exposed cookie is reused for development.

Five additional engine regression tests cover unsupported homepage + real image access, unsupported homepage + missing photos, explicit homepage restrictions, absent output folder, and a login wall on a profile after an unknown homepage. Verification: [CI](https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39372695896) passed 58 tests, lint (0 errors, 16 warnings), debug and R8/resource-shrunk release builds for application commit 6e82d6e5fda2eaa32774b33a999b231252a62c42. [Artifact](https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39372695896/artifacts/12241284424): debug 2,479,775 bytes; unsigned release 258,349 bytes. Debug SHA-256: 61e534a46ce7990894ddc240d04370305c0432f9541b1db37a7210f5c6b5ae39. CI certificates are ephemeral; updating an earlier APK may require helper reinstall, clearing its private queue/cookies/settings but preserving external files; real-device profile retrieval is not yet proven. Previous measurements below are historical.

---

# 0.2.1 — access-wall classification fix

The 0.2.0 classifier could mark an HTTP 200 page as CHALLENGE_REQUIRED solely because its script bundles contained the word captcha, a /challenge/ string, or login UI text. Those strings do not establish an active access wall.

0.2.1 checks explicit structured server error flags and challenge/login forms. Actual challenge redirects and HTTP 401/403/429 still stop or pause work. Removing a false wall does not attest a session: unsupported viewer metadata remains COOKIE_AUTH_UNVERIFIED (HTTP 200), and missing profile metadata remains PROFILE_IMAGE_URL_MISSING. The three-profile download gate remains enforced.

Regression tests cover bundled module names, navigation links, authenticated viewer metadata, discovered-image transport, explicit challenge/login flags and forms, and fail-closed behavior when authentication/photo evidence is absent. No user cookies, response bodies, personal diagnostics or account identifiers are included in source or fixtures. The shared session is not reused for this change.

Verification passed: 53 tests, zero failures/errors; lint has 0 errors and 15 warnings. Debug and R8/resource-shrunk release builds succeeded. CI: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39371722092 . Tested application commit: 5b5c9a821b7edb22af6b5bdc48ac6e787cd94e8f . Artifact: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39371722092/artifacts/12241986259 . Debug APK: 2,479,191 bytes, SHA-256 22241013a5b01499e99a911bd8b3303a6c20052c7fb50d66dd40deb301e8079c . Unsigned shrunk release: 258,161 bytes. CI uses an ephemeral debug signing key; an update over an earlier APK may require reinstalling only the helper, which clears its private queue/cookies/settings but preserves external JSON/photos. Live profile downloads and physical-device scheduling remain unverified. The 0.2.0 results below are historical baseline measurements; they do not describe the new APK's size or signing certificate.

---

# INSFAV Thumb Fetcher — 0.2.0 technical status

Date: 2026-10-20

Implemented and automatically verified; live Instagram and physical-device acceptance remain outstanding. This is a development build, not a claim that authenticated Instagram downloads have passed live validation.

- Repository: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher
- Draft PR: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/pull/1
- Tested application commit: `004e40802c5e499cc786d66cb1525a5ece8ad074`
- Successful CI: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39357326357
- Build reports and APKs: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39357326357/artifacts/12235613808

## Implemented behavior

The native Java Activity retains the black AMOLED interface, original JSON parser, SQLite queue, bounded image conversion and SAF write verification. Main INSFAV was not modified. Source JSON is read only.

Cookies expand inline through Select Cookies. Paste Cookies accepts Netscape/HttpOnly text or an HTTP Cookie header, including quoted Instagram cookie escape sequences. There is no cookie file picker. Save, Test and Remove are available. Saved values are never displayed again. The editor is cleared on save or leaving the screen, excluded from state restoration/autofill/IME learning and protected from screenshots while visible.

The cookie vault uses AES-256-GCM, random nonces, authenticated associated data, Android Keystore and private AtomicFile storage. Backups/device transfer are disabled. Cookies, raw HTTP bodies/headers, account identifiers and exception text are excluded from diagnostics. Only scoped Instagram HTTPS requests receive cookies; image CDNs never receive them. Cookie rotation is not persisted automatically; changed/expired sessions require paste and retest.

The provider requests the Instagram homepage for server viewer attestation, then the exact profile page. It accepts structured image metadata bound to the requested username and numeric account ID, rejects explicit/known default avatars, and does not use og:image alone, guessed CDN URLs, private APIs or challenge bypasses. Missing or changed server metadata fails explicitly.

Test Cookies attempts up to five distinct imported profiles. At least three actual downloads, successful native image decoding and valid output checks are required to unlock batch/automatic work. Existing files alone cannot unlock it: preflight still fetches the image. At least three imported accounts and a writable output folder are therefore needed for this initial verification. Saving/removing cookies invalidates proof. Testing never arms automatic processing.

Output is `.thumbnails/username.webp`, using the normalized pageId, exactly 128 × 128 pixels, center crop, WebP quality 78. Select the parent folder or .thumbnails itself. The old misspelled .thumnails folder, historical SHA files and unrelated media are preserved. Valid existing outputs are skipped during batch work. Partial files and invalid outputs can be recovered; successful counters require verified completed writes.

Delay defaults to 15–30 seconds, with validated editable bounds of 1–86400 seconds. Hours use local HH:mm, support midnight crossings, include the start and exclude the end. A current profile/write can finish at the boundary; no subsequent profile starts outside the window. Explicit Test Cookies may run outside the schedule. HTTP 429 retains Retry-After and pauses instead of random retries; manual retest cannot bypass that deadline.

Foreground service execution and persisted WorkManager scheduling share one serial engine and owner lock. Each scheduled execution checks a bounded group of cached files and retrieves at most one network profile. Background Processing defaults off. Automatic continuation requires explicit user arming, current cookie proof, unfinished accounts, working hours and no pause/error block. Pause and Stop also work while a scheduled queue is waiting. Stop disarms it. No boot receiver directly starts a foreground service, and no exact alarm is used.

Counts distinguish existing, successful, failed and pending accounts. Failure groups distinguish invalid/expired or unverified cookies, login, unavailable profiles, restricted/challenged profiles, rate limiting, missing image URL, download, decode, storage and Android background restrictions. TXT export contains bounded fixed error codes, HTTP integers and aggregate counts, with secrets and account identifiers redacted.

## Automated verification

GitHub Actions ran `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` successfully with JDK 17, Gradle 8.13 and AGP 8.9.1.

| Suite | Tests | Failures/errors |
| --- | ---: | ---: |
| Import | 6 | 0 |
| Provider transport/metadata | 9 | 0 |
| Cookie parsing/encryption | 7 | 0 |
| Native image codec | 2 | 0 |
| Serial queue engine | 8 | 0 |
| Queue persistence | 4 | 0 |
| Schedule policy | 4 | 0 |
| SAF storage | 4 | 0 |
| UI cookie/idle queue controls | 2 | 0 |
| Diagnostics | 1 | 0 |
| **Total** | **47** | **0** |

Tests run in Robolectric with Android API 28 fixtures; they do not contact Instagram. AES tests inject a JVM test key, not a physical Android Keystore. Network tests use synthetic cookies and controlled HTTP/image fixtures. This proves the implementation's fixture behavior, not live availability.

Coverage includes all three JSON formats and size cap, duplicate/invalid normalization, invalid-import preservation, header injection/domain/path/expiry checks, encryption/tamper/remove, viewer identity and CDN cookie isolation, 404/403/login/429/network loss, preflight gating, Retry-After persistence, existing file skips, one-profile scheduled execution, pause during a write, overnight boundaries, SQLite reopen/retry, partial/corrupt outputs, storage permission loss and unsupported rename.

The actual current INSFAV ThumbnailPolicy source was compiled read only against this helper: **10,000 username filename mappings passed**. Physical rendering in main INSFAV remains unverified.

| Synthetic import size | JSON bytes | CI elapsed |
| --- | ---: | ---: |
| 1,000 accounts | 24,903 | 30 ms |
| 5,000 accounts | 128,903 | 70 ms |
| 10,000 accounts | 258,903 | 103 ms |

These are single CI measurements, not device performance or memory guarantees. Lint reported **0 errors and 15 warnings**: 14 ApplySharedPref suggestions where immediate durable checkpoints are used, and one newer WorkManager-version notice.

## Build size and dependencies

| Output | Bytes | Purpose |
| --- | ---: | --- |
| Debug APK | 2,477,963 | Installable test build, approximately 2.36 MiB |
| R8/resource-shrunk release APK | 257,485 | Approximately 251 KiB; unsigned, not directly installable |

Package `com.insfav.thumbfetcher`, versionCode 2, versionName 0.2.0, minimum API 26, compile SDK 35, target SDK 37. Targeting an API does not mean it was device-tested.

Direct production dependency: AndroidX WorkManager `work-runtime:2.10.1`. Its transitive AndroidX/Kotlin components include Core, Room/SQLite, Lifecycle, Startup, ProfileInstaller, tracing, coroutines and futures. Observed APK version metadata includes Core 1.12.0, Room 2.6.1, SQLite 2.4.0, Startup 1.1.1, ProfileInstaller 1.3.0 and coroutines 1.7.3. WorkManager is deliberately pinned; lint reports a newer 2.12.0 release. Test-only dependencies are JUnit 4.13.2 and Robolectric 4.14.1. No Compose, WebView, ads, analytics or telemetry SDK is included.

Merged permissions are Internet, foreground service/dataSync, notifications, network state, wake lock and boot completed, plus AndroidX's internal nonexported-receiver permission. No broad storage or exact-alarm permission is requested.

Debug APK SHA-256: `6ffd06f4da5362ae2cf9a8ba8650bc89f778cd1eb30cdf16ddec54c3bcccbb7a`

APK signature v2 verified. CI debug certificate SHA-256: `3dc89ce46dc5cea833740190a38ddcca06c457ac482edac7d145c421df6284b9`.

The former helper signing key is unavailable. This CI build uses a new debug certificate, and later ephemeral CI certificates may differ too. An update over an older installation may fail. Reinstalling only the helper clears its private queue/settings/cookies and requires reimport/reconfiguration; external JSON and thumbnails remain. Stable distribution requires an owner-controlled signing key outside GitHub source. No signing key is included in the repository.

## Outstanding acceptance gates

1. On a real device, use accessible profiles and a user session pasted only into the app. Run Test Cookies and verify at least three of up to five real account photos. No real session was available in this development environment, so no such success is claimed.
2. Confirm each generated file visually maps to the correct account inside unchanged INSFAV, with real SAF providers and existing media intact.
3. Validate real Android Keystore storage across app restart and key loss, and verify no sensitive data appears in exported diagnostics.
4. Validate foreground notification controls, process death, reboot/unlock, Doze, clock/timezone changes, Android 15+ dataSync limits and Android 16+ job quotas on devices. WorkManager dispatch may be delayed or stopped; continuous execution is not guaranteed.
5. Validate actual expired/revoked cookies, challenges, rate limits, storage grant loss and provider markup changes. Cookies do not guarantee access to personal, private, restricted, deleted or unavailable profiles. A 404 is not proof of account deletion.
6. Measure import/SAF performance and memory on Android 8 and a current device. Optional official Meta API support is not implemented.

Device test order: Select JSON → Select Output Folder → Select Cookies → Paste Cookies → Save Cookies → Test Cookies → adjust/save hours and delays → optionally enable Background Processing → Start Missing. On failure, share only Export Diagnostics output, never cookies or session credentials.
