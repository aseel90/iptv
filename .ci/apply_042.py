from pathlib import Path


def replace(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f'Expected block not found in {path}: {old[:120]!r}')
    p.write_text(s.replace(old, new, 1))

replace('app/src/main/java/com/selyro/tv/model/Models.kt',
'''data class SeriesDetails(
    val series: SeriesItem,
    val episodes: List<Episode>
)
''',
'''data class SeriesDetails(
    val series: SeriesItem,
    val episodes: List<Episode>
)

data class RecentMedia(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val containerExtension: String? = null
)
''')

replace('app/src/main/java/com/selyro/tv/iptv/XtreamClient.kt',
'''    suspend fun seriesDetails(series: SeriesItem): SeriesDetails {
''',
'''    fun episodeStreamUrl(id: String, extension: String = "mp4"): String {
        val ext = extension.ifBlank { "mp4" }
        return "$base/series/${path(account.username)}/${path(account.password)}/$id.$ext"
    }

    suspend fun seriesDetails(series: SeriesItem): SeriesDetails {
''')
replace('app/src/main/java/com/selyro/tv/iptv/XtreamClient.kt',
'''                    streamUrl = "$base/series/${path(account.username)}/${path(account.password)}/$id.$ext",
''',
'''                    streamUrl = episodeStreamUrl(id, ext),
''')

replace('app/src/main/java/com/selyro/tv/data/AccountStore.kt',
'''import com.selyro.tv.model.PlaylistAccount
import com.selyro.tv.model.SourceType
''',
'''import com.selyro.tv.model.PlaylistAccount
import com.selyro.tv.model.RecentMedia
import com.selyro.tv.model.SourceType
''')
replace('app/src/main/java/com/selyro/tv/data/AccountStore.kt',
'''    private fun recentsKey(accountId: String) = "recent_v2.$accountId"
    private fun progressKey(accountId: String) = "playback_progress_v2.$accountId"
''',
'''    private fun recentsKey(accountId: String) = "recent_v2.$accountId"
    private fun recentItemsKey(accountId: String) = "recent_items_v3.$accountId"
    private fun progressKey(accountId: String) = "playback_progress_v2.$accountId"
''')
replace('app/src/main/java/com/selyro/tv/data/AccountStore.kt',
'''            .remove(recentsKey(accountId))
            .remove(progressKey(accountId))
''',
'''            .remove(recentsKey(accountId))
            .remove(recentItemsKey(accountId))
            .remove(progressKey(accountId))
''')
replace('app/src/main/java/com/selyro/tv/data/AccountStore.kt',
'''    fun recents(): List<String> {
        val accountId = currentAccountId() ?: return emptyList()
        return prefs.getString(recentsKey(accountId), "").orEmpty().split('|').filter { it.isNotBlank() }
    }

    fun playbackProfile(): StreamingProfile = runCatching {
''',
'''    fun recents(): List<String> {
        val accountId = currentAccountId() ?: return emptyList()
        return prefs.getString(recentsKey(accountId), "").orEmpty().split('|').filter { it.isNotBlank() }
    }

    fun setRecentItem(item: RecentMedia) {
        val accountId = currentAccountId() ?: return
        if (item.kind.isBlank() || item.id.isBlank() || item.title.isBlank()) return
        val prefKey = recentItemsKey(accountId)
        val root = runCatching { JSONObject(prefs.getString(prefKey, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val key = "${item.kind}:${item.id}"
        root.put(key, JSONObject().apply {
            put("kind", item.kind)
            put("id", item.id)
            put("title", item.title)
            put("subtitle", item.subtitle.orEmpty())
            put("extension", item.containerExtension.orEmpty())
        })
        val allowed = recents().take(50).toSet()
        val keys = root.keys().asSequence().toList()
        keys.filterNot { it in allowed }.forEach(root::remove)
        prefs.edit().putString(prefKey, root.toString()).apply()
    }

    fun recentItems(): Map<String, RecentMedia> {
        val accountId = currentAccountId() ?: return emptyMap()
        val raw = prefs.getString(recentItemsKey(accountId), null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val o = root.optJSONObject(key) ?: continue
                    val kind = o.optString("kind")
                    val id = o.optString("id")
                    val title = o.optString("title")
                    if (kind.isBlank() || id.isBlank() || title.isBlank()) continue
                    put(key, RecentMedia(
                        kind = kind,
                        id = id,
                        title = title,
                        subtitle = o.optString("subtitle").takeIf { it.isNotBlank() },
                        containerExtension = o.optString("extension").takeIf { it.isNotBlank() }
                    ))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun playbackProfile(): StreamingProfile = runCatching {
''')

