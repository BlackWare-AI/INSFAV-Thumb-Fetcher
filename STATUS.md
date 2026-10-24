# 0.5.0 build 10 development status

This build adds a durable one-time Retry Timeouts action for completed `WEBVIEW_TIMEOUT` rows. It excludes successful, pending and `PROFILE_PAGE_UNAVAILABLE` rows, survives process restart, preserves the actual second-attempt error, shows Instagram links for unresolved retry results, and can export `Timeout-Failed.txt`. Application version is 0.5.0 (versionCode 10).

The output index and account queue are stored in private SQLite. Reimporting an unchanged account list keeps completion status; changed lists preserve rows for accounts still present. A full output folder index happens on first use or through **More → Verify Thumbnail Index**. Start Missing only re-enables unfinished accounts and does not reopen completed output files on each run. SAF change notifications invalidate changed indexed files; explicit verification is available if a storage provider does not report external folder changes. Interrupted claims return to Pending.

Notifications now show queue-derived completed, failed, skipped, remaining and progress counts. Rate limits preserve Retry-After and can resume through WorkManager within the configured hours when Background Processing remains enabled. Android may delay or stop background work.

No authenticated live Instagram validation or physical-device verification is claimed for 0.5.0 build 10. Node DOM fixture tests passed locally. GitHub Actions passed Android unit tests, lint, app builds and the offline WebView pool/emulator verification. The build 10 APK is signed with the CI debug key; it may require uninstalling an older build before installation and is not guaranteed to support future in-place updates. No account identifiers or cookies were added to the repository.

The prior published 0.4.0 release and the historical `v0.2.2-working` reference remain unchanged.
