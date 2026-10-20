# Architecture — hidden WebView provider

One native Activity and one serial QueueEngine retain the JSON parser, queue, scheduling, SAF writer and ThumbnailCodec. ProfileProvider is now a closeable session boundary: authenticate, fetch, cancel and close. Production uses WebViewProfileProvider; PublicProfileProvider remains the bounded HTTPS CDN downloader and a testable legacy parser, with no automatic fallback that reports missing metadata as success.

## Thread and lifecycle boundary

WebViewProfileProvider is called on a worker. Each homepage/profile operation posts creation and navigation to the main looper. CookieManager removes previous cookies, installs validated host-only session copies sequentially with callbacks, then loads the exact profile URL using the system WebView user agent. Nothing is attached to a user-facing window. A fixed viewport supports layout without attaching a browser UI.

A 500 ms poll uses evaluateJavascript with a static app asset. onPageFinished logs only a fixed event. The script returns a small snapshot with explicit identity, image completion and status; no HTML bodies or cookies are returned. Java checks callback encoding, size, page origin/path, username, source type, dimensions and trusted image host independently. The worker then downloads the discovered URL using the existing HTTPS limits and converts/writes through unchanged components.

Homepage identity polling allows 10 seconds; profile DOM/image polling allows 45 seconds. Overall setup/operation deadlines are 18/55 seconds with bounded cleanup. Cancellation, TLS errors, main-frame transport failures, renderer death, disallowed navigation and restrictions complete through the same cleanup path. The view stops loading, loses clients/listeners, disables JS, clears history/cache, is destroyed, and browser cookies/storage are purged. Cleanup completion precedes the next profile; missing cleanup acknowledgement blocks work. No app JS bridge is exposed.

ServiceWorker networking, mixed content, file/content access, dialogs, permissions and file choosers are disabled. Only the exact Instagram origins and trusted CDN suffixes are permitted for resources; main navigation stays at the requested homepage/profile. HTTP 429/401 from Instagram subresources also block. Native image redirects remain trusted HTTPS only and never receive session cookies.

## Identity and readiness

The DOM probe accepts profile-picture alt semantics tied to the exact requested username in header/main, excludes article/nav/aside, and checks canonical route identity. A structured fallback requires matching username and numeric ID, with recognized profile-photo fields. Only a loaded image with nonzero bounded natural dimensions is accepted. Structured images absent from DOM are loaded in a single temporary Image before acceptance. No resource URL guessing, arbitrary-largest-image selection, og:image-only matching or post extraction occurs. This is source/DOM attestation, not independent human visual verification; real account acceptance must be checked on device.

Wrong identity, ambiguity and known defaults cannot become success. A routed server error remains PROFILE_PAGE_UNAVAILABLE, never proof of deletion. Main-page 404 is SKIPPED for this run; missing metadata and login/challenge/network failures remain separate. The prior 0.2.3 separation of skipped/failed/success counts is retained.

## Proof and diagnostics

More → Test One Profile attempts one eligible profile and never unlocks batch work. Test Cookies attempts up to five eligible profiles without stopping at three successes; at least three successful live fetch/decode/output validations establish current-generation access proof. An explicit blocker still stops immediately. Tests do not arm automatic work. The application invalidates old provider proof on upgrade, preserving accounts, original JSON, vault and completed external files.

Homepage viewer metadata matched to ds_user_id can attest session identity. An unsupported homepage records UNVERIFIED separately and still allows profile access testing. Three public photo downloads alone never assert that cookies were accepted.

Diagnostics export fixed error/event codes, aggregate counters and integer HTTP codes only. WEBVIEW CREATED, COOKIES_APPLIED, PAGE_FINISHED, PROFILE_IMAGE_RESOLVED and CLEANED identify progress without secrets. Native/JS exceptions, console output, request headers, raw URLs, usernames and bodies are not exported.

## Secret persistence

CookieVault remains AES-256-GCM/Android Keystore and private AtomicFile. CookieManager receives host-only, session-only working copies; sessionid is HttpOnly while CSRF cookies remain accessible to Instagram's own JavaScript. Domain/path/expiry constraints are checked. Cookies are never given to the native CDN downloader. Rotated browser cookies are not saved into the authoritative vault.

Chromium's browser store is private but platform-managed, with no app-controlled at-rest encryption guarantee. Cookie/WebStorage purges occur before/after operations, at process startup and on Remove Cookies. Abrupt process death can leave browser data until startup; these lifecycle purges are not secure deletion. Backup/device transfer are disabled. This limitation must remain explicit.

## Durable work and files

Foreground service and bounded WorkManager jobs retain a process-wide owner lock. The queue remains private SQLite/WAL; profile work is serial and does not automatically start. Pause/Stop and time-window boundaries finish/cancel a bounded current request and preserve unfinished work. Background Processing must be explicitly enabled and the queue armed. Android may suspend/kill a hidden renderer or apply dataSync/job quotas; failures pause rather than claim continuous background execution.

OutputFolder and ThumbnailCodec are unchanged: .thumbnails/username.webp, 128 × 128 WebP quality 78, sampled decoding, center crop, staging/readback/final verification and existing-valid-file preservation. No changes to the main INSFAV repository or JSON content.

## Platform references

- https://developer.android.com/reference/android/webkit/WebView
- https://developer.android.com/reference/android/webkit/CookieManager
- https://developer.android.com/reference/android/webkit/WebViewClient
- https://developer.android.com/develop/background-work/services/fgs/timeout
