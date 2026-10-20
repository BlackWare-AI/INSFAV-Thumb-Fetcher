# INSFAV Thumb Fetcher — Working WebView Build

Tag: `v0.2.2-working`. Actual application version: **0.3.0**, versionCode 6. The archival tag does not refer to the earlier HTTP-only 0.2.2 application. Version information and runtime code are unchanged.

Preserved functionality:
- WebView-based Instagram profile-photo retrieval.
- Access through the user's saved Instagram session.
- Accounts read from INSFAV JSON.
- 128 × 128 WebP conversion, center crop and quality 78.
- Output to `.thumbnails/username.webp`.
- Serial queue, random delays, pause/resume, background processing and working hours.

Known issues retained:
- Inaccessible accounts can cause queue errors or pauses.
- The Test Cookies/preflight verification section will be removed in a later change.

The owner reported successful real downloads on 2026-10-20. Developer-side authenticated one/five-account tests have not been independently repeated. Main INSFAV is unchanged.

Original application commit: `3fc7a40a6d7aa7f30e264b776a3a87df568f1285`.
Original CI: https://github.com/BlackWare-AI/INSFAV-Thumb-Fetcher/actions/runs/39463080746 .

The release attaches the exact previously delivered working development APK:
- `INSFAV-Thumb-Fetcher-0.3.0.apk`, 2,493,548 bytes.
- SHA-256: `1f03bfb584ce951bc0c7da67f3df81f5a130071302ba29e2c8106f1241cb24fa`.
- Existing APK v2 signature verified.
- Certificate SHA-256: `df133086e89b09e3b264927235dada19bfd69fe4c1671e2aeab0a4425ef396f7`.
- No signing key or password is included. This is a development certificate, not a production distribution certificate.

Before publishing, the preservation workflow checks runtime-source equality, scans reachable Git history, builds from a fresh checkout, runs existing tests/lint, and launches both the fresh APK and original working APK on an Android emulator. Startup checks do not authenticate to Instagram.