p = Path('app/src/main/java/com/selyro/tv/ui/AppViewModel.kt')
s = p.read_text()
for imp in ['import kotlinx.coroutines.Dispatchers\n', 'import kotlinx.coroutines.withContext\n', 'import java.net.HttpURLConnection\n', 'import java.net.URL\n']:
    s = s.replace(imp, '')
p.write_text(s)

replace('app/src/main/java/com/selyro/tv/ui/AppViewModel.kt',
'''    val recents = MutableStateFlow(store.recents())
    val playbackProfile = MutableStateFlow(store.playbackProfile())
''',
'''    val recents = MutableStateFlow(store.recents())
    val recentItems = MutableStateFlow(store.recentItems())
    val playbackProfile = MutableStateFlow(store.playbackProfile())
''')
replace('app/src/main/java/com/selyro/tv/ui/AppViewModel.kt',
'''        recents.value = store.recents()
        playbackProgress.value = store.playbackProgress()
''',
'''        recents.value = store.recents()
        recentItems.value = store.recentItems()
        playbackProgress.value = store.playbackProgress()
''')
replace('app/src/main/java/com/selyro/tv/ui/AppViewModel.kt',
'''            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) { probeServer(target) }
                serverQualities.value = serverQualities.value + (key to result)
            }
        }
    }

    private fun probeServer(account: PlaylistAccount): ServerConnectionQuality {
        val started = System.nanoTime()
        return runCatching {
            val url = URL(account.server.trim())
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
                instanceFollowRedirects = true
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "Selyro-TV/${com.selyro.tv.BuildConfig.VERSION_NAME}")
            }
            try {
                connection.responseCode
                val latency = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
                val grade = when {
                    latency <= 250L -> ConnectionGrade.EXCELLENT
                    latency <= 900L -> ConnectionGrade.GOOD
                    else -> ConnectionGrade.WEAK
                }
                ServerConnectionQuality(grade, latency)
            } finally {
                connection.disconnect()
            }
        }.getOrElse { ServerConnectionQuality(ConnectionGrade.OFFLINE, null) }
    }
''',
'''            viewModelScope.launch {
                val result = probeServer(target)
                serverQualities.value = serverQualities.value + (key to result)
            }
        }
    }

    private suspend fun probeServer(account: PlaylistAccount): ServerConnectionQuality {
        val started = System.nanoTime()
        val connected = runCatching {
            when (account.type) {
                SourceType.XTREAM -> {
                    require(account.username.isNotBlank() && account.password.isNotBlank())
                    XtreamClient(account).authenticate() != null
                }
                SourceType.M3U -> M3uClient.fetch(account.server).channels.isNotEmpty()
            }
        }.getOrDefault(false)
        if (!connected) return ServerConnectionQuality(ConnectionGrade.OFFLINE, null)
        val latency = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
        val grade = when {
            latency <= 350L -> ConnectionGrade.EXCELLENT
            latency <= 1_200L -> ConnectionGrade.GOOD
            else -> ConnectionGrade.WEAK
        }
        return ServerConnectionQuality(grade, latency)
    }
''')
replace('app/src/main/java/com/selyro/tv/ui/AppViewModel.kt',
'''    fun markWatched(kind: String, id: String) {
        store.addRecent("$kind:$id")
        recents.value = store.recents()
    }
''',
'''    fun markWatched(
        kind: String,
        id: String,
        title: String? = null,
        subtitle: String? = null,
        containerExtension: String? = null
    ) {
        store.addRecent("$kind:$id")
        if (!title.isNullOrBlank()) {
            store.setRecentItem(RecentMedia(kind, id, title, subtitle, containerExtension))
        }
        recents.value = store.recents()
        recentItems.value = store.recentItems()
    }

    fun episodeStreamUrl(item: RecentMedia): String? {
        val account = _account.value ?: return null
        if (item.kind != "episode" || account.type != SourceType.XTREAM) return null
        return XtreamClient(account).episodeStreamUrl(item.id, item.containerExtension ?: "mp4")
    }
''')

