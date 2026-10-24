# 0.5.0 build 11 development status

This build adds a durable, independent one-time Retry Missing Images action for `PROFILE_IMAGE_URL_MISSING` and `PROFILE_DATA_AMBIGUOUS`, alongside the existing Retry Timeouts action. Both are restricted to their selected error codes, exclude successful, pending and unavailable accounts, survive process restart, preserve the actual latest error, show Instagram links, and export UTF-8 failure reports through SAF. Application version is 0.5.0 (versionCode 11).

The output index and account queue are stored in private SQLite. Reimporting an unchanged account list keeps completion status; changed lists preserve rows for accounts still present. A full output folder index happens on first use or through **More → Verify Thumbnail Index**. Start Missing only re-enables unfinished accounts and does not reopen completed output files on each run. SAF change notifications invalidate changed indexed files; explicit verification is available if a storage provider does not report external folder changes. Interrupted claims return to Pending.

Notifications now show queue-derived completed, failed, skipped, remaining and progress counts. Rate limits preserve Retry-After and can resume through WorkManager within the configured hours when Background Processing remains enabled. Android may delay or stop background work.

Retry Missing Images, the retry result snapshot and WebView count reporting are covered by unit tests and CI fixtures. No authenticated live Instagram validation or physical-device verification is claimed for 0.5.0 build 11. Node DOM fixture tests passed locally. GitHub Actions passed Android unit tests, lint, app builds and the offline WebView pool/emulator verification. The release workflow requires and verifies the persistent Actions signing key; updates are compatible with installations signed using that same key. No account identifiers or cookies were added to the repository.

The prior published 0.4.0 release and the historical `v0.2.2-working` reference remain unchanged.
