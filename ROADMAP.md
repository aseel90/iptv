# Selyro TV — Product Roadmap & Engineering Playbook

> **Current stable baseline:** `0.3.8-navigation-icons` (`versionCode 18`)  
> **Package:** `com.selyro.tv`  
> **Primary target:** Android TV / Google TV, with special attention to low-power devices such as Xiaomi TV Stick.  
> **Project rule:** playback stability, remote-control usability, and low memory usage are more important than adding features quickly.

---

## 1. Product goal

Selyro TV is a **TV-first IPTV player**. It does not provide channels or media; users connect sources they are authorized to use.

The product should feel like a native television application rather than a phone app stretched onto a TV. Every feature should therefore be judged against five priorities:

1. **Reliable playback** — changing channels, recovering from weak streams, and leaving the app must never leave hidden/background playback behind.
2. **Remote-first UX** — every screen must be predictable with D-pad, OK, Back, and media keys; focus must always be visible.
3. **Low-resource operation** — avoid loading huge channel/image collections into memory at once; design for TV sticks, not only fast boxes.
4. **Clear diagnostics without clutter** — surface useful state such as buffering or stream quality only when it helps the viewer.
5. **Safe incremental releases** — UI work should not accidentally rewrite the player, account storage, lifecycle handling, or updater.

---

## 2. Current technical baseline

The application is currently built with:

- **Kotlin** and **Java 17** bytecode target.
- **Android Gradle Plugin 8.13.x**.
- **Compile / target SDK 36** and **min SDK 23**.
- **Jetpack Compose** for the UI.
- **Compose for TV (`tv-material`)** for TV-friendly components and focus behavior.
- **Media3 / ExoPlayer 1.11.x** for playback.
- **Coil 3** for channel logos, posters, and remote images.
- **Kotlin Coroutines + StateFlow** for asynchronous loading and UI state.
- **Android Keystore AES-GCM** for saved sensitive provider credentials.
- **GitHub Actions** for build, tests, lint, diagnostics, signed APK export, checksums, and update publication.

The app is configured as a Leanback TV application, landscape-first, with no touchscreen requirement.

---

## 3. Current architecture and the methods used to build Selyro

### 3.1 TV-first Compose UI

The main UI is written in Compose and organized around TV navigation rather than touch gestures.

Current principles:

- Visible focus state on every actionable element.
- Large, readable targets suitable for viewing from a distance.
- D-pad navigation is treated as a core feature, not an accessibility afterthought.
- Arabic and English layouts are supported.
- The permanent main sidebar uses **real vector icons, not emoji**, together with text labels.
- Main navigation remains visible instead of introducing unnecessary collapsing behavior.
- Grid/list layouts use lazy containers so only visible content is composed.

**Best practice going forward:** keep screen composables presentation-focused. Network access, account mutation, and playback orchestration should stay outside UI components.

### 3.2 State management

`AppViewModel` owns application-facing state using `StateFlow` / `MutableStateFlow` and launches asynchronous work in `viewModelScope`.

The preferred flow is:

`UI event -> ViewModel -> provider/storage/player service -> StateFlow update -> Compose renders state`

This prevents individual composables from directly owning network requests or persistent state.

**Next architecture improvement:** split the growing ViewModel by responsibility (accounts, Live TV, library, EPG, playback/session) behind repositories/use-cases while keeping one simple UI-facing state model where practical.

### 3.3 Provider layer

Provider-specific logic lives outside the UI. The application supports user-provided sources through adapters such as:

- Xtream-style APIs for authorized services.
- M3U/M3U8 playlists.

Provider code should return Selyro's own models rather than leaking provider JSON structures into screens.

**Rule:** no provider-specific URL construction, authentication logic, or JSON parsing inside Compose screens.

### 3.4 Account and local state storage

`AccountStore` persists server/account configuration, favorites, recents, playback progress, language, display mode, and playback profile.

Sensitive provider credentials are protected through `SecretStore`, which uses **Android Keystore + AES/GCM**. They must never be committed to the repository, printed in CI logs, crash logs, analytics, screenshots, or update metadata.

