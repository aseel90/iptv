package com.selyro.tv.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.selyro.tv.R
import com.selyro.tv.data.AppLanguage
import com.selyro.tv.data.DisplayMode
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
private val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

@Composable private fun tx(en: String, ar: String): String = if (LocalAppLanguage.current == AppLanguage.ARABIC) ar else en

private enum class Section { HOME, LIVE, MOVIES, SERIES, FAVORITES, RECENT, SETTINGS }

@Composable
private fun sectionLabel(section: Section): String = when (section) {
    Section.HOME -> tx("Home", "الرئيسية")
    Section.LIVE -> tx("Live TV", "القنوات")
    Section.MOVIES -> tx("Movies", "الأفلام")
    Section.SERIES -> tx("Series", "المسلسلات")
    Section.FAVORITES -> tx("Favorites", "المفضلة")
    Section.RECENT -> tx("Recent", "الأخيرة")
    Section.SETTINGS -> tx("Settings", "الإعدادات")
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
            val result = UpdateManager.download(context, info) { percent -> updateStatus = UpdateStatus.Downloading(percent) }
            result.onSuccess { file ->
                updateStatus = UpdateStatus.Ready(info, file)
                UpdateManager.install(context, file)
            }.onFailure { updateStatus = UpdateStatus.Error(it.message ?: "Update download failed") }
        }
    }

    LaunchedEffect(Unit) { checkUpdates() }
    DisposableEffect(Unit) { onDispose { playback?.release(); playback = null } }
    LaunchedEffect(profile) { playback?.setProfile(profile) }

    val language by vm.language.collectAsState()
    val direction = if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides direction, LocalAppLanguage provides language) {
        MaterialTheme {
            Box(Modifier.fillMaxSize().background(Bg)) {
                val activePlayback = playback
                if (playing != null && activePlayback != null) {
                    PlayerScreen(player = activePlayback.player) { activePlayback.stop(); playing = null }
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
                UpdateOverlay(updateStatus, ::checkUpdates, ::beginUpdate, { info, file ->
                    if (UpdateManager.needsInstallPermission(context)) UpdateManager.openInstallPermission(context) else UpdateManager.install(context, file)
                }, Modifier.align(Alignment.TopEnd).padding(18.dp))
            }
        }
    }
}

