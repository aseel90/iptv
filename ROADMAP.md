# Selyro TV Roadmap

> Current stable baseline: 0.3.6 Performance & UX. The immediate priority is validating the new playback/search/EPG/resume work on real Android TV devices, then tightening server-management UX without regressing playback stability.

## Next update — server management polish

- Allow deleting an individual server even when it is the only configured server.
- Show a confirmation dialog before deleting a server.
- Add Edit Server so URL / username / password / display name can be corrected without deleting and re-adding the account.
- Keep Clear All Servers as a separate, clearly destructive action with confirmation.
- Ensure deleting a server also clears only that server's cached state/history where appropriate, without affecting other servers.
- Re-test first-launch and no-server states after the last server is deleted.

## Next update — Favorites discoverability

- Keep the existing Favorite toggle in channel details and the Live player OK menu.
- Add a visible star/favorite action directly on Live channel rows so users can save a channel without starting playback first.
- Make saved state obvious (filled star / Saved label) and allow removing from Favorites from the same control.
- Add a short remote-control hint for the player Favorite action so the feature is discoverable.
- Verify Favorites stays synchronized after server refresh/reload and across app restarts.
- Keep D-pad focus predictable when toggling favorites from channel lists.

## 0.3.x — Stabilization and polish

- Continue real-device testing across Android TV / Google TV / Xiaomi TV Stick layouts.
- Verify D-pad focus, accessibility, Live TV zap behavior, playback/reconnect, resume state, tracks and memory stability.
- Verify the 0.3.6 Live retry/watchdog and offline-state behavior on weak or intermittent streams.
- Verify Continue Watching / resume and unified search across multiple providers.
- Prefer regression fixes over speculative feature growth.

## 0.4 — Library and discovery UX candidates

- Improved recents.
- Sorting and filters.
- Hide categories.
- Additional loading/empty/error state polish.

## 0.5 — Playback reliability

- Retry/backoff refinement.
- Expired URL handling.
- Better playback diagnostics.
- Buffering/profile refinement.
- Preferred audio/subtitle tracks.
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