For future migrations, persistent data should be versioned so changes to account structure do not silently destroy users' saved state.

### 3.5 Playback architecture

Playback is deliberately isolated from UI code:

- `PlayerFactory` creates Media3/ExoPlayer instances.
- `StreamingProfile` supplies FAST / BALANCED / STABLE buffering profiles.
- `PlaybackManager` owns playback lifecycle, retry logic, startup watchdog, and player replacement when profiles change.
- Decoder fallback is enabled for broader Android TV hardware compatibility.
- HTTP redirects and explicit connection/read timeouts are configured centrally.

Current reliability techniques include:

- Guarded reconnect attempts with increasing delays.
- A startup watchdog for streams that never become ready.
- Generation checks so stale retry callbacks cannot restart a previous channel after a zap.
- Explicit stop/clear/release behavior.

**Critical engineering rule:** changes to UI, favorites, icons, server management, search, or EPG should not modify `PlayerFactory` / `PlaybackManager` unless the release is specifically about playback and has dedicated regression testing.

### 3.6 App lifecycle safety

Selyro contains lifecycle protections created after real-device testing:

- `singleTask` activity behavior to avoid duplicate app instances.
- Playback is stopped when the app leaves the foreground where appropriate.
- Player resources are released when the owning UI/session is disposed.
- Exit removes the task instead of leaving an apparently closed player active.
- Android overlay-hiding protections are enabled on supported Android versions.

These protections are considered **non-regression requirements** for every future release.

### 3.7 Connection-quality diagnostics

Selyro currently distinguishes two useful concepts:

- **Saved-server health/latency** in Settings.
- **Actual Live playback health** in the player based on startup time, buffering events, and playback errors.

The player indicator must remain small and temporary: it should appear during controls, channel changes, buffering, weak/offline state, or detailed Info—not stay permanently over the picture.

**Improvement planned:** server connectivity probing should not compete with Live playback. Prefer manual/Settings-triggered probes rather than automatically probing every saved server during app startup.

### 3.8 In-app update architecture

The source repository and public update distribution are intentionally separated.

- Source repository: `aseel90/iptv`
- Public update metadata: `aseel90/FeatherFury-LaB/selyro-updates/latest.json`

The app downloads update metadata, verifies version/checksum information, downloads the APK, and hands installation to Android using a FileProvider / package installer flow.

This separation allows the source repository to become private later without breaking existing app updates.

---

## 4. Development method — how every update should be built

### Step 1 — start from the last known stable `main`

Never begin a feature from an old release branch. Create a focused branch such as:

`release/0.x.y-short-description`

One release branch should have one clear purpose.

### Step 2 — define non-regression requirements before coding

Before touching code, list what must continue to work. At minimum:

- App launches as a single TV task.
- No playback survives in the background after leaving/exiting.
- Live channel navigation and Smart Zap remain responsive.
- D-pad focus cannot become trapped or invisible.
- Audio/subtitle track controls still work.
- Movies/episodes keep seeking and resume behavior.
- Existing accounts, favorites, and settings survive the upgrade.
- In-app updater can upgrade over the previous stable build.

### Step 3 — make the smallest safe change

Prefer a small patch over a broad rewrite. If a UI change can be implemented only in the UI layer, do not refactor playback at the same time.

Avoid combining these in one release unless necessary:

- player engine changes,
- storage migrations,
- navigation rewrite,
- provider/API rewrite,
- updater/signing changes.

Separating risk makes failures easier to diagnose and rollback.

### Step 4 — use explicit asynchronous boundaries

Network and disk work should run outside the main UI thread using coroutines and suitable dispatchers.

Rules:

- No blocking HTTP in composables.
- Cancel obsolete loads when the user changes server/channel/screen.
- Guard delayed callbacks so an old channel cannot affect the current one.
- Prefer bounded timeouts and clear errors over infinite spinners.
- Do not poll continuously when a user-triggered refresh is enough.

### Step 5 — build for memory-constrained TVs

For large IPTV libraries:

- use `LazyColumn` / `LazyVerticalGrid`;
- do not decode thousands of posters/logos simultaneously;
- keep image requests sized to display needs;
- avoid keeping libraries for multiple servers resident at the same time;
- clear provider-specific large data when switching server;
- avoid duplicate lists created repeatedly during recomposition;
- test long sessions for leaks, not only app startup.