@Composable
private fun UpdateOverlay(status: UpdateStatus, onCheck: () -> Unit, onUpdate: (UpdateInfo) -> Unit, onInstall: (UpdateInfo, java.io.File) -> Unit, modifier: Modifier = Modifier) {
    when (status) {
        is UpdateStatus.Available -> Column(modifier.width(360.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF14242B)).border(1.dp, Accent, RoundedCornerShape(14.dp)).padding(14.dp)) {
            Text(tx("Update available", "يوجد تحديث"), color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Selyro TV ${status.info.versionName}", color = Color.White, fontSize = 14.sp)
            if (status.info.notes.isNotBlank()) Text(status.info.notes, color = Muted, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.height(9.dp)); TvButton(tx("UPDATE NOW", "تحديث الآن"), selected = true, modifier = Modifier.fillMaxWidth()) { onUpdate(status.info) }
        }
        is UpdateStatus.Downloading -> Column(modifier.width(300.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14242B)).padding(14.dp)) { Text(tx("Downloading update…", "جاري تنزيل التحديث…") + " ${status.percent}%", color = Color.White, fontSize = 14.sp) }
        is UpdateStatus.Ready -> Column(modifier.width(320.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14242B)).padding(14.dp)) {
            Text(tx("Update downloaded", "تم تنزيل التحديث"), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); TvButton(tx("INSTALL UPDATE", "تثبيت التحديث"), selected = true, modifier = Modifier.fillMaxWidth()) { onInstall(status.info, status.file) }
        }
        is UpdateStatus.Error -> Column(modifier.width(360.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A2022)).padding(14.dp)) { Text(status.message, color = Color.White, fontSize = 13.sp); Spacer(Modifier.height(8.dp)); TvButton(tx("RETRY", "إعادة المحاولة")) { onCheck() } }
        else -> Unit
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel) {
    val loading by vm.loading.collectAsState(); val error by vm.error.collectAsState(); val adding by vm.addingAccount.collectAsState(); val context = LocalContext.current
    var showExit by remember { mutableStateOf(false) }; var type by remember { mutableStateOf(SourceType.XTREAM) }; var name by remember { mutableStateOf("My IPTV") }; var server by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    BackHandler { if (adding) vm.cancelAddAccount() else showExit = true }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight().background(Rail).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.selyro_tv_icon), "Selyro TV", Modifier.size(118.dp)); Spacer(Modifier.height(10.dp)); Text("SELYRO TV", color = Accent, fontSize = 27.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(tx("Fast IPTV for Android TV", "مشغل IPTV سريع للتلفاز"), color = Color.White, fontSize = 17.sp); Spacer(Modifier.height(7.dp)); Text(tx("Optimized for remote control and low-power TV sticks.", "مصمم للتحكم بالريموت وأجهزة التلفاز منخفضة الموارد."), color = Muted, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 40.dp, vertical = 20.dp), verticalArrangement = Arrangement.Center) {
            Text(tx(if (adding) "Add server" else "Connect a provider", if (adding) "إضافة سيرفر" else "ربط مزود الخدمة"), color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TvButton("Xtream Codes", type == SourceType.XTREAM) { type = SourceType.XTREAM }; TvButton("M3U URL", type == SourceType.M3U) { type = SourceType.M3U } }; Spacer(Modifier.height(10.dp))
            TvInput(tx("Playlist name", "اسم السيرفر"), name) { name = it }; Spacer(Modifier.height(7.dp)); TvInput(if (type == SourceType.XTREAM) tx("Server URL", "رابط السيرفر") else tx("M3U playlist URL", "رابط قائمة M3U"), server) { server = it }
            if (type == SourceType.XTREAM) { Spacer(Modifier.height(7.dp)); TvInput(tx("Username", "اسم المستخدم"), username) { username = it }; Spacer(Modifier.height(7.dp)); TvInput(tx("Password", "كلمة المرور"), password, true) { password = it } }
            if (!error.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(error.orEmpty(), color = Danger, fontSize = 13.sp, maxLines = 2) }
            Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (adding) TvButton(tx("CANCEL", "إلغاء"), modifier = Modifier.weight(.35f)) { vm.cancelAddAccount() }
                TvButton(if (loading) tx("CONNECTING…", "جاري الاتصال…") else tx("CONNECT", "اتصال"), true, !loading && server.isNotBlank(), Modifier.weight(1f)) { vm.login(PlaylistAccount(name.ifBlank { "My IPTV" }, server, username, password, type)) }
            }
        }
    }
    if (showExit) ExitConfirmDialog({ showExit = false }) { (context as? Activity)?.finish() }
}

@Composable
private fun MainShell(vm: AppViewModel, updateStatus: UpdateStatus, onCheckUpdates: () -> Unit, onUpdate: (UpdateInfo) -> Unit, onPlay: (PlayRequest) -> Unit) {
    var section by remember { mutableStateOf(Section.HOME) }; val error by vm.error.collectAsState(); val context = LocalContext.current; var showExit by remember { mutableStateOf(false) }
    BackHandler { if (section != Section.HOME) section = Section.HOME else showExit = true }
    LaunchedEffect(section) { when (section) { Section.LIVE -> vm.loadLive(); Section.MOVIES -> vm.ensureMovies(); Section.SERIES -> vm.ensureSeries(); else -> Unit } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 1100.dp; val railWidth = if (compact) 170.dp else 208.dp; val contentPadding = if (compact) 16.dp else 26.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(railWidth).fillMaxHeight().background(Rail).padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.selyro_tv_icon), "Selyro TV", Modifier.size(if (compact) 62.dp else 74.dp)); Text("SELYRO TV", color = Accent, fontSize = if (compact) 17.sp else 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(if (compact) 5.dp else 9.dp)); Section.entries.forEach { item -> TvNavItem(sectionLabel(item), section == item) { section = item } }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(contentPadding)) {
                if (!error.isNullOrBlank()) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A2022)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(error.orEmpty(), color = Color.White, modifier = Modifier.weight(1f), maxLines = 2); Spacer(Modifier.width(8.dp)); TvButton(tx("Dismiss", "إغلاق")) { vm.clearError() } }; Spacer(Modifier.height(10.dp)) }
                when (section) { Section.HOME -> HomeScreen(vm) { section = it }; Section.LIVE -> LiveScreen(vm, onPlay); Section.MOVIES -> MoviesScreen(vm, onPlay); Section.SERIES -> SeriesScreen(vm, onPlay); Section.FAVORITES -> FavoritesScreen(vm, onPlay); Section.RECENT -> RecentScreen(vm, onPlay); Section.SETTINGS -> SettingsScreen(vm, updateStatus, onCheckUpdates, onUpdate) }
            }
        }
    }
    if (showExit) ExitConfirmDialog({ showExit = false }) { (context as? Activity)?.finish() }
}

