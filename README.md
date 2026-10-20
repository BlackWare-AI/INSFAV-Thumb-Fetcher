# INSFAV Thumb Fetcher 0.3.0

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

**Real authenticated one-account and five-account downloads have not been verified in this development environment.** Automated fixtures are not proof of real Instagram access. Use the two device tests above; export diagnostics on failure, never cookies.

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
