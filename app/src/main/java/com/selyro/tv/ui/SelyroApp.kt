package com.selyro.tv.ui

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
                MainShell(vm) { request ->
                    vm.markWatched(request.kind, request.id)
                    val manager = playback ?: PlaybackManager(context, profile).also { playback = it }
                    manager.play(request.url, request.title)
                    playing = request
                }
            }
        }
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
            Modifier.width(360.dp).fillMaxHeight().background(Rail).padding(42.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("SELYRO", color = Accent, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text("TV", color = Muted, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            Text("Fast IPTV for Android TV", color = Color.White, fontSize = 21.sp)
            Spacer(Modifier.height(10.dp))
            Text("Optimized for remote control and low-power TV sticks.", color = Muted, fontSize = 15.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 72.dp, vertical = 50.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Connect a provider", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Xtream Codes", type == SourceType.XTREAM) { type = SourceType.XTREAM }
                TvButton("M3U URL", type == SourceType.M3U) { type = SourceType.M3U }
            }
            Spacer(Modifier.height(18.dp))
            TvInput("Playlist name", name) { name = it }
            Spacer(Modifier.height(12.dp))
            TvInput(if (type == SourceType.XTREAM) "Server URL" else "M3U playlist URL", server) { server = it }
            if (type == SourceType.XTREAM) {
                Spacer(Modifier.height(12.dp))
                TvInput("Username", username) { username = it }
                Spacer(Modifier.height(12.dp))
                TvInput("Password", password, password = true) { password = it }
            }
            Spacer(Modifier.height(20.dp))
            TvButton(if (loading) "Connecting…" else "Connect", true, enabled = !loading && server.isNotBlank()) {
                vm.login(PlaylistAccount(name.ifBlank { "My IPTV" }, server, username, password, type))
            }
            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(error.orEmpty(), color = Danger, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
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

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(200.dp).fillMaxHeight().background(Rail).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("SELYRO", color = Accent, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("TV", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Section.entries.forEach { item ->
                TvNavItem(item.label, section == item) { section = item }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(26.dp)) {
            if (!error.isNullOrBlank()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A2022)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error.orEmpty(), color = Color.White, modifier = Modifier.weight(1f))
                    TvButton("Dismiss") { vm.clearError() }
                }
                Spacer(Modifier.height(12.dp))
            }
            when (section) {
                Section.HOME -> HomeScreen(vm) { section = it }
                Section.LIVE -> LiveScreen(vm, onPlay)
                Section.MOVIES -> MoviesScreen(vm, onPlay)
                Section.SERIES -> SeriesScreen(vm, onPlay)
                Section.FAVORITES -> FavoritesScreen(vm, onPlay)
                Section.RECENT -> RecentScreen(vm, onPlay)
                Section.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel, go: (Section) -> Unit) {
    val channels by vm.channels.collectAsState()
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val info by vm.providerInfo.collectAsState()
    val account by vm.account.collectAsState()

    Heading("Home", account?.name ?: "Selyro TV")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DashboardCard("Live TV", channels.size.toString()) { go(Section.LIVE) }
        DashboardCard("Movies", if (movies.isEmpty()) "Open to load" else movies.size.toString()) { go(Section.MOVIES) }
        DashboardCard("Series", if (series.isEmpty()) "Open to load" else series.size.toString()) { go(Section.SERIES) }
    }
    Spacer(Modifier.height(22.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)) {
        Text("Provider", color = Muted, fontSize = 14.sp)
        Text(account?.server.orEmpty(), color = Color.White, fontSize = 18.sp)
        if (info != null) {
            Spacer(Modifier.height(8.dp))
            Text("Status: ${info?.status ?: "Connected"}   Active: ${info?.activeConnections ?: "—"}/${info?.maxConnections ?: "—"}", color = Muted)
        }
    }
}

@Composable
private fun LiveScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.channels.collectAsState()
    val epg by vm.epgByChannel.collectAsState()
    val loading by vm.loadingSection.collectAsState()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<Channel?>(null) }
    val groups = remember(all) { listOf("All") + all.map { it.group }.distinct().sorted() }
    val filtered = remember(all, query, group) {
        all.asSequence().filter { group == "All" || it.group == group }
            .filter { query.isBlank() || it.name.contains(query, true) }
            .toList()
    }

    LaunchedEffect(selected?.id) { selected?.let(vm::loadEpg) }

    Heading("Live TV", "${all.size} channels")
    TvInput("Search channels", query) { query = it }
    Spacer(Modifier.height(14.dp))
    if (loading == "Live TV" && all.isEmpty()) { LoadingBox("Loading channels…"); return }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(groups) { g -> TvNavItem(g, group == g) { group = g } }
        }
        LazyColumn(Modifier.width(390.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { channel ->
                TvListItem(channel.name, channel.group, onFocus = { selected = channel }) {
                    onPlay(PlayRequest("live", channel.id, channel.name, channel.url))
                }
            }
        }
        ChannelDetails(Modifier.weight(1f), selected, epg[selected?.id].orEmpty(), vm, onPlay)
    }
}

