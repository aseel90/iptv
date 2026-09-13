from pathlib import Path

ROOT = Path('.')

def edit(path, fn):
    p = ROOT / path
    before = p.read_text()
    after = fn(before)
    if after != before:
        p.write_text(after)
        print(f'updated {path}')
    else:
        print(f'unchanged {path}')

def replace_once(text, old, new, label):
    if new in text and old not in text:
        return text
    if old not in text:
        raise RuntimeError(f'missing pattern: {label}')
    return text.replace(old, new, 1)

def app_vm(s):
    pairs = [
        ('''    init {\n        if (_account.value != null) loadLive()\n        probeServers()\n    }\n''', '''    init {\n        if (_account.value != null) loadLive()\n    }\n''', 'startup probe'),
        ('''                _account.value = account\n                probeServers()\n                channels.value = loadedChannels\n''', '''                _account.value = account\n                channels.value = loadedChannels\n''', 'login probe'),
        ('''                serverQualities.value = serverQualities.value - accountKey(original)\n                probeServers()\n''', '''                serverQualities.value = serverQualities.value - accountKey(original)\n''', 'update probe'),
        ('''        }\n        probeServers()\n    }\n\n    fun loadLive''', '''        }\n    }\n\n    fun loadLive''', 'remove probe'),
    ]
    for old, new, label in pairs:
        s = replace_once(s, old, new, label)
    return s

def selyro(s):
    s = replace_once(s, '    LaunchedEffect(accounts) { vm.probeServers() }\n\n', '', 'settings auto probe')
    s = replace_once(s, '''                        PlayerScreen(\n                            player = activePlayback.player,\n                            language = language,\n''', '''                        PlayerScreen(\n                            player = activePlayback.player,\n                            language = language,\n                            streamingProfile = profile,\n''', 'streaming profile parameter')
    s = replace_once(s, '''private fun ServerQualityBadge(quality: ServerConnectionQuality?) {\n    val grade = quality?.grade ?: ConnectionGrade.CHECKING\n''', '''private fun ServerQualityBadge(quality: ServerConnectionQuality?) {\n    if (quality == null) {\n        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n            Text("▂▄▆█", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)\n            Text(tx("Not tested", "لم يتم الفحص"), color = Muted, fontSize = 10.sp)\n        }\n        return\n    }\n    val grade = quality.grade\n''', 'not tested state')
    return s

