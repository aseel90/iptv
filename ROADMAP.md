# Selyro TV Roadmap

Last updated: 2026-09-11
Current stable version: **0.3.1-polish** (`versionCode 11`)
Package: `com.selyro.tv`

## Product direction

Selyro TV is an Android TV / Smart TV IPTV player focused on fast navigation, reliable playback, clear remote-control UX, and a modern TV-first interface. The roadmap deliberately avoids feature bloat: stability, playback quality, and TV usability come before adding more screens.

## Current stable baseline — 0.3.1

Completed and considered part of the stable baseline:

- Xtream IPTV flows for Live TV, Movies and Series.
- Modern Selyro visual identity, launcher icon and Android TV banner.
- Arabic and English UI with RTL/LTR support.
- Grid/List library modes.
- Series details and playable episode flows.
- Favorites and recent content.
- Multi-server/account support.
- In-app update channel with version and SHA-256 verification.
- TV remote/D-pad optimized focus states.
- Modern player overlay and seek controls.
- Resume playback and visible progress.
- Audio and subtitle track selection.
- Reconnect/retry behavior for playback interruptions.
- Stable-signed upgrade path for the current test line.

## 0.3.x — Stability and polish only

No large new features should be added in this line.

Priority work:

- Test 720p/1080p/4K layouts on common Android TV devices.
- Test Xiaomi TV Stick under low-memory and weak-network conditions.
- Verify focus restoration after dialogs, player exit, search and navigation changes.
- Improve accessibility labels and readable focus contrast.
- Refine playback error messages in Arabic and English.
- Test resume state across app restart and server switching.
- Test subtitle/audio switching on different stream/container formats.
- Reduce unnecessary recompositions and image-memory pressure where measurable.
- Add regression tests for Movies, Series, Live TV and updater flows.

## 0.4 — Library and discovery UX

Only after 0.3.x has been used without major regressions.

Planned candidates:

- Continue Watching row on Home.
- Better Recently Watched management.
- Search across Movies / Series / Live channels.
- Improved sorting and category filters.
- Optional hide/show categories.
- Better empty/loading/error states.
- More polished movie/series detail pages without changing the current Selyro identity.

## 0.5 — Playback reliability

- More granular retry/backoff strategy.
- Better handling of expired/changed stream URLs.
- Stream startup diagnostics for unsupported codecs/containers.
- Improve buffering behavior for medium and weak connections without excessive latency.
- Remember preferred audio/subtitle language when appropriate.
- Optional next-episode flow for Series.
- Validate long playback sessions and memory stability.

## 0.6 — User controls

Candidates, not commitments:

- Parental PIN and category lock.
- Profiles only if real user demand exists.
- Per-server preferences.
- Optional playback defaults.
- Backup/restore of non-sensitive app preferences.

## 1.0 — Production release readiness

Before a commercial/public release:

- Freeze core playback behavior and run a full regression pass.
- Verify Android TV / Google TV launcher requirements and store metadata.
- Prepare Privacy Policy and end-user documentation.
- Review permissions and remove anything not required.
- Run dependency/security audit.
- Move source code to a private repository.
- Separate public update distribution from private source code.
- Use a dedicated production signing key stored securely outside the repository.
- Document signing-key backup and recovery procedure.
- Create repeatable signed release workflow with versionCode checks, SHA-256 validation and changelog generation.

## Repository and update architecture

### Private source repository

`aseel90/iptv` should become **private** once the update channel is moved away from raw files in this repository.

It should contain:

- Android/Kotlin source code.
- CI workflows.
- Internal QA documentation.
- Product roadmap.
- Private release tooling.

### Public update distribution

The app must still be able to download updates without GitHub authentication. Therefore, do **not** depend on a private repository raw URL for APK downloads.

Recommended options:

1. **Dedicated public repository**, e.g. `aseel90/selyro-releases`, containing only:
   - `latest.json`
   - signed APK files
   - SHA-256 files
   - release notes

2. **Cloudflare R2 / custom update domain** for APK and manifest hosting.

No source code, credentials, signing keys, provider data or internal documentation should be published in the public distribution channel.

### Current temporary dependency

The current updater reads its manifest from:

`aseel90/FeatherFury-LaB/selyro-updates/latest.json`

The current manifest points to an APK hosted in `aseel90/iptv`. This must be migrated before making `iptv` private, otherwise existing installs will lose the ability to download updates.

## Signing policy

- Never commit a production keystore/private signing key to GitHub.
- Every normal upgrade must use the same trusted signing identity or a properly planned key-rotation mechanism.
- `versionCode` must always increase.
- Test direct upgrades from older supported versions to the newest release.
- Keep an offline encrypted backup of the production signing key and credentials.

## Release checklist

For every release:

1. Bump `versionCode` and `versionName`.
2. Run compile, unit tests, Android lint and diagnostics.
3. Build the signed APK.
4. Verify package name and version metadata.
5. Verify APK signature.
6. Calculate SHA-256.
7. Test install/update over the previous stable version.
8. Smoke-test Home, Live, Movies, Series, Favorites, Resume, Audio/Subtitles and Settings.
9. Publish APK to the public update distribution channel.
10. Update `latest.json` only after the APK is successfully available.
11. Keep source repository private and release distribution source-free.

## Development rule

If the current stable build works well, prefer fixing real user-reported problems over adding speculative features. New features should only enter the roadmap when they materially improve playback reliability, TV navigation, discovery, safety or maintainability.
