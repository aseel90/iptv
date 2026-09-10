# Selyro TV

Selyro TV is a TV-first IPTV player for Android TV / Google TV, with special attention to low-power streaming devices such as Xiaomi TV Stick.

> Selyro TV is a player only. It does not provide, host, sell, or bundle TV channels or copyrighted media. Users must connect sources they are authorized to use.

## Current status

**v0.1.0-alpha — foundation**

Implemented in the first project scaffold:

- Native Android project in Kotlin.
- TV-first Compose UI with D-pad focus handling.
- Android TV Leanback launcher configuration.
- Media3 / ExoPlayer playback foundation.
- Hardware-decoder fallback.
- FAST / BALANCED / STABLE buffer profiles.
- Lightweight M3U parser foundation.
- GitHub Actions debug APK build on every push to `main`.

## Technical baseline

- Kotlin 2.3.21
- Android Gradle Plugin 8.13.2
- Compile / target SDK 36
- Min SDK 21
- Compose BOM 2026.08.00
- Compose for TV `tv-material` 1.1.0
- Media3 / ExoPlayer 1.11.0

## Product roadmap

### Phase 1 — Playback core
- Provider onboarding.
- M3U/M3U8 import.
- Xtream-style provider adapter for user-authorized services.
- Live channel browser.
- Real player screen and remote controls.
- Quick channel switching.
- Playback retry / reconnect strategy.
- Buffer profile selector.

### Phase 2 — IPTV essentials
- EPG XMLTV ingestion and local cache.
- Favorites.
- Recent channels.
- Search.
- Movies / series provider catalog.
- Continue watching.
- Audio tracks and subtitles.

### Phase 3 — Professional TV UX
- Profiles.
- Parental PIN.
- Catch-up when supported by the user's provider.
- Multi-playlist management.
- Advanced player diagnostics.
- Automatic network-quality profile selection.
- Low-memory optimization for TV sticks.

### Phase 4 — Release readiness
- TV banner/icon assets.
- Signed release build.
- Privacy policy and store listing.
- Android TV quality checklist.
- Xiaomi TV Stick device testing.

## APK

Open the repository's **Actions** tab and select **Build Android TV APK**. Successful runs expose a `Selyro-TV-debug` artifact containing the installable APK.