replace('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt',
'''private data class PlayRequest(
    val kind: String,
    val id: String,
    val title: String,
    val url: String,
    val group: String? = null
)

private fun serverQualityKey(account: PlaylistAccount): String =
    "${account.type}:${account.server.trim()}:${account.username}"
''',
'''private data class PlayRequest(
    val kind: String,
    val id: String,
    val title: String,
    val url: String,
    val group: String? = null,
    val subtitle: String? = null,
    val containerExtension: String? = null
)
''')
replace('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt',
'''        vm.markWatched(request.kind, request.id)
''',
'''        vm.markWatched(request.kind, request.id, request.title, request.subtitle, request.containerExtension)
''')
replace('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt',
'''                                vm.markWatched("live", channel.id)
''',
'''                                vm.markWatched("live", channel.id, channel.name, channel.group)
''')
replace('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt',
'''    LaunchedEffect(section) { when (section) { Section.LIVE -> vm.loadLive(); Section.MOVIES -> vm.ensureMovies(); Section.SERIES -> vm.ensureSeries(); else -> Unit } }
''',
'''    LaunchedEffect(section) {
        when (section) {
            Section.LIVE -> vm.loadLive()
            Section.MOVIES -> vm.ensureMovies()
            Section.SERIES -> vm.ensureSeries()
            Section.FAVORITES, Section.RECENT -> vm.ensureMovies()
            else -> Unit
        }
    }
''')

p = Path('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt')
s = p.read_text()
s = s.replace('''onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl))''', '''onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl, subtitle = "${openedSeries.name} • S${ep.season} E${ep.episode}", containerExtension = ep.containerExtension))''', 1)
s = s.replace('''onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl))''', '''onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl, subtitle = "${current.name} • S${ep.season} E${ep.episode}", containerExtension = ep.containerExtension))''', 1)
p.write_text(s)

replace('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt',
'''@Composable private fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val recents by vm.recents.collectAsState(); val progress by vm.playbackProgress.collectAsState(); val lookup = remember(channels, movies) { buildMap<String, PlayRequest> { channels.forEach { put("live:${it.id}", PlayRequest("live", it.id, it.name, it.url, it.group)) }; movies.forEach { put("movie:${it.id}", PlayRequest("movie", it.id, it.name, it.streamUrl)) } } }; Heading(tx("Recent", "المشاهدة الأخيرة"), tx("Continue where you left off", "تابع من حيث توقفت")); LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) { items(recents.mapNotNull { lookup[it] }, key = { "${it.kind}-${it.id}" }) { item -> TvListItem(item.title, if (item.kind == "live") tx("Live", "قناة") else tx("Movie", "فيلم"), progress = progress["${item.kind}:${item.id}"]?.fraction) { onPlay(item) } } } }
''',
'''@Composable
private fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by vm.channels.collectAsState()
    val movies by vm.movies.collectAsState()
    val recents by vm.recents.collectAsState()
    val recentItems by vm.recentItems.collectAsState()
    val progress by vm.playbackProgress.collectAsState()
    val lookup = remember(channels, movies) {
        buildMap<String, PlayRequest> {
            channels.forEach { put("live:${it.id}", PlayRequest("live", it.id, it.name, it.url, it.group)) }
            movies.forEach { put("movie:${it.id}", PlayRequest("movie", it.id, it.name, it.streamUrl)) }
        }
    }
    val visible = recents.mapNotNull { key ->
        lookup[key] ?: recentItems[key]?.let { item ->
            if (item.kind == "episode") {
                vm.episodeStreamUrl(item)?.let { url ->
                    PlayRequest("episode", item.id, item.title, url, subtitle = item.subtitle, containerExtension = item.containerExtension)
                }
            } else null
        }
    }
    Heading(tx("Recent", "المشاهدة الأخيرة"), tx("Continue where you left off", "تابع من حيث توقفت"))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(visible, key = { "${it.kind}-${it.id}" }) { item ->
            val typeLabel = when (item.kind) {
                "live" -> tx("Live", "قناة")
                "episode" -> tx("Episode", "حلقة")
                else -> tx("Movie", "فيلم")
            }
            val subtitle = listOfNotNull(typeLabel, item.subtitle).filter { it.isNotBlank() }.joinToString(" • ")
            TvListItem(item.title, subtitle, progress = progress["${item.kind}:${item.id}"]?.fraction) { onPlay(item) }
        }
    }
}
''')
replace('app/src/main/java/com/selyro/tv/ui/SelyroApp.kt',
'''                        val quality = qualities[serverQualityKey(saved)]
''',
'''                        val quality = qualities[saved.id]
''')

replace('app/build.gradle.kts', 'versionCode = 21', 'versionCode = 22')
replace('app/build.gradle.kts', 'versionName = "0.4.1-resume-search-ux"', 'versionName = "0.4.2-server-recent-hotfix"')
