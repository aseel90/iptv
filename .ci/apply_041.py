from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}")
    p.write_text(text.replace(old, new, 1))


replace(
    "app/src/main/java/com/selyro/tv/data/AccountStore.kt",
    '''        val root = runCatching { JSONObject(prefs.getString(prefKey, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val completed = safeDuration > 0L && (safePosition >= safeDuration - 60_000L || safePosition.toDouble() / safeDuration >= 0.95)
        if (safeDuration <= 0L || safePosition < 10_000L || completed) {
            root.remove(key)
        } else {
''',
    '''        // A player can briefly report TIME_UNSET/0 after stop or during teardown. Never let
        // that transient state erase a valid resume point that was already persisted.
        if (safeDuration <= 0L) return

        val root = runCatching { JSONObject(prefs.getString(prefKey, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val completed = safePosition >= safeDuration - 60_000L || safePosition.toDouble() / safeDuration >= 0.95
        if (safePosition < 10_000L || completed) {
            root.remove(key)
        } else {
''',
)

replace(
    "app/src/main/java/com/selyro/tv/ui/PlayerScreen.kt",
    '''    fun persistProgress(force: Boolean = false) {
        if (isLive) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPersistAt < 5_000L) return
        lastPersistAt = now
        onProgress(max(0L, player.currentPosition), normalizedDuration(player))
    }
''',
    '''    fun persistProgress(force: Boolean = false) {
        if (isLive) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPersistAt < 5_000L) return
        val playerDuration = normalizedDuration(player)
        val snapshotDuration = if (playerDuration > 0L) playerDuration else duration
        if (snapshotDuration <= 0L) return
        val snapshotPosition = if (playerDuration > 0L) max(0L, player.currentPosition) else position.coerceAtLeast(0L)
        lastPersistAt = now
        onProgress(snapshotPosition.coerceAtMost(snapshotDuration), snapshotDuration)
    }
''',
)

app = Path("app/src/main/java/com/selyro/tv/ui/SelyroApp.kt")
s = app.read_text()
s = s.replace(
    "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.focus.onFocusChanged\n",
    "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.focus.FocusRequester\nimport androidx.compose.ui.focus.focusRequester\nimport androidx.compose.ui.focus.onFocusChanged\n",
    1,
)
s = s.replace(
    '''    var playback by remember { mutableStateOf<PlaybackManager?>(null) }
    var playing by remember { mutableStateOf<PlayRequest?>(null) }
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    val scope = rememberCoroutineScope()
''',
    '''    var playback by remember { mutableStateOf<PlaybackManager?>(null) }
    var playing by remember { mutableStateOf<PlayRequest?>(null) }
    var pendingResume by remember { mutableStateOf<PlayRequest?>(null) }
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    val scope = rememberCoroutineScope()
    val latestPlayback by rememberUpdatedState(playback)
    val latestPlaying by rememberUpdatedState(playing)
''',
    1,
)
s = s.replace(
    '''    LaunchedEffect(Unit) { checkUpdates() }
''',
    '''    fun startPlayback(request: PlayRequest, resume: Boolean) {
        vm.markWatched(request.kind, request.id)
        val manager = playback ?: PlaybackManager(context, profile).also { playback = it }
        val startPosition = if (resume) vm.resumePosition(request.kind, request.id) else 0L
        if (!resume && request.kind != "live") vm.clearPlaybackProgress(request.kind, request.id)
        manager.play(request.url, request.title, startPosition)
        playing = request
        pendingResume = null
    }

    LaunchedEffect(Unit) { checkUpdates() }
''',
    1,
)
s = s.replace(
    '''            if (event == Lifecycle.Event.ON_STOP) {
                playback?.stop()
                playing = null
            }
''',
    '''            if (event == Lifecycle.Event.ON_STOP) {
                val request = latestPlaying
                val manager = latestPlayback
                if (request != null && request.kind != "live" && manager != null) {
                    val durationMs = manager.player.duration.takeIf { it > 0L } ?: 0L
                    if (durationMs > 0L) {
                        vm.savePlaybackProgress(request.kind, request.id, manager.player.currentPosition.coerceAtLeast(0L), durationMs)
                    }
                }
                manager?.stop()
                playing = null
            }
''',
    1,
)
s = s.replace(
    '''                    ) { request ->
                        vm.markWatched(request.kind, request.id)
                        val manager = playback ?: PlaybackManager(context, profile).also { playback = it }
                        manager.play(request.url, request.title, vm.resumePosition(request.kind, request.id))
                        playing = request
                    }
''',
    '''                    ) { request ->
                        val saved = if (request.kind == "live") null else vm.progressFor(request.kind, request.id)
                        if (saved != null && saved.positionMs >= 10_000L && saved.fraction < .95f) {
                            pendingResume = request
                        } else {
                            startPlayback(request, resume = false)
                        }
                    }
''',
    1,
)
s = s.replace(
    '''                        ) {
                            activePlayback.stop()
                            playing = null
                        }
                    }
                }
                UpdateOverlay''',
    '''                        ) {
                            activePlayback.stop()
                            playing = null
                        }
                    }

                    val resumeRequest = pendingResume
                    if (resumeRequest != null) {
                        vm.progressFor(resumeRequest.kind, resumeRequest.id)?.let { saved ->
                            ResumePlaybackDialog(
                                title = resumeRequest.title,
                                positionMs = saved.positionMs,
                                durationMs = saved.durationMs,
                                onDismiss = { pendingResume = null },
                                onResume = { startPlayback(resumeRequest, resume = true) },
                                onRestart = { startPlayback(resumeRequest, resume = false) }
                            )
                        }
                    }
                }
                UpdateOverlay''',
    1,
)
s = s.replace('TvInput(tx("Search channels", "بحث في القنوات"), query) { query = it }', 'TvSearchInput(tx("Search channels", "بحث في القنوات"), query) { query = it }')
s = s.replace('TvInput(tx("Search", "بحث") + " ${if (category == "All") tx("movies", "في الأفلام") else category}", query) { query = it }', 'TvSearchInput(tx("Search", "بحث") + " ${if (category == "All") tx("movies", "في الأفلام") else category}", query) { query = it }')
s = s.replace('TvInput(tx("Search series", "بحث في المسلسلات"), query) { query = it }', 'TvSearchInput(tx("Search series", "بحث في المسلسلات"), query) { query = it }')

