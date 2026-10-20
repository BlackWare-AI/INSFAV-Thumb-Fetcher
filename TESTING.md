# Verification — WebView revision

Run Node DOM-probe fixtures and JVM/native Android fixtures:

```sh
node --test tools/profile-probe.test.cjs
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Node tests execute the actual asset in deterministic mocked DOMs. They cover exact account avatar acceptance, posts/navigation exclusion, similar names, stale canonical data, image readiness, structured identity, default avatars, trusted sources, ambiguity, login/challenge detection, routed errors and viewer identity. These are not real Chromium rendering/network tests.

Robolectric tests cover native snapshot validation, navigation/resource policy, subresource rate limiting, 404 classification, invalid callback results, one-profile preflight, all five profile attempts, provider closing on failure and existing JSON/codec/storage/queue/policy behaviors. Test sessions/images are synthetic; no cookies or personal backup are committed.

## Required physical-device order — not yet completed

1. Install 0.3.0 and confirm Android System WebView is available. Import a small JSON with one accessible account and choose a writable folder. Paste a fresh session only into the app.
2. More → Test One Profile. Confirm the actual intended photo becomes a valid 128 × 128 WebP and the UI reports one successful verification. A generic icon, another account's photo, existing file alone or HTTP 200 is not success.
3. Import five accessible accounts and use Test Cookies. Confirm all five actual photos and their account mapping. Record any early blocker and exported error code. Never report this gate as passed from fixtures.
4. Open/refresh the folder in unchanged INSFAV and visually confirm mapping. Verify original JSON and existing files remain unchanged.
5. Check slow JS/network, lazy avatar loading, private/restricted profiles, changed DOM, deleted/unavailable profiles, login, challenges, SSL failure, renderer death, HTTP 429/Retry-After and network loss. Posts must never be accepted as avatars.
6. Measure memory across repeated accounts and confirm no view/window accumulation, console/credential disclosure or leftover active page work after Pause/Stop/timeout. Inspect browser cleanup after normal completion and abrupt process death; platform private-store secure erasure/encryption is not guaranteed.
7. Validate background on/off, lock screen, scheduled overnight windows, Doze, process kill/restart, reboot, foreground service timeout/job quotas and storage grant loss. Invisible WebView continuity is not guaranteed by Android.
8. Check encrypted vault persistence, fresh-cookie replacement, Remove Cookies and startup purging of any previous browser session. No secrets may appear in exported diagnostics.
9. Test Android 8 and a current physical device. Measure 1k/5k/10k import and actual SAF/indexing performance; test APK signing/update compatibility.

No device/emulator is connected to this development environment. Authenticated one/five-account Instagram downloads, renderer lifecycle under real background limits and real INSFAV rendering remain unverified. STATUS.md distinguishes completed CI checks from those outstanding gates.
