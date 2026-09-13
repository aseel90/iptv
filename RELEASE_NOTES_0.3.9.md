# Selyro TV 0.3.9-stability-speed

- Removes automatic server connection probes during app startup, login/account changes, removal, and Settings entry. Connection testing now runs only when **TEST CONNECTION** is pressed.
- Shows **Not tested / لم يتم الفحص** for servers that have not been manually tested.
- Simplifies the live quality indicator to compact signal bars during normal viewing, while keeping full quality details in Info.
- Replaces the old `Connecting xx%` horizontal bar with a small circular buffering indicator. A percentage is shown only when it can be derived from Media3 `totalBufferedDuration` against the active streaming-profile target; otherwise the ring is shown without an invented percentage.
- Fixes a confirmed recovery gap where a stream that had already reached `STATE_READY` could later remain stuck in `STATE_BUFFERING`. `PlaybackManager` now starts a lightweight rebuffer watchdog and reuses the existing reconnect/prepare path only after prolonged buffering.
- Adds no speed test, server probe, or extra network request inside Player.
- Keeps live channel zap, Audio, Subtitles, Favorites, Info, and the 0.3.8 navigation icons unchanged.
- Keeps `PlayerFactory`, bitrate policy, decoder path, stream URL logic, LoadControl values, and `StreamingProfile` values unchanged: FAST(5000,15000,650,1200), BALANCED(10000,30000,1000,2000), STABLE(20000,50000,2000,4000).
- Release: versionCode 19 / versionName `0.3.9-stability-speed`.
