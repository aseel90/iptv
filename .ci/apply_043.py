from pathlib import Path


def replace(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))

replace(
    "app/src/main/java/com/selyro/tv/ui/PlayerScreen.kt",
    """    val currentLiveChannel = liveChannels.getOrNull(currentLiveIndex)
    val streamGrade = when {""",
    """    val currentLiveChannel = liveChannels.getOrNull(currentLiveIndex)
    // Keep player navigation physically consistent: remote Left always moves left and
    // remote Right always moves right, regardless of the app's RTL/LTR language.
    val channelDrawerAlignment = if (language == AppLanguage.ARABIC) Alignment.CenterEnd else Alignment.CenterStart
    val liveInfoAlignment = if (language == AppLanguage.ARABIC) Alignment.CenterStart else Alignment.CenterEnd
    val streamGrade = when {"""
)

replace(
    "app/src/main/java/com/selyro/tv/ui/PlayerScreen.kt",
    """            LiveChannelDrawer(Modifier.align(Alignment.CenterStart), language, liveChannels, drawerCursor, liveContext?.currentChannelId)""",
    """            LiveChannelDrawer(Modifier.align(channelDrawerAlignment), language, liveChannels, drawerCursor, liveContext?.currentChannelId)"""
)

replace(
    "app/src/main/java/com/selyro/tv/ui/PlayerScreen.kt",
    """                Modifier.align(Alignment.CenterEnd), language, currentLiveChannel, liveContext?.epg.orEmpty(),""",
    """                Modifier.align(liveInfoAlignment), language, currentLiveChannel, liveContext?.epg.orEmpty(),"""
)

replace(
    "app/src/main/java/com/selyro/tv/ui/PlayerScreen.kt",
    """    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PlayerPanelStrong)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val enabled = when (index) { 1 -> hasAudio; 2 -> hasSubtitles; else -> true }
            val focused = index == cursor
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (focused) PlayerAccent else Color.White.copy(alpha = if (enabled) 0.08f else 0.035f))
                    .padding(horizontal = 10.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (focused) Color.Black else if (enabled) Color.White else PlayerMuted.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }""",
    """    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PlayerPanelStrong)
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val enabled = when (index) { 1 -> hasAudio; 2 -> hasSubtitles; else -> true }
                val focused = index == cursor
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (focused) PlayerAccent else Color.White.copy(alpha = if (enabled) 0.08f else 0.035f))
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (focused) Color.Black else if (enabled) Color.White else PlayerMuted.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }"""
)

replace(
    "app/src/main/java/com/selyro/tv/ui/PlayerScreen.kt",
    """        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackTabChip(pt(language, "Audio", "الصوت"), tab == TrackTab.AUDIO)
            TrackTabChip(pt(language, "Subtitles", "الترجمة"), tab == TrackTab.SUBTITLES)
            Spacer(Modifier.weight(1f))
            Text(pt(language, "← → switch   ↑ ↓ choose   OK apply", "← → تبديل   ↑ ↓ اختيار   OK تطبيق"), color = PlayerMuted, fontSize = 11.sp)
        }""",
    """        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrackTabChip(pt(language, "Audio", "الصوت"), tab == TrackTab.AUDIO)
                TrackTabChip(pt(language, "Subtitles", "الترجمة"), tab == TrackTab.SUBTITLES)
                Spacer(Modifier.weight(1f))
                Text(pt(language, "← → switch   ↑ ↓ choose   OK apply", "← → تبديل   ↑ ↓ اختيار   OK تطبيق"), color = PlayerMuted, fontSize = 11.sp)
            }
        }"""
)

replace(
    "app/build.gradle.kts",
    'versionCode = 22',
    'versionCode = 23'
)
replace(
    "app/build.gradle.kts",
    'versionName = "0.4.2-server-recent-hotfix"',
    'versionName = "0.4.3-remote-navigation-hotfix"'
)
