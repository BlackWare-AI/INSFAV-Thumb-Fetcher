# Verification

Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` with JDK 17 and Android SDK 35. CI retains APK/mapping/test/lint reports. Automated cookies are synthetic TEST_* values. No live authenticated request occurs in tests.

## Automated coverage

- Three JSON formats, 32 MiB cap, invalid/duplicate counts, order, Turkish locale, 1k/5k/10k synthetic imports and preserving valid data after rejection.
- Netscape/HttpOnly and HTTP-header parsing; domain/path/expiry; ambiguity, input bounds and injection rejection.
- AES-GCM roundtrip, ciphertext not plaintext, tamper failure and remove. A test AES key is injected; Android Keystore itself still needs a device.
- Viewer attestation versus unrelated profiles; cookie headers only on permitted Instagram HTTPS requests, never image CDNs; login/redirect/404/403/429/network-loss fixtures.
- Three independent fixture image downloads required for preflight; existing media cannot supply proof by itself. Unverified sessions cannot start batch. One scheduled execution makes at most one profile retrieval (cached files are checked in bounded groups). Valid cached outputs skip network.
- Overnight/end-exclusive windows, next local start, delay bounds, invalid time inputs and the explicit arming/background/proof/pause/error predicates.
- 429 saves Retry-After and pauses, no out-of-window network, Pause finishes an in-flight write without starting another account, and cookie changes invalidate proof/scheduling.
- Native 128 × 128 WebP decode/crop/encode, broken/truncated data, SAF readback/skips, corrupt replacement, partial recovery, revoked permissions and unsupported rename.
- Private SQLite persistence/retry eligibility and bounded redacted diagnostics.

## Physical-device acceptance — outstanding

1. Install the APK, select JSON containing at least three accessible accounts and choose a writable output parent.
2. Paste cookies only into the app; Save Cookies. Verify the editor clears/hides and only Cookies Configured remains. Restart to check encrypted persistence. Never send cookies in chat/issues.
3. Test Cookies. Verify the session and at least 3 of up to 5 profile downloads. Missing/changed HTML, login and challenges must fail. If it fails, export diagnostics; scheduling cannot repair an unavailable provider.
4. Refresh the same folder in unchanged INSFAV and confirm each .thumbnails/username.webp maps to its account. Verify dimensions, WebP format and unchanged existing media/JSON.
5. Test custom delays, same-day/overnight windows, finishing an in-flight operation at the boundary, no out-of-window requests and eligible next-window recovery.
6. Test expired/revoked sessions, login/challenge, 429/Retry-After, unavailable/private profiles, missing URL, decode errors, lost grants and write errors. Verify distinct reasons and no secrets in logs.
7. Test background processing on/off, Pause/Resume/Stop, process death, reboot/unlock, Doze, Android 15+ dataSync time limits and Android 16+ job quotas. Dispatch can be deferred/stopped.
8. Measure 1k/5k/10k import, memory, UI and actual SAF performance on Android 8 and a current device. Target SDK 37 is not evidence of Android 17 device testing.
9. Check signing certificate compatibility for updating old test APKs. If the old certificate is unavailable, reinstall needs private settings/queue re-import; external JSON/thumbnails remain intact.

No production acceptance or live-provider success is claimed until these checks pass. Actual automated results belong in STATUS.md.
