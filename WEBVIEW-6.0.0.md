# 6.0.0 WebView integration

Reference: BlackWare-AI/INSFAV-Plus at `eb8e17799d7ff7dd5c514ea6b64352c7da497829`. Target baseline: `f2efc03378e00c0750ad9a56d3ea3d5296006260` (0.5.0, build 12). Source checkout remains unchanged.

## Inspection and port boundaries

Plus `web/index.html`/`web/app.js` method choices call its local Android bridge, which dispatches to the fetcher session APIs. Its `InstagramLoginActivity` is the actual first-party Instagram browser: both Legacy and YTDLnis-style choices use that Activity and the default persistent CookieManager. They are extraction methods sharing one login, rather than separate browser engines or a dependency on the YTDLnis app. Legacy calls `BrowserCookies.read`; Netscape calls `NetscapeBrowserCookies.read`, serializes seven-column Netscape rows and parses them back into a jar.

Ported `InstagramLoginActivity`, `BrowserCookies`, `NetscapeBrowserCookies`, and `BrowserCookieStore` with package adaptation. Added engine selection/status and browser-copy logout invalidation to the target's existing `InstagramSession`. Extended the existing `CookieVault` with separately named browser vaults, retaining manual `cookies.enc`, Android Keystore alias `insfav.cookie.aes.v1`, AES-GCM wire format/AAD and compatibility with existing encrypted jars. Added optional Secure/HttpOnly fields with backward-compatible decoding.

The target uses native Android controls, so its main Activity invokes the same session APIs directly. No Plus favorites screen, bulk downloader, unrelated HTML/CSS, update system or app bridge is copied. There is intentionally no JavaScript interface on Instagram pages in either source or target. The existing target `profile-probe.js` and `evaluateJavascript`/JSON callback path stay intact to protect its thumbnail behavior; source probe changes unrelated to the browser port are excluded. No new Android dependency or permission is needed: AndroidX WebKit 1.14.0, INTERNET, a non-exported login Activity, no cleartext traffic, disabled app backup and existing extraction rules already satisfy the port.

## Browser and retrieval chain

Native method controls -> `InstagramSession.selectEngine` -> `InstagramLoginActivity` -> shared persistent CookieManager -> `BrowserCookieStore` -> separate encrypted Legacy/Netscape copies. Methods can be switched on the main screen or browser screen; browser recreation never clears cookies/storage. The session screen follows the Plus AMOLED layout, localized English/Turkish controls, cookie modal, refresh, history/back, scoped logout and per-method removal.

Queue/HD viewer -> `InstagramSession.open` -> existing `WebViewProfileProvider` -> persistent CookieManager and target probe -> account-bound image URL -> existing trusted HTTPS image transfer. Successful browser resolutions call `BrowserCookieStore.updated` asynchronously. Saved copies establish local configuration readiness; they never replace a fresher live cookie, certify an authenticated login or automatically resume retrieval. Manual cookies retain the existing separate WebKit profile. The existing target authentication check, challenge latch, cooldown behavior, image host/identity validation, codec, SAF staging/readback/rename, filename and output index remain in place.

Both browser methods retain source WebView defaults for User-Agent, JavaScript/DOM storage, redirect restriction to HTTPS Instagram hosts, file/content access denied, mixed-content blocking, third-party cookies disabled, SSL errors cancelled, console suppression, permission/geolocation denial, renderer-loss handling, cookie flush and exclusive session lease. Added WebView pause/resume calls and disabled selection/long press on the native cookie text; `FLAG_SECURE` and disabled saved-view-state prevent secret screenshots and restored text. The remote Instagram page retains its own usable input behavior.

## Cookie fidelity and security limits

CookieManager provides the current Cookie request header, not complete Set-Cookie attributes. As in Plus, optional **read-only** Chromium metadata is accepted only when its plaintext value matches the authoritative live API value. Unsupported, locked, missing or encrypted-value schemas fall back to the API; stale/deleted database rows cannot replace/revive a cookie. Known domain/path/expiry are retained and Chromium expiry is converted to Unix seconds.

The small target adaptation preserves actual Secure and HttpOnly metadata if columns exist, and preserves those fields when parsing Netscape input. Without metadata, host/path describe the scoped request, expiry 0 means session-only export (not the original cookie lifetime), and Secure TRUE is a restrictive HTTPS-only export policy, not a claim about the unknown original flag. Unknown HttpOnly is not inferred from a cookie name. Export comments explain these limits. SameSite and other unavailable fields cannot be expressed in Netscape format. No fabricated future expiry is used.

Export is the source-style in-app Netscape representation and encrypted internal snapshot, not an automatic plaintext file in Downloads. There is no credential-sharing path to another app. Cookie text never enters diagnostics or public CI artifacts. Only synthetic fixture credentials are committed. Snapshot writes are serialized, revision checked and verified by encrypted readback. Explicit removal cancels stale writes and prevents automatic recreation until another explicit save; logout invalidates both browser copies while preserving manual cookies, queue and images. No user data is reset on upgrade.

## Verification

`BrowserCookieSnapshotTest` ports the source regression cases: missing/incompatible/stale database, live HttpOnly extraction, expiry/scope, Netscape round trip, encrypted write/update/no-op, per-method removal and revision cancellation. Additional cases preserve Secure FALSE and HttpOnly and ensure engine selection reaches the existing provider.

`BrowserSessionUiTest` opens both methods, checks initial URL/security flags, live cookies, refresh, modal back and secret clearing on pause. Existing queue, image codec, storage, HD and DOM fixtures remain part of the full suite.

`SessionInstrumentation` exercises real offline Chromium cookies and localStorage across reopen, force stop, crash, background kill request, reinstall and reboot. At each check it extracts both methods, parses Netscape output, reads the encrypted snapshots and verifies no plaintext fixture value exists in their files. Existing manual isolation/scoped logout and thumbnail/queue sentinels remain. These checks do not establish real Instagram behavior or that Android reclaimed a background process.

The 6.0.0 publication workflow depends on both full Android verification (including pool matrix) and session durability, uses only the existing Actions signing secrets, checks package/version/release flags and previous certificate, installs build 12 then upgrades/launches the signed new APK on an API 35 emulator, and publishes only afterward. Re-running refuses to overwrite an existing release. Physical-device and authenticated Instagram login/2FA, page loading, downloads and long-running behavior require user-controlled access and are explicitly unverified.