@Composable private fun HomeScreen(vm: AppViewModel, go: (Section) -> Unit) {
    val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val series by vm.series.collectAsState(); val info by vm.providerInfo.collectAsState(); val account by vm.account.collectAsState()
    Heading(tx("Home", "الرئيسية"), account?.name ?: "Selyro TV"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { DashboardCard(tx("Live TV", "القنوات"), channels.size.toString(), Modifier.weight(1f)) { go(Section.LIVE) }; DashboardCard(tx("Movies", "الأفلام"), if (movies.isEmpty()) tx("Open to load", "افتح للتحميل") else movies.size.toString(), Modifier.weight(1f)) { go(Section.MOVIES) }; DashboardCard(tx("Series", "المسلسلات"), if (series.isEmpty()) tx("Open to load", "افتح للتحميل") else series.size.toString(), Modifier.weight(1f)) { go(Section.SERIES) } }; Spacer(Modifier.height(18.dp)); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(18.dp)) { Text(tx("Provider", "السيرفر الحالي"), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(account?.server.orEmpty(), color = Color.White, fontSize = 16.sp, maxLines = 1); if (info != null) { Spacer(Modifier.height(8.dp)); Text(tx("Status", "الحالة") + ": ${info?.status ?: tx("Connected", "متصل")}   •   " + tx("Active", "الاتصالات") + ": ${info?.activeConnections ?: "—"}/${info?.maxConnections ?: "—"}", color = Muted, fontSize = 13.sp) } }
}

@Composable private fun LiveScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.channels.collectAsState(); val epg by vm.epgByChannel.collectAsState(); val loading by vm.loadingSection.collectAsState(); val mode by vm.displayMode.collectAsState(); var query by remember { mutableStateOf("") }; var group by remember { mutableStateOf("All") }; var selected by remember { mutableStateOf<Channel?>(null) }; val groups = remember(all) { listOf("All") + all.map { it.group.ifBlank { "Other" } }.distinct().sorted() }; val filtered = remember(all, query, group) { all.asSequence().filter { group == "All" || it.group == group }.filter { query.isBlank() || it.name.contains(query, true) }.toList() }; LaunchedEffect(selected?.id) { selected?.let(vm::loadEpg) }
    Heading(tx("Live TV", "القنوات المباشرة"), "${all.size} ${tx("channels", "قناة")}"); TvInput(tx("Search channels", "بحث في القنوات"), query) { query = it }; Spacer(Modifier.height(12.dp)); if (loading == "Live TV" && all.isEmpty()) { LoadingBox(tx("Loading channels…", "جاري تحميل القنوات…")); return }
    BoxWithConstraints(Modifier.fillMaxSize()) { val showDetails = maxWidth >= 820.dp && mode == DisplayMode.LIST; val categoryWidth = if (maxWidth < 850.dp) 145.dp else 180.dp; Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LazyColumn(Modifier.width(categoryWidth), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(groups) { g -> val label = if (g == "All") tx("All", "الكل") else g; val count = if (g == "All") all.size else all.count { it.group == g }; TvNavItem("$label ($count)", group == g) { group = g; selected = null } } }; if (mode == DisplayMode.GRID) { LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { gridItems(filtered, key = { it.id }) { channel -> ChannelGridCard(channel, { selected = channel }) { onPlay(PlayRequest("live", channel.id, channel.name, channel.url)) } } } } else { LazyColumn(Modifier.weight(if (showDetails) 1.15f else 1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(filtered, key = { it.id }) { channel -> TvListItem(channel.name, channel.group, { selected = channel }) { onPlay(PlayRequest("live", channel.id, channel.name, channel.url)) } } }; if (showDetails) ChannelDetails(Modifier.weight(.85f), selected, epg[selected?.id].orEmpty(), vm, onPlay) } } }
}

