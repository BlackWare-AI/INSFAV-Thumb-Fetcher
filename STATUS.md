# 0.5.0 development status

This branch ports the integrated Fetcher's durable queue/index, queue-backed notifications, rate-limit cooldown scheduling, and thumbnail resolution audit into the standalone helper. It retains the standalone JSON parser, cookie vault, WebView pool, photo viewer, SAF output, and 256 × 256 WebP encoder. Application version is 0.5.0 (versionCode 8).

The output index and account queue are stored in private SQLite. Reimporting an unchanged account list keeps completion status; changed lists preserve rows for accounts still present. A full output folder index happens on first use or through **More → Verify Thumbnail Index**. Subsequent runs resolve saved SAF URIs and verify each output when it is processed. Start Missing rechecks previously completed rows, so removed files become eligible again. Interrupted claims return to Pending.

Notifications now show queue-derived completed, failed, skipped, remaining and progress counts. Rate limits preserve Retry-After and can resume through WorkManager within the configured hours when Background Processing remains enabled. Android may delay or stop background work.

No authenticated live Instagram validation or physical-device verification is claimed for 0.5.0. The current workspace has no Android SDK / cached Gradle 8.13 distribution and cannot reach services.gradle.org, so local build and tests could not start. CI verification is required before release. No account identifiers or cookies were added to the repository.

The prior published 0.4.0 release and the historical `v0.2.2-working` reference remain unchanged.
