from pathlib import Path

p = Path('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt')
s = p.read_text()

old = '''                val activePlayback = playback\n                if (playing != null && activePlayback != null) {\n                    PlayerScreen(player = activePlayback.player) { activePlayback.stop(); playing = null }\n                } else if (account == null) {\n'''
new = '''                val activePlayback = playback\n                val currentRequest = playing\n                if (currentRequest != null && activePlayback != null) {\n                    PlayerScreen(\n                        player = activePlayback.player,\n                        language = language,\n                        onProgress = { positionMs, durationMs ->\n                            vm.savePlaybackProgress(currentRequest.kind, currentRequest.id, positionMs, durationMs)\n                        }\n                    ) {\n                        activePlayback.stop()\n                        playing = null\n                    }\n                } else if (account == null) {\n'''
if old not in s:
    raise SystemExit('PlayerScreen binding pattern not found')
s = s.replace(old, new)

old = '''                        vm.markWatched(request.kind, request.id)\n                        val manager = playback ?: PlaybackManager(context, profile).also { playback = it }\n                        manager.play(request.url, request.title)\n                        playing = request\n'''
new = '''                        vm.markWatched(request.kind, request.id)\n                        val manager = playback ?: PlaybackManager(context, profile).also { playback = it }\n                        manager.play(request.url, request.title, vm.resumePosition(request.kind, request.id))\n                        playing = request\n'''
if old not in s:
    raise SystemExit('PlaybackManager binding pattern not found')
s = s.replace(old, new)

s = s.replace(
    'val all by vm.movies.collectAsState(); val loading by vm.loadingSection.collectAsState(); val mode by vm.displayMode.collectAsState(); var query by remember { mutableStateOf("") };',
    'val all by vm.movies.collectAsState(); val loading by vm.loadingSection.collectAsState(); val mode by vm.displayMode.collectAsState(); val progress by vm.playbackProgress.collectAsState(); var query by remember { mutableStateOf("") };',
    1,
)
s = s.replace(
    'MediaGridCard(movie.name, movie.poster, movie.year, { selected = movie }) { onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) }',
    'MediaGridCard(movie.name, movie.poster, movie.year, progress["movie:${movie.id}"]?.fraction, { selected = movie }) { onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) }',
)
s = s.replace(
    'TvListItem(movie.name, listOfNotNull(movie.year, movie.category).filter { it.isNotBlank() }.joinToString(" • "), { selected = movie }) { onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) }',
    'TvListItem(movie.name, listOfNotNull(movie.year, movie.category).filter { it.isNotBlank() }.joinToString(" • "), progress = progress["movie:${movie.id}"]?.fraction, onFocus = { selected = movie }) { onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) }',
)
s = s.replace(
    'MediaDetails(Modifier.weight(.88f), selected?.name, selected?.poster, selected?.plot, selected?.rating) { selected?.let { movie -> onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) } }',
    'MediaDetails(Modifier.weight(.88f), selected?.name, selected?.poster, selected?.plot, selected?.rating, selected?.let { progress["movie:${it.id}"]?.fraction }) { selected?.let { movie -> onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) } }',
)

s = s.replace(
    'val all by vm.series.collectAsState(); val details by vm.selectedSeriesDetails.collectAsState(); val loading by vm.loadingSection.collectAsState(); val mode by vm.displayMode.collectAsState(); var query',
    'val all by vm.series.collectAsState(); val details by vm.selectedSeriesDetails.collectAsState(); val loading by vm.loadingSection.collectAsState(); val mode by vm.displayMode.collectAsState(); val progress by vm.playbackProgress.collectAsState(); var query',
    1,
)
s = s.replace(
    'TvListItem("S${ep.season} E${ep.episode}  ${ep.title}", ep.duration.orEmpty()) { onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl)) }',
    'TvListItem("S${ep.season} E${ep.episode}  ${ep.title}", ep.duration.orEmpty(), progress = progress["episode:${ep.id}"]?.fraction) { onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl)) }',
)

