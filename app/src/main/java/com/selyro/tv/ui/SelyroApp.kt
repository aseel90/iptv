package com.selyro.tv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.selyro.tv.model.*
import com.selyro.tv.player.PlaybackManager
import com.selyro.tv.player.StreamingProfile
import com.selyro.tv.update.UpdateInfo
import com.selyro.tv.update.UpdateManager
import com.selyro.tv.update.UpdateStatus
import kotlinx.coroutines.launch

private val Bg = Color(0xFF070B10)
private val Rail = Color(0xFF0D131A)
private val Panel = Color(0xFF111923)
private val Focus = Color(0xFF20313D)
private val Accent = Color(0xFF63D8C6)
private val Muted = Color(0xFFAAB5C1)
private val Danger = Color(0xFFFF8A80)

private enum class Section(val label: String) {
    HOME("Home"), LIVE("Live TV"), MOVIES("Movies"), SERIES("Series"), FAVORITES("Favorites"), RECENT("Recent"), SETTINGS("Settings")
}

private data class PlayRequest(val kind: String, val id: String, val title: String, val url: String)

@Composable
fun SelyroApp(vm: AppViewModel = viewModel()) {
    val account by vm.account.collectAsState()
    val profile by vm.playbackProfile.collectAsState()
    val context = LocalContext.current.applicationContext
    var playback by remember { mutableStateOf<PlaybackManager?>(null) }
    var playing by remember { mutableStateOf<PlayRequest?>(null) }
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    val scope = rememberCoroutineScope()

    fun checkUpdates() {
        scope.launch {
            updateStatus = UpdateStatus.Checking
            updateStatus = UpdateManager.check()
        }
    }

    fun beginUpdate(info: UpdateInfo) {
        if (UpdateManager.needsInstallPermission(context)) {
            UpdateManager.openInstallPermission(context)
            updateStatus = UpdateStatus.Error("Allow Selyro TV to install unknown apps, then press UPDATE again.")
            return
        }
        scope.launch {
            updateStatus = UpdateStatus.Downloading(0)
            val result = UpdateManager.download(context, info) { percent ->
                updateStatus = UpdateStatus.Downloading(percent)
            }
            result.onSuccess { file ->
                updateStatus = UpdateStatus.Ready(info, file)
                UpdateManager.install(context, file)
            }.onFailure {
                updateStatus = UpdateStatus.Error(it.message ?: "Update download failed")
            }
        }
    }

    LaunchedEffect(Unit) { checkUpdates() }

    DisposableEffect(Unit) {
        onDispose {
            playback?.release()
            playback = null
        }
    }
    LaunchedEffect(profile) { playback?.setProfile(profile) }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Bg)) {
            val activePlayback = playback
            if (playing != null && activePlayback != null) {
                PlayerScreen(player = activePlayback.player) {
                    activePlayback.stop()
                    playing = null
                }
            } else if (account == null) {
                LoginScreen(vm)
            } else {
                MainShell(vm, updateStatus, ::checkUpdates, ::beginUpdate) { request ->
                    vm.markWatched(request.kind, request.id)
                    val manager = playback ?: PlaybackManager(context, profile).also { playback = it }
                    manager.play(request.url, request.title)
                    playing = request
                }
            }

            UpdateOverlay(
                status = updateStatus,
                onCheck = ::checkUpdates,
                onUpdate = ::beginUpdate,
                onInstall = { info, file ->
                    if (UpdateManager.needsInstallPermission(context)) UpdateManager.openInstallPermission(context)
                    else UpdateManager.install(context, file)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)
            )
        }
    }
}

