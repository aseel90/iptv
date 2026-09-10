package com.selyro.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.selyro.tv.model.Episode
import com.selyro.tv.model.SeriesItem
import com.selyro.tv.model.VodItem
import com.selyro.tv.player.StreamingProfile

@Composable
internal fun MoviesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.movies.collectAsState()
    val loading by vm.loadingSection.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<VodItem?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    val filtered = remember(all, query) { all.filter { query.isBlank() || it.name.contains(query, true) } }

    Heading("Movies", if (all.isEmpty()) "On demand" else "${all.size} titles")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvInput("Search movies", query, compact = true) { query = it }
        SmallPill("Grid", selected = viewMode == ViewMode.GRID) { viewMode = ViewMode.GRID }
        SmallPill("List", selected = viewMode == ViewMode.LIST) { viewMode = ViewMode.LIST }
    }
    Spacer(Modifier.height(14.dp))
    if (loading == "Movies" && all.isEmpty()) {
        LoadingBox("Loading movies…")
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.weight(1.45f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(filtered, key = { it.id }) { movie ->
                    MediaPosterCard(
                        title = movie.name,
                        image = movie.poster,
                        subtitle = movie.year ?: movie.category,
                        onFocus = { selected = movie }
                    ) {
                        onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1.45f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filtered, key = { it.id }) { movie ->
                    MediaListRow(
                        movie.name,
                        movie.poster,
                        movie.year ?: movie.category,
                        onFocus = { selected = movie }
                    ) {
                        onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl))
                    }
                }
            }
        }

        MovieDetails(
            Modifier.weight(0.8f),
            movie = selected,
            favorite = selected?.let { vm.isFavorite("movie", it.id) } == true,
            onFavorite = { selected?.let { vm.toggleFavorite("movie", it.id) } },
            onPlay = {
                selected?.let { onPlay(PlayRequest("movie", it.id, it.name, it.streamUrl)) }
            }
        )
    }
}

@Composable
private fun MovieDetails(
    modifier: Modifier,
    movie: VodItem?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    Column(modifier.fillMaxHeight().then(surfacePanelModifier(22.dp)).padding(22.dp)) {
        if (movie == null) {
            EmptyPrompt("Select a movie", "Poster art and details will appear here.")
            return@Column
        }
        AsyncImage(
            model = movie.poster,
            contentDescription = null,
            modifier = Modifier.width(150.dp).height(215.dp)
                .clip(RoundedCornerShape(14.dp)).background(SurfaceRaised),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(16.dp))
        Text(movie.name, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            movie.year?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
            movie.rating?.takeIf { it.isNotBlank() }?.let { MetaChip("★ $it") }
            movie.category.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            movie.plot ?: "No description available.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TvButton("Play", selected = true, onClick = onPlay)
            TvButton(if (favorite) "Saved" else "Favorite", onClick = onFavorite)
        }
    }
}

@Composable
internal fun SeriesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.series.collectAsState()
    val details by vm.selectedSeriesDetails.collectAsState()
    val loading by vm.loadingSection.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SeriesItem?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    val filtered = remember(all, query) { all.filter { query.isBlank() || it.name.contains(query, true) } }

    Heading("Series", if (all.isEmpty()) "Shows & episodes" else "${all.size} series")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvInput("Search series", query, compact = true) { query = it }
        SmallPill("Grid", selected = viewMode == ViewMode.GRID) { viewMode = ViewMode.GRID }
        SmallPill("List", selected = viewMode == ViewMode.LIST) { viewMode = ViewMode.LIST }
    }
    Spacer(Modifier.height(14.dp))
    if (loading == "Series" && all.isEmpty()) {
        LoadingBox("Loading series…")
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.weight(1.25f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(filtered, key = { it.id }) { item ->
                    MediaPosterCard(
                        title = item.name,
                        image = item.poster,
                        subtitle = item.year ?: item.category,
                        onFocus = { selected = item }
                    ) { vm.loadSeriesDetails(item) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1.25f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    MediaListRow(
                        item.name,
                        item.poster,
                        item.year ?: item.category,
                        onFocus = { selected = item }
                    ) { vm.loadSeriesDetails(item) }
                }
            }
        }

        SeriesDetailsPanel(
            modifier = Modifier.weight(1f),
            series = selected,
            episodes = details?.takeIf { it.series.id == selected?.id }?.episodes.orEmpty(),
            loading = loading == "Episodes",
            onLoad = { selected?.let(vm::loadSeriesDetails) },
            onPlay = { ep -> onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl)) }
        )
    }
}

