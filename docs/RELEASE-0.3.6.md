# Selyro TV 0.3.6 — Performance & UX

Target branch: `release/0.3.6-performance-ux`

## Goals

This release is one coordinated stabilization/UX pass on top of 0.3.5. It keeps the single-task/lifecycle protections and focuses on the parts users feel every day on Android TV and low-power TV sticks.

## Changes

- Playback startup watchdog with guarded retry scheduling and stale-zap protection.
- Active playback is fully stopped/cleared before leaving the player/background path.
- Slow/offline stream feedback in the player while retries continue automatically.
- Last Live channel and its group are remembered per provider; Home exposes **Resume Live** and Live opens on the remembered group/channel.
- EPG short-guide cache (10 minutes), larger 8-program fetch, local-device time display, Now/Next layout and current-program progress.
- Unified Search section across Live TV, Movies and Series, with a small persisted search history.
- Continue Watching shortcut on Home using saved VOD progress; normal movie/episode resume remains intact.
- Next Episode card at the end of an episode; OK immediately starts the next ordered episode.
- Existing 0.3.4 Live remote UX remains: Smart Zap, channel drawer, Live hub, audio/subtitles, favorite and info panels.
- Existing 0.3.5 lifecycle/task protections remain: singleTask, stop-on-background, task removal on exit and Android 12+ overlay blocking.

## QA contract

CI must pass unit tests, Android lint and debug assembly. The exported APK is re-signed with the same current Selyro QA key so it remains update-compatible with prior QA builds.