@Composable private fun ChannelDetails(modifier: Modifier, channel: Channel?, epg: List<EpgProgram>, vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { Column(modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(18.dp)) { if (channel == null) { Text(tx("Select a channel", "اختر قناة"), color = Muted); return@Column }; AsyncImage(channel.logo, null, Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Fit); Spacer(Modifier.height(12.dp)); Text(channel.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2); Text(channel.group, color = Muted, maxLines = 1); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvButton(tx("Play", "تشغيل"), true) { onPlay(PlayRequest("live", channel.id, channel.name, channel.url)) }; TvButton(if (vm.isFavorite("live", channel.id)) tx("★ Saved", "★ محفوظة") else tx("☆ Favorite", "☆ مفضلة")) { vm.toggleFavorite("live", channel.id) } }; Spacer(Modifier.height(16.dp)); Text("EPG", color = Accent, fontWeight = FontWeight.SemiBold); if (epg.isEmpty()) Text(tx("No guide data", "لا توجد بيانات للجدول"), color = Muted) else epg.take(4).forEach { p -> Spacer(Modifier.height(7.dp)); Text(if (p.title.isBlank()) tx("Program", "برنامج") else p.title, color = Color.White, fontSize = 14.sp, maxLines = 1) } } }

@Composable private fun MoviesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.movies.collectAsState(); val loading by vm.loadingSection.collectAsState(); val mode by vm.displayMode.collectAsState(); var query by remember { mutableStateOf("") }; var category by remember { mutableStateOf("All") }; var selected by remember { mutableStateOf<VodItem?>(null) }; val categories = remember(all) { all.map { it.category.ifBlank { "Other" } }.distinct().sorted() }; LaunchedEffect(categories) { if (categories.isNotEmpty() && category == "All") category = categories.first() }; val filtered = remember(all, query, category) { all.filter { (category == "All" || it.category == category) && (query.isBlank() || it.name.contains(query, true)) } }
    Heading(tx("Movies", "الأفلام"), if (all.isEmpty()) "Xtream VOD" else "${all.size} ${tx("movies", "فيلم")} • ${categories.size} ${tx("categories", "تصنيف")}"); TvInput(tx("Search", "بحث") + " ${if (category == "All") tx("movies", "في الأفلام") else category}", query) { query = it }; Spacer(Modifier.height(12.dp)); if (loading == "Movies" && all.isEmpty()) { LoadingBox(tx("Loading movies…", "جاري تحميل الأفلام…")); return }
    BoxWithConstraints(Modifier.fillMaxSize()) { val showDetails = maxWidth >= 880.dp && mode == DisplayMode.LIST; val categoryWidth = if (maxWidth < 850.dp) 150.dp else 185.dp; Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LazyColumn(Modifier.width(categoryWidth), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(listOf("All") + categories) { c -> val count = if (c == "All") all.size else all.count { it.category == c }; TvNavItem("${if (c == "All") tx("All", "الكل") else c} ($count)", category == c) { category = c; selected = null } } }; if (mode == DisplayMode.GRID) { LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { gridItems(filtered, key = { it.id }) { movie -> MediaGridCard(movie.name, movie.poster, movie.year, { selected = movie }) { onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) } } } } else { LazyColumn(Modifier.weight(if (showDetails) 1.12f else 1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(filtered, key = { it.id }) { movie -> TvListItem(movie.name, listOfNotNull(movie.year, movie.category).filter { it.isNotBlank() }.joinToString(" • "), { selected = movie }) { onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) } } }; if (showDetails) MediaDetails(Modifier.weight(.88f), selected?.name, selected?.poster, selected?.plot, selected?.rating) { selected?.let { movie -> onPlay(PlayRequest("movie", movie.id, movie.name, movie.streamUrl)) } } } } }
}