marker = '''@Composable
private fun TvInput(label: String, value: String, password: Boolean = false, onValueChange: (String) -> Unit) {
'''
if marker not in s:
    raise SystemExit("TvInput marker not found")
insert = r'''@Composable
private fun ResumePlaybackDialog(
    title: String,
    positionMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 430.dp, max = 620.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF0C151D))
                .border(1.dp, Color(0xFF28423F), RoundedCornerShape(22.dp)).padding(24.dp)
        ) {
            Text(tx("Continue watching?", "متابعة المشاهدة؟"), color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(12.dp))
            Text(
                tx("You stopped at", "توقفت عند") + " ${formatResumeTime(positionMs)}  •  ${(fraction * 100).toInt()}%",
                color = Muted, fontSize = 13.sp
            )
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .10f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Accent))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(tx("CONTINUE", "متابعة") + "  ${formatResumeTime(positionMs)}", true, modifier = Modifier.weight(1f), onClick = onResume)
                TvButton(tx("START OVER", "من البداية"), modifier = Modifier.weight(.72f), onClick = onRestart)
            }
        }
    }
}

@Composable
private fun TvSearchInput(label: String, value: String, onValueChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(47.dp).clip(RoundedCornerShape(12.dp))
            .background(if (focused) Focus else Panel)
            .border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF263746), RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable { editing = true }
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⌕", color = if (focused) Accent else Muted, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (value.isBlank()) label else value,
            color = if (value.isBlank()) Muted else Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(tx("OK to search", "اضغط للبحث"), color = if (focused) Accent else Color(0xFF687988), fontSize = 10.sp)
    }
    if (editing) {
        TvSearchDialog(label, value, onValueChange) { editing = false }
    }
}

@Composable
private fun TvSearchDialog(label: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 500.dp, max = 760.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF0C151D))
                .border(1.dp, Color(0xFF28423F), RoundedCornerShape(22.dp)).padding(24.dp)
        ) {
            Text(label, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(13.dp)).background(Panel)
                    .border(if (fieldFocused) 2.dp else 1.dp, if (fieldFocused) Accent else Color(0xFF263746), RoundedCornerShape(13.dp))
                    .focusRequester(focusRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(tx("DONE", "تم"), true) { onDismiss() }
                if (value.isNotBlank()) TvButton(tx("CLEAR", "مسح")) { onValueChange("") }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }
}

private fun formatResumeTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun TvInput(label: String, value: String, password: Boolean = false, onValueChange: (String) -> Unit) {
'''
s = s.replace(marker, insert, 1)
app.write_text(s)

gradle = Path("app/build.gradle.kts")
g = gradle.read_text()
g = g.replace('versionCode = 20', 'versionCode = 21', 1)
g = g.replace('versionName = "0.4.0-account-foundation"', 'versionName = "0.4.1-resume-search-ux"', 1)
gradle.write_text(g)