### Step 6 — validate with CI before merge

The minimum automated gate is:

1. Unit tests.
2. Android lint.
3. APK compile/build.
4. Diagnostics/static contract checks.
5. APK export.
6. Signature verification.
7. SHA-256 generation.

A release is not considered ready because it compiles once locally.

### Step 7 — real-TV regression pass

Automated tests cannot verify TV focus feel, HDMI display behavior, remote latency, or weak-device memory pressure.

Before public rollout, test on at least one real Android TV/Google TV device, with Xiaomi TV Stick remaining a priority device.

### Step 8 — merge, publish, then verify the updater

After checks pass:

- merge the PR into `main`;
- export the stable-signed QA APK;
- publish the APK/update metadata to the public update channel;
- verify `latest.json` points to the intended version and checksum;
- test upgrade from the previous stable version without uninstalling.

### Step 9 — clean temporary CI machinery

One-time materialization/recovery workflows should be deleted after use. Long-term CI should converge toward a small set of reusable workflows rather than accumulating one workflow per historical release.

---

## 5. Quality rules and best practices

### Playback

- Never silently downgrade resolution or alter stream URLs to make a quality badge look better.
- Measure playback health; do not confuse Wi-Fi signal strength with IPTV server health.
- Keep retry counts bounded.
- Reset quality metrics when the channel changes.
- Preserve seek position only for seekable VOD/episodes; do not apply VOD assumptions to Live TV.
- Treat long buffering, decoder errors, HTTP failures, and provider failures as different diagnostic categories when possible.

### TV UX

- One D-pad action should have one predictable result.
- Focused item must be obvious from normal TV viewing distance.
- Avoid nested focusable controls unless they add real value.
- Keep overlays temporary and dismissible.
- Do not crowd the video picture with permanent diagnostics.
- Icons should be consistent vector assets with the same visual weight; no emoji in navigation.
- Arabic RTL must be tested on-device, not only previewed in the IDE.

### Network

- Use bounded connect/read timeouts.
- Do not repeatedly probe servers while the viewer is already watching healthy Live TV.
- Cache data that is expensive but reasonably stable (EPG/categories/provider metadata) with explicit invalidation.
- Retry only idempotent/safe operations automatically.
- Surface provider authentication errors separately from general network failures.

### Security and privacy

- Keep credentials encrypted with Android Keystore.
- Never commit playlists, usernames, passwords, tokens, private provider URLs, signing keys, or production secrets.
- Never include credentials in crash reports or updater logs.
- Keep `allowBackup=false` unless a reviewed encrypted backup design is introduced.
- Review the current broad cleartext HTTP allowance before production; IPTV compatibility may require HTTP, but the long-term goal should be a scoped Network Security Config rather than unrestricted cleartext.
- The current public AOSP test signing identity is acceptable only for QA/update continuity, not for a commercial production release.

### Code organization

- Prefer domain models independent of provider payloads.
- Prefer small composables and reusable TV components.
- Move business logic out of `SelyroApp.kt` as it grows.
- Keep provider, storage, player, updater, and UI modules conceptually separate even before a multi-module Gradle migration.
- Add tests for parsing, account migration, favorites/progress persistence, retry policy, and update metadata validation.

---

## 6. Immediate roadmap

## 0.3.9 — Stability & regression hardening

The next release should prioritize verification and cleanup over visual feature growth.

Planned work:

- Move saved-server latency probing to Settings/manual checks (or delay it until needed) so app startup does not create unnecessary network traffic.
- Run a full regression audit of features introduced between 0.3.4 and 0.3.8.
- Verify/restore as needed:
  - unified search behavior;
  - Continue Watching and playback resume;
  - last Live channel/category behavior;
  - Now/Next EPG presentation and progress;
  - Next Episode flow;
  - Live retry/watchdog behavior;
  - audio/subtitle selection;
  - favorites from list, player, and Favorites screen;
  - server edit/delete/clear-all behavior;
  - navigation icons and D-pad focus.