s = s.replace(
    '@Composable private fun FavoritesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val favorites by vm.favorites.collectAsState();',
    '@Composable private fun FavoritesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val favorites by vm.favorites.collectAsState(); val progress by vm.playbackProgress.collectAsState();',
)
s = s.replace(
    'TvListItem(item.name, tx("Movie", "فيلم") + " • ${item.category}") { onPlay(PlayRequest("movie", item.id, item.name, item.streamUrl)) }',
    'TvListItem(item.name, tx("Movie", "فيلم") + " • ${item.category}", progress = progress["movie:${item.id}"]?.fraction) { onPlay(PlayRequest("movie", item.id, item.name, item.streamUrl)) }',
)

s = s.replace(
    '@Composable private fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val recents by vm.recents.collectAsState();',
    '@Composable private fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val recents by vm.recents.collectAsState(); val progress by vm.playbackProgress.collectAsState();',
)
s = s.replace(
    'TvListItem(item.title, item.kind.replaceFirstChar { c -> c.uppercase() }) { onPlay(item) }',
    'TvListItem(item.title, if (item.kind == "live") tx("Live", "قناة") else tx("Movie", "فيلم"), progress = progress["${item.kind}:${item.id}"]?.fraction) { onPlay(item) }',
)

s = s.replace(
    'private fun MediaGridCard(title: String, image: String?, meta: String?, onFocus: (() -> Unit)? = null, onClick: () -> Unit) {',
    'private fun MediaGridCard(title: String, image: String?, meta: String?, progress: Float? = null, onFocus: (() -> Unit)? = null, onClick: () -> Unit) {',
)
s = s.replace(
    '        if (!meta.isNullOrBlank()) Text(meta, color = Muted, fontSize = 10.sp, maxLines = 1)\n    }\n}\n',
    '        if (!meta.isNullOrBlank()) Text(meta, color = Muted, fontSize = 10.sp, maxLines = 1)\n        if (progress != null && progress > 0f) {\n            Spacer(Modifier.height(6.dp))\n            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .12f))) {\n                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Accent))\n            }\n        }\n    }\n}\n',
    1,
)

s = s.replace(
    'private fun TvListItem(title: String, subtitle: String = "", onFocus: (() -> Unit)? = null, onClick: () -> Unit) {',
    'private fun TvListItem(title: String, subtitle: String = "", progress: Float? = null, onFocus: (() -> Unit)? = null, onClick: () -> Unit) {',
)
s = s.replace(
    '            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1)\n        }\n    }\n}\n',
    '            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1)\n            if (progress != null && progress > 0f) {\n                Spacer(Modifier.height(5.dp))\n                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .10f))) {\n                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Accent))\n                }\n            }\n        }\n        if (progress != null && progress > 0f) {\n            Spacer(Modifier.width(10.dp))\n            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = Accent, fontSize = 10.sp)\n        }\n    }\n}\n',
    1,
)

s = s.replace(
    'private fun MediaDetails(modifier: Modifier, title: String?, image: String?, plot: String?, rating: String?, onPlay: () -> Unit) {',
    'private fun MediaDetails(modifier: Modifier, title: String?, image: String?, plot: String?, rating: String?, progress: Float? = null, onPlay: () -> Unit) {',
)
s = s.replace(
    '        Spacer(Modifier.height(16.dp))\n        TvButton(tx("PLAY", "تشغيل"), true, onClick = onPlay)\n',
    '        if (progress != null && progress > 0f) {\n            Spacer(Modifier.height(12.dp))\n            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .10f))) {\n                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Accent))\n            }\n            Spacer(Modifier.height(6.dp))\n            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}% ${tx("watched", "تمت مشاهدته")}", color = Muted, fontSize = 11.sp)\n        }\n        Spacer(Modifier.height(16.dp))\n        TvButton(if (progress != null && progress > 0f) tx("RESUME", "متابعة") else tx("PLAY", "تشغيل"), true, onClick = onPlay)\n',
)

s = s.replace('TvListItem(channel.name, channel.group, { selected = channel })', 'TvListItem(channel.name, channel.group, onFocus = { selected = channel })')
s = s.replace('MediaGridCard(item.name, item.poster, item.year, { selected = item })', 'MediaGridCard(item.name, item.poster, item.year, onFocus = { selected = item })')
s = s.replace('TvListItem(item.name, item.category, { selected = item })', 'TvListItem(item.name, item.category, onFocus = { selected = item })')

p.write_text(s)
