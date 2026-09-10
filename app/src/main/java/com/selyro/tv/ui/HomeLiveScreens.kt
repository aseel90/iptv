package com.selyro.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.selyro.tv.model.Channel
import com.selyro.tv.model.EpgProgram

@Composable
internal fun HomeScreen(vm: AppViewModel, go: (Section) -> Unit) {
    val channels by vm.channels.collectAsState()
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val info by vm.providerInfo.collectAsState()
    val account by vm.account.collectAsState()
    val recents by vm.recents.collectAsState()

    Heading("Home", "Welcome back")
    HeroPanel(
        provider = account?.name ?: "Selyro TV",
        status = info?.status ?: "Connected",
        connectionText = info?.let { "${it.activeConnections ?: "—"} / ${it.maxConnections ?: "—"} connections" } ?: "Ready to stream",
        onLive = { go(Section.LIVE) },
        onMovies = { go(Section.MOVIES) }
    )
    Spacer(Modifier.height(20.dp))
    Text("Browse", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DashboardCard("Live TV", channels.size.takeIf { it > 0 }?.toString() ?: "Open", "Channels") { go(Section.LIVE) }
        DashboardCard("Movies", movies.size.takeIf { it > 0 }?.toString() ?: "Browse", "On demand") { go(Section.MOVIES) }
        DashboardCard("Series", series.size.takeIf { it > 0 }?.toString() ?: "Browse", "Episodes") { go(Section.SERIES) }
        DashboardCard("Recent", recents.size.toString(), "Continue watching") { go(Section.RECENT) }
    }
}

@Composable
private fun HeroPanel(
    provider: String,
    status: String,
    connectionText: String,
    onLive: () -> Unit,
    onMovies: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(230.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF102C31), Color(0xFF101D29), Surface)
                )
            )
            .padding(horizontal = 30.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("CONNECTED PROVIDER", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(provider, color = TextPrimary, fontSize = 31.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text("$status  •  $connectionText", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Watch Live", selected = true, onClick = onLive)
                TvButton("Browse Movies", onClick = onMovies)
            }
        }
        Box(
            Modifier.size(142.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            BrandMark(86.dp)
        }
    }
}

@Composable
internal fun LiveScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.channels.collectAsState()
    val epg by vm.epgByChannel.collectAsState()
    val loading by vm.loadingSection.collectAsState()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<Channel?>(null) }
    val groups = remember(all) {
        listOf("All") + all.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(all, query, group) {
        all.asSequence()
            .filter { group == "All" || it.group == group }
            .filter { query.isBlank() || it.name.contains(query, true) }
            .toList()
    }
    LaunchedEffect(selected?.id) { selected?.let(vm::loadEpg) }

    Heading("Live TV", "${all.size} channels")
    TvInput("Search channels", query, compact = true) { query = it }
    Spacer(Modifier.height(14.dp))
    if (loading == "Live TV" && all.isEmpty()) {
        LoadingBox("Loading channels…")
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(
            Modifier.width(190.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(groups) { g -> TvFilterItem(g, selected = group == g) { group = g } }
        }
        LazyColumn(
            Modifier.width(370.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.id }) { channel ->
                ChannelRow(channel, onFocus = { selected = channel }) {
                    onPlay(PlayRequest("live", channel.id, channel.name, channel.url))
                }
            }
        }
        ChannelDetails(
            Modifier.weight(1f),
            selected,
            epg[selected?.id].orEmpty(),
            vm,
            onPlay
        )
    }
}

@Composable
private fun ChannelDetails(
    modifier: Modifier,
    channel: Channel?,
    epg: List<EpgProgram>,
    vm: AppViewModel,
    onPlay: (PlayRequest) -> Unit
) {
    Column(
        modifier.fillMaxHeight().then(surfacePanelModifier(22.dp)).padding(22.dp)
    ) {
        if (channel == null) {
            EmptyPrompt("Select a channel", "Move through the list to preview channel information.")
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceRaised).padding(10.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(5.dp))
                Text(channel.group, color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Play", selected = true) {
                onPlay(PlayRequest("live", channel.id, channel.name, channel.url))
            }
            TvButton(if (vm.isFavorite("live", channel.id)) "Saved" else "Favorite") {
                vm.toggleFavorite("live", channel.id)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Program guide", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (epg.isEmpty()) {
            Text("No guide data available.", color = Muted, fontSize = 13.sp)
        } else {
            epg.take(5).forEachIndexed { index, program ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (index == 0) Accent else Border)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            program.title.ifBlank { "Program" },
                            color = if (index == 0) TextPrimary else Muted,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        if (!program.description.isNullOrBlank()) {
                            Text(program.description.orEmpty(), color = Subtle, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
