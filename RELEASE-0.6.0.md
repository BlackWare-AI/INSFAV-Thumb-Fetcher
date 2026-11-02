INSFAV Thumb Fetcher 0.6.0 (versionCode 14)

This release corrects the version number previously published as 6.0.0. The Android package now reports **0.6.0** and uses the matching **v0.6.0** tag and APK filename. The higher versionCode and existing signing certificate support an in-place update from build 13 without resetting app data.

Application behavior is unchanged. The existing folder selection flow, thumbnail layout and output naming, Legacy/YTDLnis-style persistent Instagram browser, encrypted cookie snapshots, Netscape conversion, thumbnail retrieval and HD viewer are preserved. Diagnostics now show the corrected version.

Validation gates: existing unit and JavaScript tests, debug/release lint and builds, offline WebView pool and session persistence checks, package/version checks, signing certificate continuity with build 13, and installation/launch after upgrading from the previously published signed APK.

Not verified with a real account or physical device: live Instagram login/2FA, live thumbnail/HD downloads, OEM background behavior and long-running sessions. This version correction makes no new claims about those checks.