- Improve stream-quality indicator behavior without adding permanent screen clutter.
- Measure long-session memory behavior on TV Stick-class hardware.
- Remove obsolete historical CI/recovery workflows after confirming they are no longer needed.

**Release criterion:** no known regression in playback, lifecycle, updater, accounts, or remote navigation.

---

## 0.4.0 — Architecture cleanup without a rewrite

Goal: make future work safer by reducing the size and responsibility of central UI/ViewModel files.

Planned direction:

- Split screens/components from the large application shell.
- Introduce repository boundaries:
  - `AccountRepository`
  - `LiveRepository`
  - `VodRepository`
  - `EpgRepository`
  - `PlaybackSession` / playback controller
- Keep provider adapters behind interfaces.
- Introduce typed UI state per major screen.
- Introduce navigation/state restoration designed specifically for TV Back behavior.
- Add structured error types instead of passing raw exception messages to UI.
- Add versioned persistent-data migrations.
- Consolidate CI into reusable workflows.

**Constraint:** this is a controlled refactor. It must preserve the existing product behavior and should be delivered in small mergeable steps.

---

## 0.4.x — Library, search, and EPG polish

- Fast unified search across Live / Movies / Series with Arabic and English matching.
- Search history with clear/remove controls.
- Improved category filters and sorting.
- Hide/reorder categories where useful.
- Better empty/loading/error states.
- EPG cache with expiry and provider-aware invalidation.
- Strong Now / Next presentation with local timezone handling.
- Optional full guide only if it remains usable on low-power hardware.

---

## 0.5.0 — Playback reliability program

- More precise retry classification (network vs server vs decoder vs expired URL).
- Refresh/replace expired provider stream URLs where provider APIs support it.
- Improve startup watchdog diagnostics.
- Tune FAST / BALANCED / STABLE profiles using real-device measurements.
- Preferred audio/subtitle language persistence.
- Better VOD resume completion rules.
- Long-running playback soak tests.
- Channel-zap stress tests.
- Validate HLS, DASH, and common direct-stream formats across representative Android TV chipsets.

Do **not** implement automatic quality switching unless the source actually provides alternate renditions and the behavior can be tested safely.

---

## 0.6.0 — Personalization and controls

Candidates, prioritized only after stability:

- Parental PIN and category locking.
- Per-server preferences.
- Playback defaults.
- Optional profiles if real user demand justifies the complexity.
- Backup/restore for non-sensitive preferences.
- Catch-up only when explicitly supported by the connected provider and modeled cleanly.

Credentials should not be exported in plaintext backups.

---

## 0.7.0 — Performance and observability

- Lightweight internal performance counters for startup, zap time, rebuffer count, memory pressure, and errors.
- Local diagnostics screen that users can share without exposing credentials.
- Better crash-report redaction.
- Image cache sizing tuned for TV sticks.
- Benchmark large libraries and category switching.
- ANR and memory-leak review.

Diagnostics must be opt-in/shareable and privacy-safe; do not build invasive analytics just to collect metrics.

---

## 0.8.0 — Release/security hardening

- Replace QA/test signing with a private production signing key before commercial/public production distribution.
- Document signing-key backup and disaster recovery.
- Review all Android permissions.
- Replace broad cleartext allowance with scoped network-security rules where compatible.
- Finalize updater trust/checksum verification strategy.
- Create a dedicated public release channel/repository or object storage endpoint.
- Remove development-only/recovery workflows and artifacts from long-term branches.
- Review ProGuard/R8 behavior on release builds.
- Privacy policy and legal/product disclosures.

---

## 1.0 — Production-ready Selyro TV

1. Feature freeze.
2. Full regression matrix on supported Android TV versions/devices.
3. Upgrade testing from multiple previous stable versions.
4. 8+ hour playback soak sessions across Live and VOD scenarios.
5. D-pad/RTL/accessibility review.
6. Permissions and security audit.
7. Production signing and release-channel verification.
8. Store/listing assets and documentation if store distribution is chosen.
9. Rollback plan documented before rollout.
10. Release only when no blocker exists in playback, lifecycle, update, account persistence, or navigation.

