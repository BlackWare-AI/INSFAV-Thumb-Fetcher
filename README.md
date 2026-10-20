# INSFAV Thumb Fetcher 0.3.0

## Preserved working build

The owner reported successful real photo downloads on 2026-10-20. Application source reference: `3fc7a40a6d7aa7f30e264b776a3a87df568f1285`, versionCode 6 / versionName **0.3.0**. The requested tag `v0.2.2-working` is an archival label for this 0.3.0 WebView build, not the earlier HTTP-only 0.2.2. Runtime code, resources, manifest, Gradle files and wrapper are preserved without changes.

- [Working tag](https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/tree/v0.2.2-working)
- [INSFAV Thumb Fetcher — Working WebView Build](https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/releases/tag/v0.2.2-working)
- Exact original APK: 2,493,548 bytes, SHA-256 `1f03bfb584ce951bc0c7da67f3df81f5a130071302ba29e2c8106f1241cb24fa`.
- The existing development APK v2 signature is retained; no signing key is uploaded.
- Known issues retained: inaccessible accounts can pause/error the queue; the Test Cookies section is planned for removal. Neither is changed in this archive.
- The preservation workflow checks source equality, reachable Git history, clean compilation/tests and emulator startup before publication. Emulator startup does not prove authenticated Instagram access.

## Future integration into INSFAV

These boundaries document a later integration. No code is transferred to the main INSFAV repository in this archival task.

| Function | Reference code |
| --- | --- |
| Instagram session management | `CookieJar.java`, `CookieVault.java`, `SessionState.java`, `ThumbApplication.java`; scoped CookieManager copies in `WebViewProfileProvider.java` |
| Hidden WebView engine | `ProfileProvider.java`, `WebViewProfileProvider.java`; main-thread creation, callbacks, polling, cancellation and cleanup |
| Profile-photo URL discovery | `app/src/main/assets/profile-probe.js`, `WebProfilePolicy.java`; account identity, loaded avatar, trusted HTTPS sources |
| Image download and conversion | `PublicProfileProvider.java` CDN transport, `ThumbnailCodec.java`; bounded decode, crop and 128 × 128 WebP quality 78 |
| Queue and state | `QueueEngine.java`, `QueueStore.java`, `QueueControl.java`, `RunPolicy.java`, `FetchService.java`, `DownloadWorker.java`, `RecoveryWorker.java`, `WorkScheduler.java` and notification/control receivers |
| Verified file output | `Identity.java`, `OutputFolder.java`; normalized username, SAF `.thumbnails/username.webp`, staging/readback and valid-file preservation |
| Import and diagnostics | `BackupParser.java`, `Diagnostics.java`, `FailureText.java`; original JSON read only and redacted fixed-code logs |

Future integration must preserve the DOM asset, WebView main-looper ownership/cleanup, encrypted vault, serial queue, schedule constraints and SAF grants. Use main INSFAV's existing accounts rather than adding collection or JSON editing. Test-gate removal, unavailable-account fixes and full-resolution viewing are later changes, after preserving this reference.


Independent native Java Android helper. Reads a local INSFAV JSON backup and writes `.thumbnails/username.webp`: 128 × 128, center crop, WebP quality 78. The main INSFAV app and original JSON are unchanged.

## Hidden WebView profile engine

The default provider now loads the exact Instagram profile page in a JavaScript-enabled Android WebView with the user's saved session. The WebView is never attached to a window or shown. It is created, measured, navigated, polled and destroyed on the main Android thread. A worker performs bounded waits, image downloading and conversion.

Page-finished events do not establish success. A bounded DOM probe waits for a decoded profile image. It accepts an exact-account profile-picture image in the profile header/main region, excluding article/navigation/sidebar images, or a trusted image URL in structured data with the requested username and numeric account ID. The latter is also loaded as an image before acceptance. Wrong accounts, missing data, known default avatars, generic og:image and arbitrary large images do not establish success. Discovery is conservative and may fail after Instagram markup changes.

