# Selyro TV product specification

Selyro TV is a player for user-provided/licensed IPTV sources. It does not ship channels.

## Complete product scope
- Android TV / Google TV, optimized for low-memory TV sticks.
- Xtream-compatible login and M3U parsing.
- Live channels, VOD movies, series data foundation.
- EPG XMLTV parser.
- Favorites and recent history persisted locally.
- Search/filter-ready domain models.
- Media3 playback with Fast, Balanced and Stable buffering profiles.
- Hardware decoding through Media3 platform decoders with normal fallback behavior.
- D-pad/remote-first UI contract.
- Network bandwidth observation helper.
- CI APK build workflow.

## Performance principles
Do not preload artwork or full EPG into composition. Keep network work off main thread. Use bounded local state, lazy TV lists, Media3 buffering and server-side lower bitrate variants where available. No player can guarantee uninterrupted playback when stream bitrate exceeds sustainable network throughput.

## Release gates
Build, lint, install on Android TV emulator/device, login against authorized test service, live zapping test, long-play soak test, VOD seek test, remote navigation pass, low-bandwidth test, memory test, signed release build.
