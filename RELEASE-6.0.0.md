INSFAV Thumb Fetcher 6.0.0 (versionCode 13)

- Ported INSFAV Plus's Legacy and YTDLnis-style Instagram browser and cookie extraction flows. Both methods share the persistent first-party WebView login; switching methods preserves that login. Added method controls, refresh, browser back navigation and English/Turkish session controls in the AMOLED interface.
- Added live cookies and saved Netscape views, explicit secure save, per-method removal and confirmed scoped logout. Browser snapshots use Android Keystore AES-GCM and stay separate from existing manual cookies. Cookie values are never included in diagnostics, CI reports or release assets.
- Connected successful thumbnail/HD browser requests to automatic refresh of the selected encrypted cookie snapshot. Retrieval continues using the same live WebView session, with the existing manual-cookie isolation, profile probe, download, image validation, naming and output-folder behavior preserved.
- CookieManager remains authoritative. Optional matching Chromium metadata preserves scope, expiry, Secure and HttpOnly flags where available; missing metadata uses request-scoped, session-only HTTPS export policy. This is not a lossless browser cookie database export, and no expiry is invented.
- Existing application ID and signing certificate are preserved for in-place updates from build 12. No app-data reset or automatic queue resume is performed.

Validation: the publication workflow requires unit tests, lint, offline Android browser persistence checks (reopen, force stop, process loss, update reinstall and reboot), release signature continuity and installation/launch of the signed release APK. Offline synthetic fixtures cover both extraction methods and Netscape format. The separate Android verification workflow covers thumbnail probe fixtures and the WebView pool matrix.

Not verified: a real Instagram login/2FA, live Instagram page or thumbnail/HD retrieval, physical-device behavior, OEM background limits and long-running live sessions. Synthetic/offline checks do not establish those results. Instagram may impose login, challenge or rate limits.