@Composable
private fun UpdateOverlay(
    status: UpdateStatus,
    onCheck: () -> Unit,
    onUpdate: (UpdateInfo) -> Unit,
    onInstall: (UpdateInfo, java.io.File) -> Unit,
    modifier: Modifier = Modifier
) {
    when (status) {
        is UpdateStatus.Available -> Column(
            modifier.width(360.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF14242B)).border(1.dp, Accent, RoundedCornerShape(14.dp)).padding(14.dp)
        ) {
            Text("Update available", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Selyro TV ${status.info.versionName}", color = Color.White, fontSize = 14.sp)
            if (status.info.notes.isNotBlank()) Text(status.info.notes, color = Muted, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.height(9.dp))
            TvButton("UPDATE NOW", selected = true, modifier = Modifier.fillMaxWidth()) { onUpdate(status.info) }
        }
        is UpdateStatus.Downloading -> Column(
            modifier.width(300.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14242B)).padding(14.dp)
        ) {
            Text("Downloading update… ${status.percent}%", color = Color.White, fontSize = 14.sp)
        }
        is UpdateStatus.Ready -> Column(
            modifier.width(320.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14242B)).padding(14.dp)
        ) {
            Text("Update downloaded", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TvButton("INSTALL UPDATE", selected = true, modifier = Modifier.fillMaxWidth()) { onInstall(status.info, status.file) }
        }
        is UpdateStatus.Error -> Column(
            modifier.width(360.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A2022)).padding(14.dp)
        ) {
            Text(status.message, color = Color.White, fontSize = 13.sp, maxLines = 3)
            Spacer(Modifier.height(8.dp))
            TvButton("CHECK AGAIN", modifier = Modifier.fillMaxWidth(), onClick = onCheck)
        }
        else -> Unit
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel) {
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    var type by remember { mutableStateOf(SourceType.XTREAM) }
    var name by remember { mutableStateOf("My IPTV") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(Rail).padding(28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("SELYRO", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("TV", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Text("Fast IPTV for Android TV", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(7.dp))
            Text("Optimized for remote control and low-power TV sticks.", color = Muted, fontSize = 13.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 40.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Connect a provider", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Xtream Codes", type == SourceType.XTREAM) { type = SourceType.XTREAM }
                TvButton("M3U URL", type == SourceType.M3U) { type = SourceType.M3U }
            }
            Spacer(Modifier.height(10.dp))
            TvInput("Playlist name", name) { name = it }
            Spacer(Modifier.height(7.dp))
            TvInput(if (type == SourceType.XTREAM) "Server URL" else "M3U playlist URL", server) { server = it }
            if (type == SourceType.XTREAM) {
                Spacer(Modifier.height(7.dp))
                TvInput("Username", username) { username = it }
                Spacer(Modifier.height(7.dp))
                TvInput("Password", password, password = true) { password = it }
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error.orEmpty(), color = Danger, fontSize = 13.sp, maxLines = 2)
            }
            Spacer(Modifier.height(12.dp))
            TvButton(
                if (loading) "CONNECTING…" else "CONNECT",
                selected = true,
                enabled = !loading && server.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                vm.login(PlaylistAccount(name.ifBlank { "My IPTV" }, server, username, password, type))
            }
        }
    }
}