@Composable
private fun SeriesDetailsPanel(
    modifier: Modifier,
    series: SeriesItem?,
    episodes: List<Episode>,
    loading: Boolean,
    onLoad: () -> Unit,
    onPlay: (Episode) -> Unit
) {
    Column(modifier.fillMaxHeight().then(surfacePanelModifier(22.dp)).padding(20.dp)) {
        if (series == null) {
            EmptyPrompt("Select a series", "Choose a poster, then press OK to load seasons and episodes.")
            return@Column
        }

        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = series.poster,
                contentDescription = null,
                modifier = Modifier.width(100.dp).height(142.dp)
                    .clip(RoundedCornerShape(12.dp)).background(SurfaceRaised),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(series.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    series.year?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                    series.rating?.takeIf { it.isNotBlank() }?.let { MetaChip("★ $it") }
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    series.plot ?: "No description available.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (episodes.isEmpty()) {
            TvButton(
                if (loading) "Loading episodes…" else "Load episodes",
                selected = true,
                enabled = !loading,
                onClick = onLoad
            )
            Spacer(Modifier.height(12.dp))
            Text("Press OK on a series poster to fetch its seasons.", color = Subtle, fontSize = 12.sp)
            return@Column
        }

        val seasons = remember(series.id, episodes) { episodes.map { it.season }.distinct().sorted() }
        var selectedSeason by remember(series.id, seasons) { mutableStateOf(seasons.firstOrNull() ?: 1) }
        Text("Seasons", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            seasons.take(10).forEach { season ->
                SmallPill("S$season", selected = season == selectedSeason) { selectedSeason = season }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Episodes", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(episodes.filter { it.season == selectedSeason }, key = { it.id }) { episode ->
                EpisodeRow(episode) { onPlay(episode) }
            }
        }
    }
}

@Composable
internal fun FavoritesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by vm.channels.collectAsState()
    val movies by vm.movies.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val savedChannels = channels.filter { "live:${it.id}" in favorites }
    val savedMovies = movies.filter { "movie:${it.id}" in favorites }

    Heading("Favorites", "${savedChannels.size + savedMovies.size} saved")
    if (savedChannels.isEmpty() && savedMovies.isEmpty()) {
        EmptyLarge("Nothing saved yet", "Use Favorite on a channel or movie to keep it here.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(savedChannels, key = { "c-${it.id}" }) { item ->
            ModernListRow(item.name, "Live • ${item.group}", badge = "LIVE") {
                onPlay(PlayRequest("live", item.id, item.name, item.url))
            }
        }
        items(savedMovies, key = { "m-${it.id}" }) { item ->
            ModernListRow(item.name, "Movie • ${item.category}", badge = item.year.orEmpty()) {
                onPlay(PlayRequest("movie", item.id, item.name, item.streamUrl))
            }
        }
    }
}

@Composable
internal fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val channels by vm.channels.collectAsState()
    val movies by vm.movies.collectAsState()
    val recents by vm.recents.collectAsState()
    val lookup = remember(channels, movies) {
        buildMap<String, PlayRequest> {
            channels.forEach { put("live:${it.id}", PlayRequest("live", it.id, it.name, it.url)) }
            movies.forEach { put("movie:${it.id}", PlayRequest("movie", it.id, it.name, it.streamUrl)) }
        }
    }
    val recentItems = recents.mapNotNull { lookup[it] }
    Heading("Recent", "Continue where you left off")
    if (recentItems.isEmpty()) {
        EmptyLarge("No recent playback", "What you watch will appear here for quick access.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recentItems, key = { "${it.kind}-${it.id}" }) { item ->
            ModernListRow(
                item.title,
                item.kind.replaceFirstChar { c -> c.uppercase() },
                badge = "Resume"
            ) { onPlay(item) }
        }
    }
}

@Composable
internal fun SettingsScreen(vm: AppViewModel) {
    val account by vm.account.collectAsState()
    val profile by vm.playbackProfile.collectAsState()
    Heading("Settings", "Playback and provider")

    SettingsCard("Buffer profile", "Choose how quickly Selyro starts and how much it buffers.") {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StreamingProfile.entries.forEach { p ->
                TvButton(
                    p.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = p == profile
                ) { vm.setPlaybackProfile(p) }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsCard("Provider", account?.name ?: "IPTV") {
        Text(
            account?.server.orEmpty(),
            color = Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TvButton("Refresh live") { vm.loadLive(force = true) }
            TvButton("Disconnect") { vm.logout() }
        }
    }
}