@Composable private fun SeriesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) {
    val all by vm.series.collectAsState(); val details by vm.selectedSeriesDetails.collectAsState(); val loading by vm.loadingSection.collectAsState(); val mode by vm.displayMode.collectAsState(); var query by remember { mutableStateOf("") }; var category by remember { mutableStateOf("All") }; var selected by remember { mutableStateOf<SeriesItem?>(null) }; var openedSeriesId by remember { mutableStateOf<String?>(null) }; val categories = remember(all) { all.map { it.category.ifBlank { "Other" } }.distinct().sorted() }; LaunchedEffect(categories) { if (categories.isNotEmpty() && category == "All") category = categories.first() }; val filtered = remember(all, query, category) { all.filter { (category == "All" || it.category == category) && (query.isBlank() || it.name.contains(query, true)) } }
    val openedSeries = selected?.takeIf { it.id == openedSeriesId }
    if (openedSeries != null) {
        BackHandler { openedSeriesId = null; selected = null; vm.clearSeriesDetails() }
        Heading(openedSeries.name, listOfNotNull(openedSeries.year, openedSeries.category).filter { it.isNotBlank() }.joinToString(" • "))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(openedSeries.poster, openedSeries.name, Modifier.width(120.dp).height(170.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                if (!openedSeries.plot.isNullOrBlank()) Text(openedSeries.plot.orEmpty(), color = Muted, fontSize = 13.sp, maxLines = 5)
                Spacer(Modifier.height(10.dp))
                TvButton(tx("BACK TO SERIES", "العودة للمسلسلات"), true) { openedSeriesId = null; selected = null; vm.clearSeriesDetails() }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(tx("Episodes", "الحلقات"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val openedEpisodes = details?.takeIf { it.series.id == openedSeries.id }?.episodes.orEmpty()
        when {
            loading == "Episodes" -> LoadingBox(tx("Loading episodes…", "جاري تحميل الحلقات…"))
            openedEpisodes.isEmpty() -> Column { Text(tx("No episodes returned", "لم يتم العثور على حلقات"), color = Muted); Spacer(Modifier.height(10.dp)); TvButton(tx("RETRY", "إعادة المحاولة"), true) { vm.loadSeriesDetails(openedSeries) } }
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(openedEpisodes, key = { it.id }) { ep -> TvListItem("S${ep.season} E${ep.episode}  ${ep.title}", ep.duration.orEmpty()) { onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl)) } } }
        }
        return
    }

    Heading(tx("Series", "المسلسلات"), if (all.isEmpty()) tx("Xtream series", "مسلسلات Xtream") else "${all.size} ${tx("series", "مسلسل")} • ${categories.size} ${tx("categories", "تصنيف")}"); TvInput(tx("Search series", "بحث في المسلسلات"), query) { query = it }; Spacer(Modifier.height(12.dp)); if (loading == "Series" && all.isEmpty()) { LoadingBox(tx("Loading series…", "جاري تحميل المسلسلات…")); return }
    BoxWithConstraints(Modifier.fillMaxSize()) { val showEpisodes = maxWidth >= 880.dp && mode == DisplayMode.LIST; val categoryWidth = if (maxWidth < 850.dp) 150.dp else 185.dp; Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LazyColumn(Modifier.width(categoryWidth), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(listOf("All") + categories) { c -> val count = if (c == "All") all.size else all.count { it.category == c }; TvNavItem("${if (c == "All") tx("All", "الكل") else c} ($count)", category == c) { category = c; selected = null; vm.clearSeriesDetails() } } }; if (mode == DisplayMode.GRID) { LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { gridItems(filtered, key = { it.id }) { item -> MediaGridCard(item.name, item.poster, item.year, { selected = item }) { selected = item; openedSeriesId = item.id; vm.loadSeriesDetails(item) } } } } else { LazyColumn(Modifier.weight(if (showEpisodes) 1.05f else 1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(filtered, key = { it.id }) { item -> TvListItem(item.name, item.category, { selected = item }) { selected = item; openedSeriesId = item.id; vm.loadSeriesDetails(item) } } }; if (showEpisodes) { Column(Modifier.weight(.95f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) { val current = selected; if (current == null) { Text(tx("Select a series", "اختر مسلسلًا"), color = Muted); return@Column }; Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) { AsyncImage(current.poster, null, Modifier.width(82.dp).height(116.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Crop); Column(Modifier.weight(1f)) { Text(current.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2); Text(listOfNotNull(current.year, current.category).filter { it.isNotBlank() }.joinToString(" • "), color = Accent, fontSize = 12.sp, maxLines = 1); if (!current.plot.isNullOrBlank()) { Spacer(Modifier.height(5.dp)); Text(current.plot.orEmpty(), color = Muted, fontSize = 12.sp, maxLines = 3) } } }; Spacer(Modifier.height(9.dp)); TvButton(tx("LOAD EPISODES", "تحميل الحلقات"), true, modifier = Modifier.fillMaxWidth()) { vm.loadSeriesDetails(current) }; Spacer(Modifier.height(9.dp)); if (loading == "Episodes") Text(tx("Loading episodes…", "جاري تحميل الحلقات…"), color = Accent); val episodes = details?.takeIf { it.series.id == current.id }?.episodes.orEmpty(); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(episodes, key = { it.id }) { ep -> TvListItem("S${ep.season} E${ep.episode}  ${ep.title}", ep.duration.orEmpty()) { onPlay(PlayRequest("episode", ep.id, ep.title, ep.streamUrl)) } } } } } } } }
}

@Composable private fun FavoritesScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val favorites by vm.favorites.collectAsState(); val c = channels.filter { "live:${it.id}" in favorites }; val m = movies.filter { "movie:${it.id}" in favorites }; Heading(tx("Favorites", "المفضلة"), "${c.size + m.size} ${tx("saved", "محفوظ")}"); LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) { items(c, key = { "c-${it.id}" }) { item -> TvListItem(item.name, tx("Live", "قناة") + " • ${item.group}") { onPlay(PlayRequest("live", item.id, item.name, item.url)) } }; items(m, key = { "m-${it.id}" }) { item -> TvListItem(item.name, tx("Movie", "فيلم") + " • ${item.category}") { onPlay(PlayRequest("movie", item.id, item.name, item.streamUrl)) } } } }