@Composable
private fun ChannelDetails(modifier: Modifier, channel: Channel?, epg: List<EpgProgram>, vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)) {
        if (channel == null) { Text("Select a channel", color = Muted); return@Column }
        AsyncImage(
            model = channel.logo,
            contentDescription = null,
            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF19232D)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(14.dp))
        Text(channel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(channel.group, color = Muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Play", true) { onPlay(PlayRequest("live", channel.id, channel.name, channel.url)) }
            TvButton(if (vm.isFavorite("live", channel.id)) "★ Saved" else "☆ Favorite") { vm.toggleFavorite("live", channel.id) }
        }
        Spacer(Modifier.height(18.dp))
        Text("EPG", color = Accent, fontWeight = FontWeight.SemiBold)
        if (epg.isEmpty()) Text("No guide data", color = Muted) else epg.take(4).forEach { p ->
            Spacer(Modifier.height(8.dp)); Text(p.title.ifBlank { "Program" }, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MoviesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.movies.collectAsState()
    val loading by vm.loadingSection.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<VodItem?>(null) }
    Heading("Movies", if (all.isEmpty()) "Xtream VOD" else "${all.size} titles")
    TvInput("Search movies", query) { query = it }
    Spacer(Modifier.height(14.dp))
    if (loading == "Movies" && all.isEmpty()) { LoadingBox("Loading movies…"); return }
    val filtered = remember(all, query) { all.filter { query.isBlank() || it.name.contains(query, true) } }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LazyColumn(Modifier.width(470.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { movie ->
                TvListItem(movie.name, listOfNotNull(movie.year, movie.category).joinToString(" • "), onFocus = { selected = movie }) {
                    onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl))
                }
            }
        }
        MediaDetails(Modifier.weight(1f), selected?.name, selected?.poster, selected?.plot, selected?.rating) {
            selected?.let { movie -> onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) }
        }
    }
}

