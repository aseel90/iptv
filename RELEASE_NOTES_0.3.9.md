# Selyro TV 0.3.9-stability-speed

- Removes automatic server connection probes during app startup, account changes, and Settings entry. Connection testing now runs only when **TEST CONNECTION** is pressed.
- Shows **Not tested / لم يتم الفحص** for servers that have not been manually tested.
- Simplifies the live quality indicator to compact signal bars during normal viewing, while keeping full quality details in Info.
- Replaces the old `Connecting xx%` bar with a small animated buffering ring. A percentage is shown only when it can be derived from Media3 `totalBufferedDuration` against the active profile buffer target.
- Keeps live channel zap, Audio, Subtitles, Favorites, Info, and 0.3.8 navigation icons unchanged.
- Does not change bitrate policy, decoder selection, stream URL logic, `PlaybackManager`, `PlayerFactory`, `StreamingProfile`, or LoadControl values.