def player_screen(s):
    s = replace_once(s, 'import androidx.compose.animation.fadeOut\n', '''import androidx.compose.animation.fadeOut\nimport androidx.compose.animation.core.LinearEasing\nimport androidx.compose.animation.core.animateFloat\nimport androidx.compose.animation.core.infiniteRepeatable\nimport androidx.compose.animation.core.rememberInfiniteTransition\nimport androidx.compose.animation.core.tween\n''', 'animation imports')
    s = replace_once(s, 'import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.background\n', 'canvas import')
    s = replace_once(s, 'import androidx.compose.ui.graphics.Color\n', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.StrokeCap\nimport androidx.compose.ui.graphics.drawscope.Stroke\n', 'stroke imports')
    s = replace_once(s, 'import com.selyro.tv.model.EpgProgram\n', 'import com.selyro.tv.model.EpgProgram\nimport com.selyro.tv.player.StreamingProfile\n', 'profile import')
    s = replace_once(s, '''fun PlayerScreen(\n    player: Player,\n    language: AppLanguage,\n    liveContext: LivePlayerContext? = null,\n''', '''fun PlayerScreen(\n    player: Player,\n    language: AppLanguage,\n    streamingProfile: StreamingProfile,\n    liveContext: LivePlayerContext? = null,\n''', 'profile argument')
    s = replace_once(s, '''    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }\n    var bufferPercent by remember { mutableIntStateOf(player.bufferedPercentage.coerceIn(0, 100)) }\n''', '''    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }\n    var bufferPercent by remember { mutableStateOf<Int?>(null) }\n''', 'nullable buffer percent')
    calc = '''                bufferPercent = bufferProgressPercent(\n                    player,\n                    streamingProfile,\n                    isStartup = (isLive && liveStartupMs == 0L) || (!isLive && player.currentPosition <= 0L)\n                )\n'''
    s = replace_once(s, '                bufferPercent = player.bufferedPercentage.coerceIn(0, 100)\n', calc, 'listener buffer percent')
    calc2 = '''            bufferPercent = bufferProgressPercent(\n                player,\n                streamingProfile,\n                isStartup = (isLive && liveStartupMs == 0L) || (!isLive && player.currentPosition <= 0L)\n            )\n'''
    s = replace_once(s, '            bufferPercent = player.bufferedPercentage.coerceIn(0, 100)\n', calc2, 'poll buffer percent')
    s = replace_once(s, '''        if (buffering) {\n            Column(\n                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 24.dp, vertical = 16.dp),\n                horizontalAlignment = Alignment.CenterHorizontally\n            ) {\n                Text("${pt(language, "Connecting", "جاري الاتصال")} $bufferPercent%", color = Color.White, fontSize = 14.sp)\n                Spacer(Modifier.height(9.dp))\n                Box(Modifier.width(180.dp).height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.15f))) {\n                    Box(Modifier.fillMaxHeight().fillMaxWidth((bufferPercent / 100f).coerceIn(0.04f, 1f)).background(PlayerAccent))\n                }\n            }\n        }\n''', '''        if (buffering) {\n            BufferingIndicator(\n                modifier = Modifier.align(Alignment.Center),\n                percent = bufferPercent\n            )\n        }\n''', 'buffering UI')
    s = replace_once(s, '''    val color = when (grade) {\n        ConnectionGrade.EXCELLENT -> Color(0xFF62E6A7)\n        ConnectionGrade.GOOD -> Color(0xFFFFD166)\n        ConnectionGrade.WEAK -> Color(0xFFFF9F43)\n        ConnectionGrade.OFFLINE -> Color(0xFFFF6B6B)\n        ConnectionGrade.CHECKING -> PlayerMuted\n    }\n    val label = when (grade) {\n''', '''    val color = when (grade) {\n        ConnectionGrade.EXCELLENT -> Color(0xFF62E6A7)\n        ConnectionGrade.GOOD -> Color(0xFFFFD166)\n        ConnectionGrade.WEAK -> Color(0xFFFF9F43)\n        ConnectionGrade.OFFLINE -> Color(0xFFFF6B6B)\n        ConnectionGrade.CHECKING -> PlayerMuted\n    }\n    if (!detailed) {\n        Box(\n            modifier.clip(RoundedCornerShape(10.dp))\n                .background(Color.Black.copy(alpha = .58f))\n                .padding(horizontal = 7.dp, vertical = 4.dp),\n            contentAlignment = Alignment.Center\n        ) {\n            Text("▂▄▆█", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)\n        }\n        return\n    }\n    val label = when (grade) {\n''', 'compact quality indicator')
    helpers = '''@Composable\nprivate fun BufferingIndicator(modifier: Modifier, percent: Int?) {\n    Box(\n        modifier.size(66.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.72f)),\n        contentAlignment = Alignment.Center\n    ) {\n        if (percent == null) {\n            IndeterminateBufferRing()\n        } else {\n            Canvas(Modifier.size(46.dp)) {\n                val strokeWidth = 4.dp.toPx()\n                drawCircle(\n                    color = Color.White.copy(alpha = 0.14f),\n                    style = Stroke(width = strokeWidth)\n                )\n                drawArc(\n                    color = PlayerAccent,\n                    startAngle = -90f,\n                    sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),\n                    useCenter = false,\n                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)\n                )\n            }\n            Text("$percent%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)\n        }\n    }\n}\n\n@Composable\nprivate fun IndeterminateBufferRing() {\n    val transition = rememberInfiniteTransition(label = "buffer-spinner")\n    val rotation by transition.animateFloat(\n        initialValue = 0f,\n        targetValue = 360f,\n        animationSpec = infiniteRepeatable(tween(durationMillis = 850, easing = LinearEasing)),\n        label = "buffer-rotation"\n    )\n    Canvas(Modifier.size(46.dp)) {\n        val strokeWidth = 4.dp.toPx()\n        drawCircle(\n            color = Color.White.copy(alpha = 0.14f),\n            style = Stroke(width = strokeWidth)\n        )\n        drawArc(\n            color = PlayerAccent,\n            startAngle = rotation - 90f,\n            sweepAngle = 105f,\n            useCenter = false,\n            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)\n        )\n    }\n}\n\nprivate fun bufferProgressPercent(\n    player: Player,\n    profile: StreamingProfile,\n    isStartup: Boolean\n): Int? {\n    if (player.playbackState != Player.STATE_BUFFERING) return null\n    val bufferedMs = player.totalBufferedDuration\n    if (bufferedMs <= 0L) return null\n    val targetMs = if (isStartup) profile.playbackBufferMs else profile.rebufferMs\n    if (targetMs <= 0) return null\n    return ((bufferedMs * 100L) / targetMs.toLong()).toInt().coerceIn(1, 100)\n}\n\n'''
    marker = '@Composable\nprivate fun LiveInfoPanel(\n'
    if helpers not in s:
        if marker not in s:
            raise RuntimeError('missing helper insertion point')
        s = s.replace(marker, helpers + marker, 1)
    return s

def playback_manager(s):
    s = replace_once(s, '''    private var startupWatchdog: Runnable? = null\n    private var retryRunnable: Runnable? = null\n''', '''    private var startupWatchdog: Runnable? = null\n    private var rebufferWatchdog: Runnable? = null\n    private var retryRunnable: Runnable? = null\n''', 'rebuffer field')
    s = replace_once(s, '''        override fun onPlaybackStateChanged(playbackState: Int) {\n            if (playbackState == Player.STATE_READY) {\n                retryCount = 0\n                cancelStartupWatchdog()\n                cancelRetry()\n            } else if (playbackState == Player.STATE_IDLE && playerInternal.currentMediaItem != null) {\n                scheduleReconnect(playerInternal.currentMediaItem?.mediaId.orEmpty(), playbackGeneration)\n            }\n        }\n''', '''        override fun onPlaybackStateChanged(playbackState: Int) {\n            when (playbackState) {\n                Player.STATE_READY -> {\n                    retryCount = 0\n                    cancelStartupWatchdog()\n                    cancelRebufferWatchdog()\n                    cancelRetry()\n                }\n                Player.STATE_BUFFERING -> {\n                    scheduleRebufferWatchdog(\n                        playerInternal.currentMediaItem?.mediaId.orEmpty(),\n                        playbackGeneration\n                    )\n                }\n                Player.STATE_IDLE -> {\n                    cancelRebufferWatchdog()\n                    if (playerInternal.currentMediaItem != null) {\n                        scheduleReconnect(playerInternal.currentMediaItem?.mediaId.orEmpty(), playbackGeneration)\n                    }\n                }\n                Player.STATE_ENDED -> cancelRebufferWatchdog()\n            }\n        }\n''', 'playback state recovery')
    s = s.replace('''        mainHandler.removeCallbacksAndMessages(null)\n        startupWatchdog = null\n        retryRunnable = null\n''', '''        mainHandler.removeCallbacksAndMessages(null)\n        startupWatchdog = null\n        rebufferWatchdog = null\n        retryRunnable = null\n''')
    s = replace_once(s, '''        playbackGeneration += 1\n        cancelStartupWatchdog()\n        val wasPlaying = playerInternal.playWhenReady\n''', '''        playbackGeneration += 1\n        cancelStartupWatchdog()\n        cancelRebufferWatchdog()\n        cancelRetry()\n        val wasPlaying = playerInternal.playWhenReady\n''', 'profile cleanup')
    marker = '''    private fun cancelStartupWatchdog() {\n        startupWatchdog?.let(mainHandler::removeCallbacks)\n        startupWatchdog = null\n    }\n\n'''
    extra = '''    private fun cancelStartupWatchdog() {\n        startupWatchdog?.let(mainHandler::removeCallbacks)\n        startupWatchdog = null\n    }\n\n    private fun scheduleRebufferWatchdog(mediaId: String, generation: Long) {\n        if (mediaId.isBlank() || generation != playbackGeneration) return\n        if (startupWatchdog != null || retryRunnable != null || rebufferWatchdog != null) return\n        val timeoutMs = maxOf(8_000L, currentProfile.rebufferMs.toLong() * 3L)\n        val runnable = Runnable {\n            rebufferWatchdog = null\n            if (generation != playbackGeneration) return@Runnable\n            if (playerInternal.currentMediaItem?.mediaId != mediaId) return@Runnable\n            if (playerInternal.playbackState == Player.STATE_BUFFERING && playerInternal.playWhenReady) {\n                scheduleReconnect(mediaId, generation)\n            }\n        }\n        rebufferWatchdog = runnable\n        mainHandler.postDelayed(runnable, timeoutMs)\n    }\n\n    private fun cancelRebufferWatchdog() {\n        rebufferWatchdog?.let(mainHandler::removeCallbacks)\n        rebufferWatchdog = null\n    }\n\n'''
    if 'private fun scheduleRebufferWatchdog' not in s:
        if marker not in s:
            raise RuntimeError('missing watchdog insertion point')
        s = s.replace(marker, extra, 1)
    s = replace_once(s, '''        cancelStartupWatchdog()\n        retryCount += 1\n''', '''        cancelStartupWatchdog()\n        cancelRebufferWatchdog()\n        retryCount += 1\n''', 'retry cancels rebuffer')
    return s

def gradle(s):
    s = replace_once(s, 'versionCode = 18', 'versionCode = 19', 'versionCode')
    s = replace_once(s, 'versionName = "0.3.8-navigation-icons"', 'versionName = "0.3.9-stability-speed"', 'versionName')
    return s

edit('app/src/main/java/com/selyro/tv/ui/AppViewModel.kt', app_vm)
edit('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt', selyro)
edit('app/src/main/java/com/selyro/tv/ui/PlayerScreen.kt', player_screen)
edit('app/src/main/java/com/selyro/tv/player/PlaybackManager.kt', playback_manager)
edit('app/build.gradle.kts', gradle)

release = '''# Selyro TV 0.3.9 — Stability & Speed\n- Removes automatic server connection probes during app startup, login, account update/removal, and Settings entry; server testing now runs only from TEST CONNECTION.\n- Untested servers show Not tested / لم يتم الفحص instead of staying on Checking.\n- Keeps one compact live quality indicator in the top-right; the normal view is signal bars only and full details remain in Info.\n- Replaces the Connecting percentage bar with a small circular buffering indicator. A percentage is shown only when Media3 buffered-duration data can be compared with the active streaming profile target.\n- Adds recovery for prolonged post-startup buffering stalls by re-preparing the same media item through the existing retry path after an abnormal buffering timeout.\n- No new network probe or speed test is added to Player.\n- Playback profiles remain FAST(5000,15000,650,1200), BALANCED(10000,30000,1000,2000), STABLE(20000,50000,2000,4000).\n- PlayerFactory, decoder path, bitrate behavior, stream URL logic, LoadControl values, live zap behavior, Audio/Subtitles/Favorites/Info, and 0.3.8 navigation icons are preserved.\n- Release: versionCode 19 / versionName 0.3.9-stability-speed.\n'''
Path('docs/RELEASE-0.3.9.md').write_text(release)