---

## 7. Testing matrix

Every meaningful release should cover these scenarios where applicable:

| Area | Minimum regression check |
|---|---|
| Launch | cold launch, warm launch, return from Home |
| Lifecycle | Home/background/foreground, Back, full exit, no duplicate playback |
| Live | open channel, rapid zap, failed stream, slow stream, recovery |
| Player | audio tracks, subtitles, controls, Info, quality indicator |
| VOD | play, pause, seek, resume, completion |
| Series | seasons/episodes, resume, next episode when supported |
| Accounts | add, edit, switch, delete active, delete last, clear all |
| Favorites | add/remove from list, player, Favorites screen |
| EPG | available, missing, delayed, timezone correctness |
| UI | English/Arabic, list/grid, focus traversal, icons |
| Network | offline start, reconnect, slow server, HTTP redirect |
| Updater | no update, update available, download, install over previous build |
| Memory | large channel/library list, repeated server switching, long session |

---

## 8. Definition of Done for a release

A release is **not done** until all applicable items are true:

- Scope is documented.
- Version code/name are correct and monotonic.
- Unit tests pass.
- Android lint passes.
- APK builds successfully.
- Diagnostic/smoke checks pass.
- No credentials/secrets are introduced.
- Signed APK identity is verified.
- SHA-256 is generated and matches the published APK.
- Upgrade from previous stable build succeeds without data loss.
- Core TV remote flows are tested on a real device.
- Playback/lifecycle non-regression checks pass.
- Public updater metadata points to the exact intended artifact.
- Release notes describe user-visible changes and known limitations.

---

## 9. Git and release policy

Preferred branch model:

- `main` = last stable merged source.
- `release/x.y.z-purpose` = one release candidate / focused scope.
- PR into `main` only after checks pass.

Commit policy:

- Describe the user or engineering outcome, not vague messages such as `update files`.
- Keep generated APK/relay commits clearly separated from source changes where possible.
- Avoid direct emergency edits on `main` unless the change is documentation-only or an urgent validated hotfix.

Versioning:

- `versionCode` must always increase.
- `versionName` should communicate the release purpose during the 0.x phase.
- Hotfixes should be small and should not hide unrelated features.

---

## 10. CI/CD cleanup target

The repository currently contains historical one-off workflows used to recover, materialize, or export individual releases. They are useful evidence of project history, but the long-term build system should become simpler.

Target workflow set:

1. **PR Validation** — unit tests + lint + compile + contract checks.
2. **TV Smoke/Diagnostics** — deterministic project checks and optional emulator/device tests.
3. **Release Export** — signed artifact + APK identity + checksum.
4. **Publish Updater** — push APK/metadata to the public distribution repository.

Reusable workflow inputs should contain version, artifact name, and branch/ref so a new release does not require copying another large YAML file.

---

## 11. What we should deliberately avoid

- Giant rewrites because the current app already has working real-device behavior.
- Mixing a UI-only feature with a player-engine rewrite.
- Permanent diagnostic overlays over video.
- Emoji as production navigation icons.
- Automatic background server checks that compete with playback.
- Keeping multiple servers' full libraries in memory.
- Infinite retries or reconnect loops.
- Provider credentials in logs or source control.
- A production release signed with the public AOSP test key.
- Adding profiles, catch-up, cloud sync, or complex customization before core stability is proven.
- Optimizing based only on emulator behavior; TV Stick-class real hardware remains essential.

---

## 12. Decision rule for future features

Before adding a feature, answer these questions:

1. Does it solve a real viewer problem?
2. Can it be operated cleanly with a TV remote?
3. Does it increase memory/network/player risk?
4. Can it be isolated from playback if it is not a playback feature?
5. Can we test it automatically and on a real TV?
6. Is there a safe migration/rollback path?
7. Does the UI remain calm and uncluttered?

If the value is low and the regression risk is high, postpone it.

---

## 13. North-star engineering principle

**Selyro TV should become more capable without becoming more fragile.**

The best update is not the one with the largest feature list; it is the one that users can install over the previous version, immediately understand with a remote, watch for hours without hidden playback or memory problems, and update again safely later.