@Composable
private fun MainShell(
    vm: AppViewModel,
    updateStatus: UpdateStatus,
    onCheckUpdates: () -> Unit,
    onUpdate: (UpdateInfo) -> Unit,
    onPlay: (PlayRequest) -> Unit
) {
    var section by remember { mutableStateOf(Section.HOME) }
    val error by vm.error.collectAsState()

    LaunchedEffect(section) {
        when (section) {
            Section.LIVE -> vm.loadLive()
            Section.MOVIES -> vm.ensureMovies()
            Section.SERIES -> vm.ensureSeries()
            else -> Unit
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1100.dp
        val railWidth = if (compact) 168.dp else 200.dp
        val contentPadding = if (compact) 16.dp else 26.dp
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(railWidth).fillMaxHeight().background(Rail).padding(if (compact) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
            ) {
                Text("SELYRO", color = Accent, fontSize = if (compact) 24.sp else 27.sp, fontWeight = FontWeight.Bold)
                Text("TV", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                Section.entries.forEach { item -> TvNavItem(item.label, section == item) { section = item } }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(contentPadding)) {
                if (!error.isNullOrBlank()) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A2022)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error.orEmpty(), color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
                        Spacer(Modifier.width(8.dp))
                        TvButton("Dismiss") { vm.clearError() }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                when (section) {
                    Section.HOME -> HomeScreen(vm) { section = it }
                    Section.LIVE -> LiveScreen(vm, onPlay)
                    Section.MOVIES -> MoviesScreen(vm, onPlay)
                    Section.SERIES -> SeriesScreen(vm, onPlay)
                    Section.FAVORITES -> FavoritesScreen(vm, onPlay)
                    Section.RECENT -> RecentScreen(vm, onPlay)
                    Section.SETTINGS -> SettingsScreen(vm, updateStatus, onCheckUpdates, onUpdate)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel, go: (Section) -> Unit) {
    val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val series by vm.series.collectAsState(); val info by vm.providerInfo.collectAsState(); val account by vm.account.collectAsState()
    Heading("Home", account?.name ?: "Selyro TV")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DashboardCard("Live TV", channels.size.toString(), Modifier.weight(1f)) { go(Section.LIVE) }
        DashboardCard("Movies", if (movies.isEmpty()) "Open to load" else movies.size.toString(), Modifier.weight(1f)) { go(Section.MOVIES) }
        DashboardCard("Series", if (series.isEmpty()) "Open to load" else series.size.toString(), Modifier.weight(1f)) { go(Section.SERIES) }
    }
    Spacer(Modifier.height(18.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(18.dp)) {
        Text("Provider", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(account?.server.orEmpty(), color = Color.White, fontSize = 16.sp, maxLines = 1)
        if (info != null) {
            Spacer(Modifier.height(8.dp))
            Text("Status: ${info?.status ?: "Connected"}   •   Active: ${info?.activeConnections ?: "—"}/${info?.maxConnections ?: "—"}", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LiveScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.channels.collectAsState(); val epg by vm.epgByChannel.collectAsState(); val loading by vm.loadingSection.collectAsState(); var query by remember { mutableStateOf("") }; var group by remember { mutableStateOf("All") }; var selected by remember { mutableStateOf<Channel?>(null) }
    val groups = remember(all) { listOf("All") + all.map { it.group.ifBlank { "Other" } }.distinct().sorted() }
    val filtered = remember(all, query, group) { all.asSequence().filter { group == "All" || it.group == group }.filter { query.isBlank() || it.name.contains(query, true) }.toList() }
    LaunchedEffect(selected?.id) { selected?.let(vm::loadEpg) }
    Heading("Live TV", "${all.size} channels")
    TvInput("Search channels", query) { query = it }
    Spacer(Modifier.height(12.dp))
    if (loading == "Live TV" && all.isEmpty()) { LoadingBox("Loading channels…"); return }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showDetails = maxWidth >= 720.dp
        val categoryWidth = if (maxWidth < 850.dp) 150.dp else 185.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyColumn(Modifier.width(categoryWidth), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(groups) { g -> TvNavItem(if (g == "All") "All (${all.size})" else "$g (${all.count { it.group == g }})", group == g) { group = g; selected = null } }
            }
            LazyColumn(Modifier.weight(if (showDetails) 1.15f else 1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(filtered, key = { it.id }) { channel -> TvListItem(channel.name, channel.group, onFocus = { selected = channel }) { onPlay(PlayRequest("live", channel.id, channel.name, channel.url)) } }
            }
            if (showDetails) ChannelDetails(Modifier.weight(0.85f), selected, epg[selected?.id].orEmpty(), vm, onPlay)
        }
    }
}

@Composable
private fun ChannelDetails(modifier: Modifier, channel: Channel?, epg: List<EpgProgram>, vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)) {
        if (channel == null) { Text("Select a channel", color = Muted); return@Column }
        AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(14.dp)); Text(channel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(channel.group, color = Muted); Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TvButton("Play", true) { onPlay(PlayRequest("live", channel.id, channel.name, channel.url)) }; TvButton(if (vm.isFavorite("live", channel.id)) "★ Saved" else "☆ Favorite") { vm.toggleFavorite("live", channel.id) } }
        Spacer(Modifier.height(18.dp)); Text("EPG", color = Accent, fontWeight = FontWeight.SemiBold); if (epg.isEmpty()) Text("No guide data", color = Muted) else epg.take(4).forEach { p -> Spacer(Modifier.height(8.dp)); Text(p.title.ifBlank { "Program" }, color = Color.White, fontSize = 15.sp) }
    }
}

@Composable
private fun MoviesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.movies.collectAsState(); val loading by vm.loadingSection.collectAsState(); var query by remember { mutableStateOf("") }; var category by remember { mutableStateOf("All") }; var selected by remember { mutableStateOf<VodItem?>(null) }
    val categories = remember(all) { all.map { it.category.ifBlank { "Other" } }.distinct().sorted() }
    LaunchedEffect(categories) { if (categories.isNotEmpty() && category == "All") category = categories.first() }
    val filtered = remember(all, query, category) { all.filter { (category == "All" || it.category == category) && (query.isBlank() || it.name.contains(query, true)) } }
    Heading("Movies", if (all.isEmpty()) "Xtream VOD" else "${all.size} movies • ${categories.size} categories")
    TvInput("Search in ${if (category == "All") "movies" else category}", query) { query = it }
    Spacer(Modifier.height(12.dp))
    if (loading == "Movies" && all.isEmpty()) { LoadingBox("Loading movies…"); return }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showDetails = maxWidth >= 700.dp
        val categoryWidth = if (maxWidth < 850.dp) 155.dp else 190.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyColumn(Modifier.width(categoryWidth), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(listOf("All") + categories) { c ->
                    val count = if (c == "All") all.size else all.count { it.category == c }
                    TvNavItem("$c ($count)", category == c) { category = c; selected = null }
                }
            }
            LazyColumn(Modifier.weight(if (showDetails) 1.12f else 1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(filtered, key = { it.id }) { movie ->
                    TvListItem(movie.name, listOfNotNull(movie.year, movie.category).filter { it.isNotBlank() }.joinToString(" • "), onFocus = { selected = movie }) {
                        onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl))
                    }
                }
            }
            if (showDetails) MediaDetails(Modifier.weight(0.88f), selected?.name, selected?.poster, selected?.plot, selected?.rating) {
                selected?.let { movie -> onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) }
            }
        }
    }
}

