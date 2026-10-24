# Verification for 0.5.0

## Automated checks

- Parser: all three JSON formats; invalid imports preserve existing data; normalization, duplicates, limits and anonymous 1,000 / 5,000 / 10,000-account inputs.
- Codec: portrait, landscape and tiny source images encode genuine 256 × 256 WebP; legacy 128 × 128 remains valid; corrupt/truncated files are rejected.
- Queue: direct start with no preflight; more than three unavailable accounts continue to a valid account; image failures do not stop the queue; restrictions and Retry-After pause; existing files are preserved; re-import preserves durable status/order; interrupted claims return to pending; notification progress includes Retry Later rows.
- Retry Timeouts: selects only completed `WEBVIEW_TIMEOUT` failures; excludes successful, pending and `PROFILE_PAGE_UNAVAILABLE` accounts; persists one extra-attempt state across database reopen; stores successful recovery; preserves a different second error code; prevents a second special retry; runs only timeout targets when unrelated pending rows exist.
- Retry Missing Images: selects only `PROFILE_IMAGE_URL_MISSING` and `PROFILE_DATA_AMBIGUOUS`; recovered rows clear error state; second errors preserve their actual code; unavailable, successful and pending rows are excluded; completed attempts survive database reopen; result snapshot and UTF-8 TXT formatter include only terminal retry failures.
- Synthetic queue concurrency matrix at 1, 2, 5, 10, 30: concurrent peak, unique assignment and exactly-once commits. A separate test uses the real shared one-second launch gate.
- UI: inline secret editor, no Test Cookies control, concurrency field, private text not restored.
- DOM fixtures: identity/route binding, loaded images, unavailable pages, login/challenge, wrong accounts/posts/default avatars, explicit HD field and offered srcset candidate.
- Android API 29 emulator: application launch and real WebView reuse with decoded anonymous fixture images at requested 1, 2, 5, 10, 30. Effective pool size is capped by emulator memory policy; tests are offline and never send fixture sessions to Instagram.
- SAF: valid existing skip, broken output replacement, partial recovery, permission loss, failed rename, cached URI lookup, explicit full index verification and resolution audit.

Run `node --test tools/profile-probe.test.cjs` and `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`. Build 11 is signed with the persistent release key stored in repository Actions secrets; the workflow fails closed if any signing secret is missing. CI publishes test reports with APK artifacts. `tools/audit-history.py` scans reachable Git history; it does not replace manual review of newly staged files.

## Required physical-device follow-up

Not claimed as completed by offline tests: live Instagram downloads with current user cookies, stable 30-page operation, long background runs under Samsung/Xiaomi power management, actual OEM renderer memory usage, and main INSFAV display of generated files. Keep default concurrency at 1 until measured on the target device. Do not upload real cookies, user JSON or private logs as fixtures.

For device validation use one known accessible account first, then five; include unavailable accounts before a valid one. Verify Skipped advances and the valid account downloads. Increase requested concurrency gradually while inspecting Active WebViews and diagnostic memory/pool codes. Verify login/challenge/429 stop new starts and Android interruption leaves unfinished accounts available for Resume. Test the explicit legacy refresh and in-memory viewer separately.
