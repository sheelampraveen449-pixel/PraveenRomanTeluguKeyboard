# NEW GBOARD

A separate standalone Android keyboard app based on the complete open-source AOSP LatinIME source.

- Upstream source: https://github.com/LineageOS/android_packages_inputmethods_LatinIME
- Upstream branch: `lineage-24.0`
- Upstream commit pinned by the Git submodule: `270a585939a3b5e817fcaeefc383c6b2a550234d`
- Android application ID: `com.praveen.newgboard`
- Display name: `NEW GBOARD`

This project is intentionally isolated from the Roman Telugu keyboard. No Roman Telugu code or features are included.

## Build

The CI workflow checks out the complete upstream source recursively, applies only the standalone package-name and app-label adaptation, builds the debug APK, runs Gradle unit tests, and installs/enables the IME in an Android emulator.

## Important source note

This is AOSP LatinIME, not Google's proprietary Gboard source. The AOSP source is licensed under Apache-2.0; see the upstream repository's NOTICE and license files. Gboard-specific appearance and behavior can be recreated only where technically and legally possible, without claiming this is Google's Gboard.
