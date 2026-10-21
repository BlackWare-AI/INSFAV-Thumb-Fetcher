# Verification for 0.4.0

## Automated checks

- Parser: all three JSON formats; invalid imports preserve existing data; normalization, duplicates, limits and anonymous 1,000 / 5,000 / 10,000-account inputs.
- Codec: portrait, landscape and tiny source images encode genuine 256 × 256 WebP; legacy 128 × 128 remains valid; corrupt/truncated files are rejected.
- Queue: direct start with no preflight; more than three unavailable accounts continue to a valid account; image failures do not stop the queue; restrictions and Retry-After pause; existing files are preserved; explicit legacy refresh only replaces 128-pixel outputs; pause cancels all uncommitted assignments and resume processes them.
- Synthetic queue concurrency matrix at 1, 2, 5, 10, 30: concurrent peak, unique assignment and exactly-once commits. A separate test uses the real shared one-second launch gate.
- UI: inline secret editor, no Test Cookies control, concurrency field, private text not restored.
- DOM fixtures: identity/route binding, loaded images, unavailable pages, login/challenge, wrong accounts/posts/default avatars, explicit HD field and offered srcset candidate.
- Android API 29 emulator: application launch and real WebView reuse with decoded anonymous fixture images at requested 1, 2, 5, 10, 30. Effective pool size is capped by emulator memory policy; tests are offline and never send fixture sessions to Instagram.
- SAF: valid existing skip, broken output replacement, partial recovery, permission loss, failed rename, legacy refresh and recovery backup handling.

Run `node --test tools/profile-probe.test.cjs` and `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`. CI publishes test reports with APK artifacts. `tools/audit-history.py` scans reachable Git history; it does not replace manual review of newly staged files.

## Required physical-device follow-up

Not claimed as completed by offline tests: live Instagram downloads with current user cookies, stable 30-page operation, long background runs under Samsung/Xiaomi power management, actual OEM renderer memory usage, and main INSFAV display of generated files. Keep default concurrency at 1 until measured on the target device. Do not upload real cookies, user JSON or private logs as fixtures.

For device validation use one known accessible account first, then five; include unavailable accounts before a valid one. Verify Skipped advances and the valid account downloads. Increase requested concurrency gradually while inspecting Active WebViews and diagnostic memory/pool codes. Verify login/challenge/429 stop new starts and Android interruption leaves unfinished accounts available for Resume. Test the explicit legacy refresh and in-memory viewer separately.
