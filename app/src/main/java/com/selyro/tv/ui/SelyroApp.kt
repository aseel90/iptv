package com.selyro.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.selyro.tv.model.PlaylistAccount
import com.selyro.tv.model.SourceType
import com.selyro.tv.player.PlaybackManager

internal enum class Section(val label: String, val short: String) {
    HOME("Home", "H"),
    LIVE("Live TV", "L"),
    MOVIES("Movies", "M"),
    SERIES("Series", "S"),
    FAVORITES("Favorites", "F"),
    RECENT("Recent", "R"),
    SETTINGS("Settings", "⚙")
}

internal enum class ViewMode { GRID, LIST }
internal data class PlayRequest(val kind: String, val id: String, val title: String, val url: String)

@Composable
fun SelyroApp(vm: AppViewModel = viewModel()) {
    val account by vm.account.collectAsState()
    val profile by vm.playbackProfile.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val playback = remember { PlaybackManager(context, profile) }
    var playing by remember { mutableStateOf<PlayRequest?>(null) }

    DisposableEffect(playback) { onDispose { playback.release() } }
    LaunchedEffect(profile) { playback.setProfile(profile) }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Bg)) {
            val request = playing
            when {
                request != null -> PlayerScreen(
                    player = playback.player,
                    title = request.title,
                    kind = request.kind,
                    onBack = { playing = null }
                )
                account == null -> LoginScreen(vm)
                else -> MainShell(vm) { item ->
                    vm.markWatched(item.kind, item.id)
                    playback.play(item.url, item.title)
                    playing = item
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

    Row(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(listOf(Color(0xFF061014), Bg, Bg))
        )
    ) {
        Column(
            Modifier.width(390.dp).fillMaxHeight().padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            BrandLockup(compact = false)
            Spacer(Modifier.height(34.dp))
            Text(
                "Your entertainment, without the clutter.",
                color = TextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Built for Android TV, remote-first navigation and stable IPTV playback.",
                color = Muted,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }

        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(570.dp)
                    .then(surfacePanelModifier(26.dp))
                    .padding(32.dp)
            ) {
                Text("Connect provider", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("Add your Xtream Codes or M3U playlist.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Xtream Codes", selected = type == SourceType.XTREAM) { type = SourceType.XTREAM }
                    TvButton("M3U URL", selected = type == SourceType.M3U) { type = SourceType.M3U }
                }
                Spacer(Modifier.height(18.dp))
                TvInput("Playlist name", name) { name = it }
                Spacer(Modifier.height(12.dp))
                TvInput(if (type == SourceType.XTREAM) "Server URL" else "M3U playlist URL", server) { server = it }
                if (type == SourceType.XTREAM) {
                    Spacer(Modifier.height(12.dp)); TvInput("Username", username) { username = it }
                    Spacer(Modifier.height(12.dp)); TvInput("Password", password, password = true) { password = it }
                }
                Spacer(Modifier.height(20.dp))
                TvButton(
                    label = if (loading) "Connecting…" else "Connect",
                    selected = true,
                    enabled = !loading && server.isNotBlank()
                ) {
                    vm.login(PlaylistAccount(name.ifBlank { "My IPTV" }, server, username, password, type))
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(error.orEmpty(), color = Danger, fontSize = 14.sp)
                }
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

    Row(Modifier.fillMaxSize().background(Bg)) {
        NavigationRail(selected = section, onSelect = { section = it })
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .padding(start = 26.dp, end = 32.dp, top = 28.dp, bottom = 24.dp)
        ) {
            if (!error.isNullOrBlank()) {
                ErrorBanner(error.orEmpty()) { vm.clearError() }
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
private fun NavigationRail(selected: Section, onSelect: (Section) -> Unit) {
    Column(
        Modifier.width(188.dp).fillMaxHeight().background(Rail).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrandLockup(compact = true)
        Spacer(Modifier.height(24.dp))
        Section.entries.forEach { item ->
            TvNavItem(item.label, item.short, selected = selected == item) { onSelect(item) }
        }
        Spacer(Modifier.weight(1f))
        Text("Selyro TV 0.3.0", color = Subtle, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
    }
}