Only allowed HTTPS Instagram/CDN resources are loaded. External navigation, file/content access, mixed content, popups, permission prompts and service-worker networking are blocked. TLS failures are never ignored. Login, challenges, access restrictions, renderer failures and HTTP 429 pause work. Retry-After is retained. No CAPTCHA bypass or private API is directly called by the app; Instagram's page JavaScript performs its own normal requests.

## First use: one profile, then five

1. Select JSON and a writable output parent or existing `.thumbnails` folder.
2. Select Cookies → Paste Cookies → Save Cookies. Netscape cookies.txt text and HTTP Cookie headers are supported; no cookie file picker exists. Values are hidden after saving.
3. More → **Test One Profile**. This tests the first eligible account with a real fetch, decode and completed file validation. It requires at least one account; it never unlocks batch work.
4. **Test Cookies** tests up to five eligible imported accounts, including all five when available. It no longer stops immediately after the third photo. At least three successful photo fetches and validated outputs are needed to unlock Start Missing; actual counts are shown. A blocker can stop the test early.
5. Set/save delays and working hours, optionally enable Background Processing, then Start Missing. Tests are explicit diagnostics and may run outside the schedule. Normal work obeys the saved window.

Previously recorded HTTP-provider proof is invalidated on upgrade. A session cookie's presence is not proof of accepted authentication: homepage viewer metadata must match ds_user_id for Session verified. When unsupported, session identity remains unverified, and actual profile access is tested separately. Existing files alone never establish live proof. Valid files are preserved even during preflight.

**The owner reported successful real downloads with this preserved APK. The developer has not independently repeated authenticated one/five-account tests in this environment.** Automated fixtures are not proof of real Instagram access. Use the two device tests above; export diagnostics on failure, never cookies.

## Queue, storage and scheduling

One account at a time; defaults are 15–30 seconds and 02:00–07:00 local time, Background Processing off. Overnight windows are supported. Pause/Resume/Stop, SQLite queue persistence, dataSync foreground notification, WorkManager scheduling and Android quota/time limits retain their existing behavior. Work may be delayed or stopped by Android; invisible WebView execution is not guaranteed while backgrounded.

Profile-page 404 results become Skipped (unavailable for this run), separately from Failed and success, without declaring the account deleted. HTTP 200 error pages, missing image data and network/login failures do not become nonexistent accounts. Start Missing explicitly rechecks skipped rows; Retry Failed selects failed rows. Existing valid WebPs and unrelated files are preserved. No state, cookies or logs are stored alongside thumbnails.

## Session privacy

The authoritative pasted cookie jar remains AES-256-GCM encrypted in private storage with an Android Keystore key. Temporary host-only session copies are applied to www.instagram.com with CookieManager callbacks before navigation. Third-party cookies are disabled; the native CDN downloader has no cookie jar.

WebView's internal browser storage is platform-managed and is not the encrypted vault. The app clears browser cookies/storage on process startup and before/after each operation, destroys each view, avoids cache reuse and does not persist rotated cookies into the vault. An abrupt process/device failure can leave private browser data until the next startup purge; secure erasure or encryption of Chromium's internal store is not guaranteed. Android backups/device transfer are disabled.

No cookie/header/body/URL/username/exception/JavaScript-console values are exported to diagnostics. The cookie editor retains its screenshot/autofill/state protections. Remove Cookies deletes the encrypted jar, purges browser session state and revokes access proof. No advertisements or analytics SDK is included.

## Build and verification

JDK 17, Gradle 8.13, compile SDK 35, minimum 26, target 37:

```sh
node --test tools/profile-probe.test.cjs
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

No new production dependency: WebView uses the installed Android System WebView package. AndroidX WorkManager 2.10.1 remains the only direct runtime dependency. JUnit/Robolectric are test-only; Node is used only for tests. System WebView/renderer memory and storage are additional to the APK and require device measurement. Release applies R8/resource shrinking; signing keys and real sessions/backups are excluded from GitHub. See ARCHITECTURE.md, TESTING.md and STATUS.md.