@Composable
private fun SeriesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.series.collectAsState(); val details by vm.selectedSeriesDetails.collectAsState(); val loading by vm.loadingSection.collectAsState(); var query by remember { mutableStateOf("") }; var category by remember { mutableStateOf("All") }; var selected by remember { mutableStateOf<SeriesItem?>(null) }
    val categories = remember(all) { all.map { it.category.ifBlank { "Other" } }.distinct().sorted() }
    LaunchedEffect(categories) { if (categories.isNotEmpty() && category == "All") category = categories.first() }
    val filtered = remember(all, query, category) { all.filter { (category == "All" || it.category == category) && (query.isBlank() || it.name.contains(query, true)) } }
    Heading("Series", if (all.isEmpty()) "Xtream series" else "${all.size} series • ${categories.size} categories")
    TvInput("Search in ${if (category == "All") "series" else category}", query) { query = it }
    Spacer(Modifier.height(12.dp))
    if (loading == "Series" && all.isEmpty()) { LoadingBox("Loading series…"); return }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showEpisodes = maxWidth >= 700.dp
        val categoryWidth = if (maxWidth < 850.dp) 155.dp else 190.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyColumn(Modifier.width(categoryWidth), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(listOf("All") + categories) { c ->
                    val count = if (c == "All") all.size else all.count { it.category == c }
                    TvNavItem("$c ($count)", category == c) { category = c; selected = null; vm.clearSeriesDetails() }
                }
            }
            LazyColumn(Modifier.weight(if (showEpisodes) 1.05f else 1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(filtered, key = { it.id }) { item ->
                    TvListItem(item.name, item.category, onFocus = { selected = item }) {
                        selected = item
                        vm.loadSeriesDetails(item)
                    }
                }
            }
            if (showEpisodes) {
                Column(Modifier.weight(0.95f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    val s = selected
                    if (s == null) { Text("Select a series", color = Muted); return@Column }
                    Text(s.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(listOfNotNull(s.year, s.category).filter { it.isNotBlank() }.joinToString(" • "), color = Accent, fontSize = 12.sp, maxLines = 1)
                    if (!s.plot.isNullOrBlank()) { Spacer(Modifier.height(6.dp)); Text(s.plot.orEmpty(), color = Muted, fontSize = 13.sp, maxLines = 3) }
                    Spacer(Modifier.height(10.dp))
                    TvButton("LOAD EPISODES", selected = true, modifier = Modifier.fillMaxWidth()) { vm.loadSeriesDetails(s) }
                    Spacer(Modifier.height(10.dp))
                    if (loading == "Episodes") Text("Loading episodes…", color = Accent)
                    val episodes = details?.takeIf { it.series.id == s.id }?.episodes.orEmpty()
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(episodes, key = { it.id }) { ep -> TvListItem("S${ep.season} E${ep.episode}  ${ep.title}", ep.duration.orEmpty()) { onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val favorites by vm.favorites.collectAsState(); val c = channels.filter { "live:${it.id}" in favorites }; val m = movies.filter { "movie:${it.id}" in favorites }
    Heading("Favorites", "${c.size + m.size} saved"); LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) { items(c, key = { "c-${it.id}" }) { item -> TvListItem(item.name, "Live • ${item.group}") { onPlay(PlayRequest("live", item.id, item.name, item.url)) } }; items(m, key = { "m-${it.id}" }) { item -> TvListItem(item.name, "Movie • ${item.category}") { onPlay(PlayRequest("movie", item.id, item.name, item.streamUrl)) } } }
}

@Composable
private fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val recents by vm.recents.collectAsState(); val lookup = remember(channels, movies) { buildMap<String, PlayRequest> { channels.forEach { put("live:${it.id}", PlayRequest("live", it.id, it.name, it.url)) }; movies.forEach { put("movie:${it.id}", PlayRequest("movie", it.id, it.name, it.streamUrl)) } } }
    Heading("Recent", "Continue where you left off"); LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) { items(recents.mapNotNull { lookup[it] }, key = { "${it.kind}-${it.id}" }) { item -> TvListItem(item.title, item.kind.replaceFirstChar { c -> c.uppercase() }) { onPlay(item) } } }
}

@Composable
private fun SettingsScreen(vm: AppViewModel, updateStatus: UpdateStatus, onCheckUpdates: () -> Unit, onUpdate: (UpdateInfo) -> Unit) {
    val account by vm.account.collectAsState(); val profile by vm.playbackProfile.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Settings", "Playback, provider, updates and app information") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(16.dp)) {
                Text("Playback", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamingProfile.entries.forEach { p -> TvButton(p.name.lowercase().replaceFirstChar { it.uppercase() }, p == profile) { vm.setPlaybackProfile(p) } }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(16.dp)) {
                Text("Updates", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                when (updateStatus) {
                    is UpdateStatus.Available -> TvButton("UPDATE TO ${updateStatus.info.versionName}", selected = true) { onUpdate(updateStatus.info) }
                    is UpdateStatus.Checking -> Text("Checking for updates…", color = Muted)
                    is UpdateStatus.Downloading -> Text("Downloading update… ${updateStatus.percent}%", color = Accent)
                    is UpdateStatus.UpToDate -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("Selyro TV is up to date", color = Accent); TvButton("CHECK AGAIN", onClick = onCheckUpdates) }
                    else -> TvButton("CHECK FOR UPDATES", onClick = onCheckUpdates)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(16.dp)) {
                Text("Provider", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Source: ${account?.type?.name ?: ""}", color = Color.White)
                Text(account?.server.orEmpty(), color = Muted, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvButton("Refresh live") { vm.loadLive(force = true) }; TvButton("Disconnect") { vm.logout() } }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF132129)).border(1.dp, Color(0xFF25433F), RoundedCornerShape(14.dp)).padding(16.dp)) {
                Text("About Selyro TV", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Developed by", color = Muted, fontSize = 12.sp)
                Text("aseel salah", color = Accent, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Version ${com.selyro.tv.BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun Heading(title: String, subtitle: String) { Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1); Spacer(Modifier.height(14.dp)) }
@Composable private fun DashboardCard(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Column(modifier.height(116.dp).clip(RoundedCornerShape(16.dp)).background(if (focused) Focus else Panel).border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF1C2833), RoundedCornerShape(16.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(15.dp), verticalArrangement = Arrangement.Center) { Text(title, color = if (focused) Color.White else Muted, fontSize = 14.sp); Spacer(Modifier.height(5.dp)); Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }
@Composable private fun TvNavItem(label: String, selected: Boolean, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (focused) Focus else if (selected) Color(0xFF17242E) else Color.Transparent).border(if (focused) 1.dp else 0.dp, Accent, RoundedCornerShape(10.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 11.dp, vertical = 9.dp)) { Text(label, color = if (focused || selected) Color.White else Muted, fontSize = 14.sp, maxLines = 2) } }
@Composable private fun TvListItem(title: String, subtitle: String = "", onFocus: (() -> Unit)? = null, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (focused) Focus else Panel).border(if (focused) 1.dp else 0.dp, Accent, RoundedCornerShape(11.dp)).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus?.invoke() }.clickable(onClick = onClick).focusable().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 15.sp, maxLines = 1); if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1) } } }
@Composable private fun TvButton(label: String, selected: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; val background = when { !enabled -> Color(0xFF171C22); focused -> Accent; selected -> Color(0xFF275A54); else -> Panel }; Box(modifier.clip(RoundedCornerShape(10.dp)).background(background).onFocusChanged { focused = it.isFocused }.clickable(enabled = enabled, onClick = onClick).focusable(enabled).padding(horizontal = 17.dp, vertical = 11.dp)) { Text(label, color = if (focused) Color.Black else if (enabled) Color.White else Color.DarkGray, fontWeight = FontWeight.SemiBold) } }
@Composable private fun TvInput(label: String, value: String, password: Boolean = false, onValueChange: (String) -> Unit) { var focused by remember { mutableStateOf(false) }; Column { Text(label, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(4.dp)); BasicTextField(value = value, onValueChange = onValueChange, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), singleLine = true, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(Panel).border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF26313C), RoundedCornerShape(10.dp)).onFocusChanged { focused = it.isFocused }.padding(horizontal = 13.dp, vertical = 10.dp)) } }
@Composable private fun LoadingBox(text: String) { Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Panel), contentAlignment = Alignment.Center) { Text(text, color = Accent, fontSize = 18.sp) } }
@Composable private fun MediaDetails(modifier: Modifier, title: String?, image: String?, plot: String?, rating: String?, onPlay: () -> Unit) { Column(modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)) { if (title == null) { Text("Select a title", color = Muted); return@Column }; AsyncImage(model = image, contentDescription = null, modifier = Modifier.width(150.dp).height(210.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Crop); Spacer(Modifier.height(12.dp)); Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); if (!rating.isNullOrBlank()) Text("Rating $rating", color = Accent); if (!plot.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(plot, color = Muted, maxLines = 6) }; Spacer(Modifier.height(16.dp)); TvButton("Play", true, onClick = onPlay) } }