@Composable
private fun SeriesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.series.collectAsState()
    val details by vm.selectedSeriesDetails.collectAsState()
    val loading by vm.loadingSection.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SeriesItem?>(null) }
    Heading("Series", if (all.isEmpty()) "Xtream series" else "${all.size} series")
    TvInput("Search series", query) { query = it }
    Spacer(Modifier.height(14.dp))
    if (loading == "Series" && all.isEmpty()) { LoadingBox("Loading series…"); return }
    val filtered = remember(all, query) { all.filter { query.isBlank() || it.name.contains(query, true) } }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LazyColumn(Modifier.width(430.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { item ->
                TvListItem(item.name, item.category, onFocus = { selected = item }) { vm.loadSeriesDetails(item) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(18.dp)) {
            val s = selected
            if (s == null) { Text("Select a series", color = Muted); return@Column }
            Text(s.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(s.plot ?: "", color = Muted, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            TvButton("Load episodes", true) { vm.loadSeriesDetails(s) }
            Spacer(Modifier.height(12.dp))
            if (loading == "Episodes") Text("Loading episodes…", color = Accent)
            val episodes = details?.takeIf { it.series.id == s.id }?.episodes.orEmpty()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(episodes, key = { it.id }) { ep ->
                    TvListItem("S${ep.season} E${ep.episode}  ${ep.title}", ep.duration.orEmpty()) {
                        onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val favorites by vm.favorites.collectAsState()
    val c = channels.filter { "live:${it.id}" in favorites }
    val m = movies.filter { "movie:${it.id}" in favorites }
    Heading("Favorites", "${c.size + m.size} saved")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(c, key = { "c-${it.id}" }) { item -> TvListItem(item.name, "Live • ${item.group}") { onPlay(PlayRequest("live", item.id, item.name, item.url)) } }
        items(m, key = { "m-${it.id}" }) { item -> TvListItem(item.name, "Movie • ${item.category}") { onPlay(PlayRequest("movie", item.id, item.name, item.streamUrl)) } }
    }
}

@Composable
private fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val recents by vm.recents.collectAsState()
    val lookup = remember(channels, movies) {
        buildMap<String, PlayRequest> {
            channels.forEach { put("live:${it.id}", PlayRequest("live", it.id, it.name, it.url)) }
            movies.forEach { put("movie:${it.id}", PlayRequest("movie", it.id, it.name, it.streamUrl)) }
        }
    }
    Heading("Recent", "Continue where you left off")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(recents.mapNotNull { lookup[it] }, key = { "${it.kind}-${it.id}" }) { item ->
            TvListItem(item.title, item.kind.replaceFirstChar { c -> c.uppercase() }) { onPlay(item) }
        }
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val account by vm.account.collectAsState(); val profile by vm.playbackProfile.collectAsState()
    Heading("Settings", "Playback and provider")
    Text("Buffer profile", color = Color.White, fontSize = 18.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StreamingProfile.entries.forEach { p -> TvButton(p.name.lowercase().replaceFirstChar { it.uppercase() }, p == profile) { vm.setPlaybackProfile(p) } }
    }
    Spacer(Modifier.height(22.dp))
    Text("Fast starts quicker. Stable buffers more for inconsistent connections.", color = Muted)
    Spacer(Modifier.height(24.dp))
    Text("Source: ${account?.type?.name ?: ""}", color = Color.White)
    Text(account?.server.orEmpty(), color = Muted)
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TvButton("Refresh live") { vm.loadLive(force = true) }
        TvButton("Disconnect") { vm.logout() }
    }
}

@Composable private fun Heading(title: String, subtitle: String) {
    Text(title, color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, color = Muted, fontSize = 15.sp)
    Spacer(Modifier.height(18.dp))
}

@Composable private fun DashboardCard(title: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(230.dp).height(130.dp).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Focus else Panel).onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable().padding(18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun TvNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (focused) Focus else if (selected) Color(0xFF17242E) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 13.dp, vertical = 11.dp)
    ) { Text(label, color = if (focused || selected) Color.White else Muted, fontSize = 15.sp) }
}

@Composable private fun TvListItem(title: String, subtitle: String = "", onFocus: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (focused) Focus else Panel)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus?.invoke() }
            .clickable(onClick = onClick).focusable().padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable private fun TvButton(label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background = when { !enabled -> Color(0xFF171C22); focused -> Accent; selected -> Color(0xFF275A54); else -> Panel }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(background)
            .onFocusChanged { focused = it.isFocused }.clickable(enabled = enabled, onClick = onClick).focusable(enabled)
            .padding(horizontal = 17.dp, vertical = 11.dp)
    ) { Text(label, color = if (focused) Color.Black else if (enabled) Color.White else Color.DarkGray, fontWeight = FontWeight.SemiBold) }
}

@Composable private fun TvInput(label: String, value: String, password: Boolean = false, onValueChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
                .border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF26313C), RoundedCornerShape(10.dp))
                .onFocusChanged { focused = it.isFocused }.padding(horizontal = 14.dp, vertical = 13.dp)
        )
    }
}

@Composable private fun LoadingBox(text: String) {
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Panel), contentAlignment = Alignment.Center) {
        Text(text, color = Accent, fontSize = 18.sp)
    }
}

@Composable private fun MediaDetails(modifier: Modifier, title: String?, image: String?, plot: String?, rating: String?, onPlay: () -> Unit) {
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)) {
        if (title == null) { Text("Select a title", color = Muted); return@Column }
        AsyncImage(model = image, contentDescription = null, modifier = Modifier.width(150.dp).height(210.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(12.dp)); Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (!rating.isNullOrBlank()) Text("Rating $rating", color = Accent)
        if (!plot.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(plot, color = Muted, maxLines = 6) }
        Spacer(Modifier.height(16.dp)); TvButton("Play", true, onClick = onPlay)
    }
}