@Composable private fun RecentScreen(vm: AppViewModel, onPlay: (PlayRequest) -> Unit) { val channels by vm.channels.collectAsState(); val movies by vm.movies.collectAsState(); val recents by vm.recents.collectAsState(); val lookup = remember(channels, movies) { buildMap<String, PlayRequest> { channels.forEach { put("live:${it.id}", PlayRequest("live", it.id, it.name, it.url)) }; movies.forEach { put("movie:${it.id}", PlayRequest("movie", it.id, it.name, it.streamUrl)) } } }; Heading(tx("Recent", "المشاهدة الأخيرة"), tx("Continue where you left off", "تابع من حيث توقفت")); LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) { items(recents.mapNotNull { lookup[it] }, key = { "${it.kind}-${it.id}" }) { item -> TvListItem(item.title, item.kind.replaceFirstChar { c -> c.uppercase() }) { onPlay(item) } } } }

@Composable private fun SettingsScreen(vm: AppViewModel, updateStatus: UpdateStatus, onCheckUpdates: () -> Unit, onUpdate: (UpdateInfo) -> Unit) {
    val account by vm.account.collectAsState(); val accounts by vm.accounts.collectAsState(); val profile by vm.playbackProfile.collectAsState(); val language by vm.language.collectAsState(); val displayMode by vm.displayMode.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading(tx("Settings", "الإعدادات"), tx("Playback, servers, language, display and updates", "التشغيل والسيرفرات واللغة والعرض والتحديثات")) }
        item { SettingsCard(tx("Language", "اللغة"), tx("Full interface direction changes automatically", "يتغير اتجاه الواجهة بالكامل تلقائيًا")) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvButton("English", language == AppLanguage.ENGLISH) { vm.setLanguage(AppLanguage.ENGLISH) }; TvButton("العربية", language == AppLanguage.ARABIC) { vm.setLanguage(AppLanguage.ARABIC) } } } }
        item { SettingsCard(tx("Display", "طريقة العرض"), tx("Choose how channels, movies and series are shown", "اختر طريقة عرض القنوات والأفلام والمسلسلات")) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvButton(tx("List", "قائمة"), displayMode == DisplayMode.LIST) { vm.setDisplayMode(DisplayMode.LIST) }; TvButton(tx("Grid", "شبكة"), displayMode == DisplayMode.GRID) { vm.setDisplayMode(DisplayMode.GRID) } } } }
        item { SettingsCard(tx("Playback", "التشغيل"), tx("Streaming buffer profile", "إعدادات سلاسة البث")) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StreamingProfile.entries.forEach { p -> TvButton(p.name.lowercase().replaceFirstChar { it.uppercase() }, p == profile) { vm.setPlaybackProfile(p) } } } } }
        item { SettingsCard(tx("Servers", "السيرفرات"), tx("Switch without keeping multiple libraries in memory", "بدّل بين السيرفرات بدون تحميل أكثر من مكتبة في الذاكرة")) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { saved -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF17212A)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(saved.name.ifBlank { saved.server }, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(saved.server, color = Muted, fontSize = 11.sp, maxLines = 1) }; Spacer(Modifier.width(8.dp)); TvButton(if (saved == account) tx("ACTIVE", "نشط") else tx("USE", "استخدام"), saved == account, saved != account) { vm.switchAccount(saved) }; if (accounts.size > 1) { Spacer(Modifier.width(6.dp)); TvButton(tx("REMOVE", "حذف")) { vm.removeAccount(saved) } } } }; TvButton(tx("+ ADD SERVER", "+ إضافة سيرفر"), true) { vm.beginAddAccount() }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvButton(tx("Refresh live", "تحديث القنوات")) { vm.loadLive(true) }; TvButton(tx("Clear all servers", "حذف كل السيرفرات")) { vm.logout() } } } } }
        item { SettingsCard(tx("Updates", "التحديثات"), tx("Selyro TV update channel", "قناة تحديث Selyro TV")) { when (updateStatus) { is UpdateStatus.Available -> TvButton(tx("UPDATE TO", "تحديث إلى") + " ${updateStatus.info.versionName}", true) { onUpdate(updateStatus.info) }; is UpdateStatus.Checking -> Text(tx("Checking for updates…", "جاري البحث عن تحديثات…"), color = Muted); is UpdateStatus.Downloading -> Text(tx("Downloading update…", "جاري تنزيل التحديث…") + " ${updateStatus.percent}%", color = Accent); is UpdateStatus.UpToDate -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(tx("Selyro TV is up to date", "التطبيق محدث"), color = Accent); TvButton(tx("CHECK AGAIN", "تحقق مجددًا"), onClick = onCheckUpdates) }; else -> TvButton(tx("CHECK FOR UPDATES", "البحث عن تحديثات"), onClick = onCheckUpdates) } } }
        item { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF132129)).border(1.dp, Color(0xFF25433F), RoundedCornerShape(14.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Image(painterResource(R.drawable.selyro_tv_icon), "Selyro TV", Modifier.size(74.dp)); Spacer(Modifier.width(14.dp)); Column { Text(tx("About Selyro TV", "حول Selyro TV"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(tx("Developed by", "تطوير"), color = Muted, fontSize = 12.sp); Text("aseel salah", color = Accent, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(tx("Version", "الإصدار") + " ${com.selyro.tv.BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp) } } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun SettingsCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(16.dp)) { Text(title, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp); Spacer(Modifier.height(9.dp)); content() } }

@Composable private fun ExitConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) { Dialog(onDismissRequest = onDismiss) { Column(Modifier.widthIn(min = 340.dp, max = 440.dp).clip(RoundedCornerShape(18.dp)).background(Panel).border(1.dp, Color(0xFF2B3C49), RoundedCornerShape(18.dp)).padding(22.dp)) { Text(tx("Exit Selyro TV?", "الخروج من Selyro TV؟"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); Text(tx("Are you sure you want to close the app?", "هل أنت متأكد من إغلاق التطبيق؟"), color = Muted, fontSize = 13.sp); Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { TvButton(tx("CANCEL", "إلغاء"), true, modifier = Modifier.weight(1f), onClick = onDismiss); TvButton(tx("EXIT", "خروج"), modifier = Modifier.weight(1f), onClick = onConfirm) } } } }

@Composable private fun MediaGridCard(title: String, image: String?, meta: String?, onFocus: (() -> Unit)? = null, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (focused) Focus else Panel).border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF1C2833), RoundedCornerShape(13.dp)).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus?.invoke() }.clickable(onClick = onClick).focusable().padding(7.dp)) { AsyncImage(image, title, Modifier.fillMaxWidth().aspectRatio(.70f).clip(RoundedCornerShape(9.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Crop); Spacer(Modifier.height(7.dp)); Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2); if (!meta.isNullOrBlank()) Text(meta, color = Muted, fontSize = 10.sp, maxLines = 1) } }
@Composable private fun ChannelGridCard(channel: Channel, onFocus: (() -> Unit)? = null, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Column(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(13.dp)).background(if (focused) Focus else Panel).border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF1C2833), RoundedCornerShape(13.dp)).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus?.invoke() }.clickable(onClick = onClick).focusable().padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { AsyncImage(channel.logo, channel.name, Modifier.size(64.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Fit); Spacer(Modifier.height(7.dp)); Text(channel.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2) } }
@Composable private fun Heading(title: String, subtitle: String) { Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1); Spacer(Modifier.height(14.dp)) }
@Composable private fun DashboardCard(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Column(modifier.height(116.dp).clip(RoundedCornerShape(16.dp)).background(if (focused) Focus else Panel).border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF1C2833), RoundedCornerShape(16.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(15.dp), verticalArrangement = Arrangement.Center) { Text(title, color = if (focused) Color.White else Muted, fontSize = 14.sp); Spacer(Modifier.height(5.dp)); Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }
@Composable private fun TvNavItem(label: String, selected: Boolean, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (focused) Focus else if (selected) Color(0xFF17242E) else Color.Transparent).border(if (focused) 1.dp else 0.dp, Accent, RoundedCornerShape(10.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 11.dp, vertical = 9.dp)) { Text(label, color = if (focused || selected) Color.White else Muted, fontSize = 14.sp, maxLines = 2) } }
@Composable private fun TvListItem(title: String, subtitle: String = "", onFocus: (() -> Unit)? = null, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (focused) Focus else Panel).border(if (focused) 1.dp else 0.dp, Accent, RoundedCornerShape(11.dp)).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus?.invoke() }.clickable(onClick = onClick).focusable().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 15.sp, maxLines = 1); if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1) } } }
@Composable private fun TvButton(label: String, selected: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) { var focused by remember { mutableStateOf(false) }; val background = when { !enabled -> Color(0xFF171C22); focused -> Accent; selected -> Color(0xFF275A54); else -> Panel }; Box(modifier.clip(RoundedCornerShape(10.dp)).background(background).onFocusChanged { focused = it.isFocused }.clickable(enabled = enabled, onClick = onClick).focusable(enabled).padding(horizontal = 17.dp, vertical = 11.dp)) { Text(label, color = if (focused) Color.Black else if (enabled) Color.White else Color.DarkGray, fontWeight = FontWeight.SemiBold) } }
@Composable private fun TvInput(label: String, value: String, password: Boolean = false, onValueChange: (String) -> Unit) { var focused by remember { mutableStateOf(false) }; Column { Text(label, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(4.dp)); BasicTextField(value, onValueChange, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), singleLine = true, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(Panel).border(if (focused) 2.dp else 1.dp, if (focused) Accent else Color(0xFF26313C), RoundedCornerShape(10.dp)).onFocusChanged { focused = it.isFocused }.padding(horizontal = 13.dp, vertical = 10.dp)) } }
@Composable private fun LoadingBox(text: String) { Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Panel), contentAlignment = Alignment.Center) { Text(text, color = Accent, fontSize = 18.sp) } }
@Composable private fun MediaDetails(modifier: Modifier, title: String?, image: String?, plot: String?, rating: String?, onPlay: () -> Unit) { Column(modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Panel).padding(20.dp)) { if (title == null) { Text(tx("Select a title", "اختر محتوى"), color = Muted); return@Column }; AsyncImage(image, null, Modifier.width(150.dp).height(210.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF19232D)), contentScale = ContentScale.Crop); Spacer(Modifier.height(12.dp)); Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); if (!rating.isNullOrBlank()) Text(tx("Rating", "التقييم") + " $rating", color = Accent); if (!plot.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(plot, color = Muted, maxLines = 6) }; Spacer(Modifier.height(16.dp)); TvButton(tx("Play", "تشغيل"), true, onClick = onPlay) } }
