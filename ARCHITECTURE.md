# Architecture — cookie and scheduling revision

One native Activity uses platform Views. Cookies expand inline; there is no WebView, cookie file picker or upload screen. JSON parsing and image conversion retain the previous bounded implementations. Identity.outputFilename writes normalized username.webp under .thumbnails. The main INSFAV application is not modified.

## Secret boundary

CookieJar parses at most 64 KiB of pasted text. Netscape HttpOnly/domain/path/expiry and standard Cookie-header syntax are supported; ambiguous values, malformed fields and control/header injection are rejected. Third-party domains are ignored. Only instagram.com/www.instagram.com HTTPS requests receive scoped cookies; CDNs never do. Unknown-expiry header cookies need server validation. Response-cookie rotation is not automatically persisted: expired/changed sessions need a fresh paste and retest.

CookieVault writes an AtomicFile encrypted with AES-256-GCM, a fresh nonce and fixed associated data. The production key stays in Android Keystore. Decryption/tamper/key-loss failures require reconfiguration. The editor is excluded from saved view state/autofill/IME learning and cleared after save/onStop. FLAG_SECURE protects the visible editor. Clipboard reads occur only on Paste Cookies; the app does not alter the clipboard. Backup/device transfer is excluded. Secrets, headers, bodies and raw exception messages never enter logs, WorkManager Data, notifications or repository fixtures.

SessionState stores only a random non-secret cookie generation, verified-profile count and a fixed session-evidence enum. Profile-access proof does not attest cookie acceptance. Save/Remove invalidates proof and disarms automatic work. Configured cookies alone are not authentication evidence.

## Network and live preflight

PublicProfileProvider requests the Instagram homepage and checks server-returned viewer metadata, matched to ds_user_id when present. Embedded JSON and the bounded _sharedData JSON assignment are parsed without executing JavaScript. Missing/unsupported viewer metadata returns COOKIE_AUTH_UNVERIFIED. Preflight alone handles this specific code by recording UNVERIFIED session evidence and testing actual profile pages. Other authentication/transport failures are never ignored. Explicit login/challenge/rate-limit checks remain enforced on every request.

Profile URLs are https://www.instagram.com/{username}/. A photo URL must be bound to the same normalized username and numeric account ID in a structured object. id/pk, profile_pic_url(_hd) and HD URL-info variants are recognized. Explicit default-avatar metadata and known default URL markers are rejected; og:image alone is never sufficient. Missing anonymous-avatar metadata is no longer automatically rejected. No private API, CDN guessing or challenge bypass is used. This is source-metadata attestation, not independent human visual identification.

Preflight validates its folder/account prerequisites before network access, tests the session and attempts up to five distinct imported profiles. Three actual downloaded/decoded images plus verified writes or already-valid untouched outputs are required. Existing files alone never supply live proof: their images are still fetched during preflight. Only the current cookie generation can receive profile-access proof. Three validated target-bound photo downloads can establish access even when homepage session identity is unverified. UI and diagnostics keep those outcomes separate and never call unknown cookie acceptance valid. The explicit diagnostic test can run outside working hours; ordinary batch/automatic processing cannot.

## Durable serial execution

QueueEngine is shared by FetchService and DownloadWorker. SQLite/WAL preserves order, eligibility, pending/terminal states and fixed error codes. Schema migration preserves accounts and revalidates terminal rows for the new output contract. A process-wide atomic owner lock prevents service/worker concurrency.

FetchService runs an explicit user batch/preflight with dataSync notification. DownloadWorker checks up to 128 cached files and retrieves at most one network profile per persisted WorkManager job with bounded foreground execution. A unique chain preserves ordering, and a 15-minute recovery guard covers eligible unfinished service/process work. WorkManager manages reboot recovery internally; no foreground service is started directly by a boot receiver and no exact alarm permission is requested.

RunPolicy supports same-day/overnight local windows, inclusive start and exclusive end. Hours are rechecked after waits and before network. The current request/write finishes at a window boundary, then work pauses until a future eligible window. Random delays default to 15–30 seconds and support 1–86400 seconds. Cached checks do not incur network delays.

Automatic work requires Background Processing, an explicitly armed queue, no manual pause/stop, no policy-error block, current cookie proof, a configured vault and unfinished rows. Turning background on alone does not arm a queue. Pause blocks automatic continuation; Stop disarms. 429 retains Retry-After and blocks random retries. Cookie, challenge, storage and repeated-error blocks require user action. Android quota/time limits, Doze, force-stop and provider latency may defer/stop work. No exact or continuous-execution guarantee is made.

## Files, counts and errors

OutputFolder reuses the exact chosen .thumbnails folder, indexes it once, skips valid WebPs, stages .part writes and verifies readback/rename/final bytes. Unsupported rename fails explicitly; SAF atomicity and power-loss durability vary. Old .thumnails/hash files and unrelated photos are untouched. State/logs/vault stay private; original JSON is never modified/uploaded.

Existing, successful, failed and pending counts remain separate. Terminal failures contribute to processed percentage but never success. Error groups distinguish invalid/expired/unverified cookies, login, unavailable/restricted profile, rate limit, missing URL, download, decode, storage and Android restrictions. Export includes bounded fixed event/code vocabulary, HTTP integers and aggregate counts; account identifiers and secrets are redacted.

## Primary references

- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/privacy-and-security/cryptography
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/develop/background-work/services/fgs/timeout
