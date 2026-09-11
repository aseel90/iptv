# Selyro TV Roadmap

> Current stable baseline: 0.3.4 Live TV UX. The immediate stabilization priority is lifecycle/task correctness: one app activity instance, no background playback after leaving Selyro, deterministic exit behavior, and overlay protection where Android supports it.

## 0.3.x — Stabilization and polish

- Fix lifecycle/task behavior so launcher relaunches do not stack duplicate MainActivity instances.
- Stop active playback when Selyro is no longer visible.
- Ensure Exit removes the app task instead of revealing an older hidden instance.
- Block third-party application overlays on Android 12+ using the platform HIDE_OVERLAY_WINDOWS protection.
- Continue real-device testing across Android TV / Google TV / Xiaomi TV Stick layouts.
- Verify D-pad focus, accessibility, Live TV zap behavior, playback/reconnect, resume state, tracks and memory stability.
- Prefer regression fixes over speculative feature growth.

## 0.4 — Library and discovery UX candidates

- Continue Watching.
- Improved recents.
- Cross-content search.
- Sorting and filters.
- Hide categories.
- Additional loading/empty/error state polish.

## 0.5 — Playback reliability

- Retry/backoff refinement.
- Expired URL handling.
- Better playback diagnostics.
- Buffering/profile refinement.
- Preferred audio/subtitle tracks.
- Optional next-episode flow.
- Long-session stability testing.

## 0.6 — User controls candidates

- Parental PIN.
- Profiles only if real user demand justifies them.
- Per-server preferences.
- Playback defaults.
- Backup/restore of non-sensitive preferences.

## 1.0 — Production readiness

- Feature freeze and full regression pass.
- Android TV requirements review.
- Privacy policy and release documentation.
- Permissions/security audit.
- Private source repository.
- Separate public update distribution.
- Private production signing key with a documented backup/recovery plan.
- Repeatable release workflow.

## Source and release architecture

- `aseel90/iptv` is the private-source target.
- The public update path is separated from source so the app can update without exposing the source repository.
- Current public update channel: `aseel90/FeatherFury-LaB/selyro-updates/latest.json`.
- Long-term preferred distribution: a dedicated public `selyro-releases` repository or Cloudflare R2/custom update domain.
- Public distribution must not contain source code, credentials, signing keys, provider data or internal documentation.

## Signing policy

The current QA/stable builds use the AOSP platform test certificate so test installations can update in place. This identity is public and is not suitable for production/commercial distribution. Before production release, migrate to a private production signing key and document the migration path for testers.

## Release checklist

1. Build succeeds.
2. Unit tests pass.
3. Android lint passes.
4. Diagnostic/smoke checks pass.
5. Signed APK is exported and checksum verified.
6. Update metadata points to the correct public APK and checksum.
7. Upgrade from the previous stable version is tested.
8. Core remote-control flows are tested on a real TV device.

## Development rule

Fix real user-reported problems first. Keep changes small enough to test, preserve the current Selyro visual identity, and avoid feature growth that risks playback stability.
