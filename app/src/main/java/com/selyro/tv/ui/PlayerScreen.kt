package com.selyro.tv.ui

import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.selyro.tv.data.AppLanguage
import com.selyro.tv.model.Channel
import com.selyro.tv.model.EpgProgram
import kotlinx.coroutines.delay
import kotlin.math.max

private val PlayerAccent = Color(0xFF6BE4D2)
private val PlayerMuted = Color(0xFFB4C0CC)
private val PlayerPanel = Color(0xE610171E)
private val PlayerPanelStrong = Color(0xF20A1118)

private enum class TrackTab { AUDIO, SUBTITLES }

/** Context used only for Live TV. Movies/series keep the classic seek-first remote mapping. */
data class LivePlayerContext(
    val currentChannelId: String,
    val channels: List<Channel>,
    val epg: List<EpgProgram> = emptyList(),
    val isFavorite: Boolean = false
)

private data class TrackOption(
    val label: String,
    val group: Tracks.Group? = null,
    val trackIndex: Int? = null,
    val selected: Boolean = false,
    val special: String? = null
)

private fun pt(language: AppLanguage, en: String, ar: String): String =
    if (language == AppLanguage.ARABIC) ar else en

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    player: Player,
    language: AppLanguage,
    liveContext: LivePlayerContext? = null,
    onLiveTune: (Channel) -> Unit = {},
    onToggleLiveFavorite: () -> Unit = {},
    onProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val isLive = liveContext != null
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(max(0L, player.currentPosition)) }
    var duration by remember { mutableLongStateOf(normalizedDuration(player)) }
    var buffered by remember { mutableLongStateOf(max(0L, player.bufferedPosition)) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var bufferPercent by remember { mutableIntStateOf(player.bufferedPercentage.coerceIn(0, 100)) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var trackPanelVisible by remember { mutableStateOf(false) }
    var trackTab by remember { mutableStateOf(TrackTab.AUDIO) }
    var trackCursor by remember { mutableIntStateOf(0) }
    var lastPersistAt by remember { mutableLongStateOf(0L) }

    var liveHubVisible by remember { mutableStateOf(false) }
    var liveHubCursor by remember { mutableIntStateOf(0) }
    var channelDrawerVisible by remember { mutableStateOf(false) }
    var liveInfoVisible by remember { mutableStateOf(false) }
    var drawerCursor by remember { mutableIntStateOf(0) }
    var pendingZapIndex by remember { mutableIntStateOf(-1) }
    var zapPreview by remember { mutableStateOf<Channel?>(null) }

    var liveStartupStartedAt by remember(liveContext?.currentChannelId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var liveStartupMs by remember(liveContext?.currentChannelId) { mutableLongStateOf(0L) }
    var liveBufferEvents by remember(liveContext?.currentChannelId) { mutableIntStateOf(0) }
    var livePlaybackError by remember(liveContext?.currentChannelId) { mutableStateOf(false) }
    var lastLivePlaybackState by remember(liveContext?.currentChannelId) { mutableIntStateOf(player.playbackState) }
    var qualityPulse by remember(liveContext?.currentChannelId) { mutableStateOf(isLive) }

    val focusRequester = remember { FocusRequester() }
    val audioOptions = remember(tracksVersion, language) { trackOptions(player, C.TRACK_TYPE_AUDIO, language) }
    val subtitleOptions = remember(tracksVersion, language) { trackOptions(player, C.TRACK_TYPE_TEXT, language) }
    val liveChannels = liveContext?.channels.orEmpty()
    val currentLiveIndex = remember(liveContext?.currentChannelId, liveChannels) {
        liveChannels.indexOfFirst { it.id == liveContext?.currentChannelId }.coerceAtLeast(0)
    }
    val currentLiveChannel = liveChannels.getOrNull(currentLiveIndex)
    val streamGrade = when {
        !isLive -> ConnectionGrade.CHECKING
        livePlaybackError -> ConnectionGrade.OFFLINE
        liveStartupMs == 0L -> ConnectionGrade.CHECKING
        liveBufferEvents >= 3 || liveStartupMs > 7_000L -> ConnectionGrade.WEAK
        liveBufferEvents >= 1 || liveStartupMs > 3_000L -> ConnectionGrade.GOOD
        else -> ConnectionGrade.EXCELLENT
    }

    fun currentOptions(): List<TrackOption> = if (trackTab == TrackTab.AUDIO) audioOptions else subtitleOptions
    fun revealControls() { controlsVisible = true; interactionVersion++ }
    fun closeLiveOverlays() {
        liveHubVisible = false
        channelDrawerVisible = false
        liveInfoVisible = false
        trackPanelVisible = false
        revealControls()
    }

    fun persistProgress(force: Boolean = false) {
        if (isLive) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPersistAt < 5_000L) return
        lastPersistAt = now
        onProgress(max(0L, player.currentPosition), normalizedDuration(player))
    }

    fun seekBy(deltaMs: Long) {
        val rawDuration = player.duration
        if (rawDuration == C.TIME_UNSET || rawDuration <= 0L || !player.isCurrentMediaItemSeekable) {
            revealControls(); return
        }
        val next = (player.currentPosition + deltaMs).coerceIn(0L, rawDuration)
        player.seekTo(next)
        position = next
        seekHint = if (deltaMs > 0L) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        persistProgress(force = true)
        revealControls()
    }

    fun selectTrack(option: TrackOption) {
        val type = if (trackTab == TrackTab.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        when (option.special) {
            "auto" -> builder.setTrackTypeDisabled(type, false)
            "off" -> builder.setTrackTypeDisabled(type, true)
            else -> {
                builder.setTrackTypeDisabled(type, false)
                val group = option.group
                val index = option.trackIndex
                if (group != null && index != null) builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            }
        }
        player.trackSelectionParameters = builder.build()
        trackPanelVisible = false
        if (isLive) liveHubVisible = true
        revealControls()
    }

    fun openTrackPanel(tab: TrackTab) {
        trackTab = tab
        val options = if (tab == TrackTab.AUDIO) audioOptions else subtitleOptions
        trackCursor = optionsIndexForSelected(options)
        trackPanelVisible = true
        liveHubVisible = false
        channelDrawerVisible = false
        liveInfoVisible = false
        revealControls()
    }

    fun queueZap(delta: Int) {
        if (liveChannels.isEmpty()) return
        val base = if (pendingZapIndex in liveChannels.indices) pendingZapIndex else currentLiveIndex
        pendingZapIndex = (base + delta + liveChannels.size) % liveChannels.size
        zapPreview = liveChannels[pendingZapIndex]
        controlsVisible = false
        interactionVersion++
    }

    LaunchedEffect(liveContext?.currentChannelId) {
        if (isLive) {
            liveStartupStartedAt = SystemClock.elapsedRealtime()
            qualityPulse = true
        }
    }

    LaunchedEffect(qualityPulse, liveContext?.currentChannelId) {
        if (isLive && qualityPulse) {
            delay(5_000L)
            qualityPulse = false
        }
    }

    DisposableEffect(player, liveContext?.currentChannelId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                playing = player.isPlaying
                bufferPercent = player.bufferedPercentage.coerceIn(0, 100)
                if (isLive) {
                    if (playbackState == Player.STATE_BUFFERING && lastLivePlaybackState != Player.STATE_BUFFERING) {
                        liveBufferEvents += 1
                        qualityPulse = true
                    }
                    if (playbackState == Player.STATE_READY) {
                        if (liveStartupMs == 0L) {
                            liveStartupMs = (SystemClock.elapsedRealtime() - liveStartupStartedAt).coerceAtLeast(1L)
                        }
                        livePlaybackError = false
                    }
                    lastLivePlaybackState = playbackState
                }
                if (playbackState == Player.STATE_ENDED) persistProgress(force = true)
            }
            override fun onPlayerError(error: PlaybackException) {
                if (isLive) {
                    livePlaybackError = true
                    qualityPulse = true
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (!isLive) revealControls() }
            override fun onTracksChanged(tracks: Tracks) { tracksVersion++ }
        }
        player.addListener(listener)
        onDispose { persistProgress(force = true); player.removeListener(listener) }
    }

    BackHandler {
        when {
            trackPanelVisible -> { trackPanelVisible = false; if (isLive) liveHubVisible = true; revealControls() }
            liveInfoVisible || channelDrawerVisible || liveHubVisible -> closeLiveOverlays()
            else -> { persistProgress(force = true); onBack() }
        }
    }

    LaunchedEffect(player) {
        focusRequester.requestFocus()
        while (true) {
            position = max(0L, player.currentPosition)
            duration = normalizedDuration(player)
            buffered = max(0L, player.bufferedPosition)
            bufferPercent = player.bufferedPercentage.coerceIn(0, 100)
            playing = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            persistProgress()
            delay(350)
        }
    }

    LaunchedEffect(liveContext?.currentChannelId) {
        pendingZapIndex = -1
        if (liveContext != null) {
            drawerCursor = currentLiveIndex
            zapPreview = currentLiveChannel
            delay(1_400)
            if (zapPreview?.id == liveContext.currentChannelId) zapPreview = null
        }
    }

    // Smart Zap: rapid Up/Down presses only tune the final channel after a short debounce.
    LaunchedEffect(pendingZapIndex, liveContext?.currentChannelId) {
        if (isLive && pendingZapIndex in liveChannels.indices && pendingZapIndex != currentLiveIndex) {
            val target = liveChannels[pendingZapIndex]
            delay(220)
            if (pendingZapIndex in liveChannels.indices && liveChannels[pendingZapIndex].id == target.id) onLiveTune(target)
        }
    }

    LaunchedEffect(controlsVisible, interactionVersion, playing, trackPanelVisible, liveHubVisible, channelDrawerVisible, liveInfoVisible) {
        if (controlsVisible && playing && !trackPanelVisible && !liveHubVisible && !channelDrawerVisible && !liveInfoVisible) {
            val version = interactionVersion
            delay(if (isLive) 3_500 else 4_500)
            if (version == interactionVersion) { controlsVisible = false; seekHint = null }
        }
    }

    LaunchedEffect(seekHint) { if (seekHint != null) { delay(1_100); seekHint = null } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (trackPanelVisible) {
                    when (event.key) {
                        Key.DirectionLeft -> { trackTab = TrackTab.AUDIO; trackCursor = optionsIndexForSelected(audioOptions); true }
                        Key.DirectionRight -> { trackTab = TrackTab.SUBTITLES; trackCursor = optionsIndexForSelected(subtitleOptions); true }
                        Key.DirectionUp -> { val list = currentOptions(); if (list.isNotEmpty()) trackCursor = (trackCursor - 1 + list.size) % list.size; true }
                        Key.DirectionDown -> { val list = currentOptions(); if (list.isNotEmpty()) trackCursor = (trackCursor + 1) % list.size; true }
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> { currentOptions().getOrNull(trackCursor)?.let(::selectTrack); true }
                        else -> false
                    }
                } else if (channelDrawerVisible && isLive) {
                    when (event.key) {
                        Key.DirectionUp -> { if (liveChannels.isNotEmpty()) drawerCursor = (drawerCursor - 1 + liveChannels.size) % liveChannels.size; true }
                        Key.DirectionDown -> { if (liveChannels.isNotEmpty()) drawerCursor = (drawerCursor + 1) % liveChannels.size; true }
                        Key.DirectionCenter, Key.Enter -> {
                            liveChannels.getOrNull(drawerCursor)?.let { target -> onLiveTune(target); zapPreview = target }
                            channelDrawerVisible = false; true
                        }
                        Key.DirectionRight -> { channelDrawerVisible = false; revealControls(); true }
                        else -> false
                    }
                } else if (liveInfoVisible && isLive) {
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter -> { liveInfoVisible = false; revealControls(); true }
                        else -> true
                    }
                } else if (liveHubVisible && isLive) {
                    when (event.key) {
                        Key.DirectionLeft -> { liveHubCursor = (liveHubCursor - 1 + 5) % 5; true }
                        Key.DirectionRight -> { liveHubCursor = (liveHubCursor + 1) % 5; true }
                        Key.DirectionUp, Key.DirectionDown -> true
                        Key.DirectionCenter, Key.Enter -> {
                            when (liveHubCursor) {
                                0 -> { channelDrawerVisible = true; drawerCursor = currentLiveIndex; liveHubVisible = false }
                                1 -> openTrackPanel(TrackTab.AUDIO)
                                2 -> openTrackPanel(TrackTab.SUBTITLES)
                                3 -> { onToggleLiveFavorite(); revealControls() }
                                4 -> { liveInfoVisible = true; liveHubVisible = false }
                            }
                            true
                        }
                        else -> false
                    }
                } else if (isLive) {
                    when (event.key) {
                        Key.DirectionUp -> { queueZap(-1); true }
                        Key.DirectionDown -> { queueZap(1); true }
                        Key.DirectionLeft -> { channelDrawerVisible = true; drawerCursor = currentLiveIndex; controlsVisible = false; true }
                        Key.DirectionRight -> { liveInfoVisible = true; controlsVisible = false; true }
                        Key.DirectionCenter, Key.Enter -> { liveHubVisible = true; liveHubCursor = 0; controlsVisible = true; true }
                        Key.MediaPlayPause -> { if (player.isPlaying) player.pause() else player.play(); true }
                        else -> false
                    }
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> { seekBy(-10_000L); true }
                        Key.DirectionRight -> { seekBy(10_000L); true }
                        Key.MediaRewind -> { seekBy(-30_000L); true }
                        Key.MediaFastForward -> { seekBy(30_000L); true }
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                            if (player.isPlaying) player.pause() else player.play(); playing = player.isPlaying; revealControls(); true
                        }
                        Key.DirectionDown -> {
                            revealControls()
                            if (audioOptions.size > 1 || subtitleOptions.size > 1) openTrackPanel(TrackTab.AUDIO)
                            true
                        }
                        Key.DirectionUp -> { revealControls(); true }
                        else -> false
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = player }
        )

        AnimatedVisibility(visible = controlsVisible && !channelDrawerVisible && !liveInfoVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent, Color.Black.copy(alpha = 0.82f))))) {
                Column(Modifier.align(Alignment.TopStart).padding(44.dp)) {
                    Text(currentLiveChannel?.name ?: player.mediaMetadata.title?.toString().orEmpty().ifBlank { pt(language, "Now playing", "قيد التشغيل") }, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (isLive && !currentLiveChannel?.group.isNullOrBlank()) Text(currentLiveChannel?.group.orEmpty(), color = PlayerAccent, fontSize = 13.sp)
                }

                if (seekHint != null) {
                    Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.76f)).padding(horizontal = 28.dp, vertical = 17.dp)) {
                        Text(seekHint.orEmpty(), color = Color.White, fontSize = 25.sp)
                    }
                }

                if (trackPanelVisible) {
                    PlayerTrackPanel(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 52.dp, vertical = 150.dp),
                        language = language, tab = trackTab, options = currentOptions(), cursor = trackCursor
                    )
                }

                if (isLive && liveHubVisible) {
                    LiveControlHub(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 48.dp, vertical = 122.dp),
                        language = language,
                        cursor = liveHubCursor,
                        isFavorite = liveContext?.isFavorite == true,
                        hasAudio = audioOptions.isNotEmpty(),
                        hasSubtitles = subtitleOptions.size > 1
                    )
                }

                if (isLive) {
                    LivePlayerHints(Modifier.align(Alignment.BottomCenter).padding(horizontal = 52.dp, vertical = 34.dp), language)
                } else {
                    ModernPlayerControls(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 52.dp, vertical = 34.dp),
                        language, position, duration, buffered, playing, player.isCurrentMediaItemSeekable,
                        audioOptions.size > 1 || subtitleOptions.size > 1
                    )
                }
            }
        }

        if (isLive && (controlsVisible || qualityPulse || buffering || streamGrade == ConnectionGrade.WEAK || streamGrade == ConnectionGrade.OFFLINE)) {
            StreamQualityBadge(
                Modifier.align(Alignment.TopEnd).padding(44.dp),
                language, streamGrade, liveStartupMs, liveBufferEvents
            )
        }

        if (buffering) {
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${pt(language, "Connecting", "جاري الاتصال")} $bufferPercent%", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(9.dp))
                Box(Modifier.width(180.dp).height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((bufferPercent / 100f).coerceIn(0.04f, 1f)).background(PlayerAccent))
                }
            }
        }

        if (isLive && channelDrawerVisible) {
            LiveChannelDrawer(Modifier.align(Alignment.CenterStart), language, liveChannels, drawerCursor, liveContext?.currentChannelId)
        }

        if (isLive && liveInfoVisible) {
            LiveInfoPanel(
                Modifier.align(Alignment.CenterEnd), language, currentLiveChannel, liveContext?.epg.orEmpty(),
                streamGrade, liveStartupMs, liveBufferEvents
            )
        }

        if (isLive && zapPreview != null && !channelDrawerVisible && !liveInfoVisible && !liveHubVisible) {
            LiveZapToast(Modifier.align(Alignment.Center), language, zapPreview!!)
        }
    }
}

@Composable
private fun LivePlayerHints(modifier: Modifier, language: AppLanguage) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("● LIVE", color = PlayerAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Back  ${pt(language, "Channels", "القنوات")}", color = PlayerMuted, fontSize = 12.sp)
                Text("↑↓  ${pt(language, "Change channel", "تغيير القناة")}", color = Color.White, fontSize = 12.sp)
                Text("←  ${pt(language, "Channel list", "قائمة القنوات")}", color = PlayerMuted, fontSize = 12.sp)
                Text("OK  ${pt(language, "Controls", "التحكم")}", color = Color.White, fontSize = 12.sp)
                Text("→  ${pt(language, "Info", "معلومات")}", color = PlayerMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LiveControlHub(
    modifier: Modifier,
    language: AppLanguage,
    cursor: Int,
    isFavorite: Boolean,
    hasAudio: Boolean,
    hasSubtitles: Boolean
) {
    val labels = listOf(
        "☰  ${pt(language, "Channels", "القنوات")}",
        "♫  ${pt(language, "Audio", "الصوت")}",
        "CC  ${pt(language, "Subtitles", "الترجمة")}",
        "${if (isFavorite) "★" else "☆"}  ${pt(language, "Favorite", "المفضلة")}",
        "ⓘ  ${pt(language, "Info", "معلومات")}" 
    )
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PlayerPanelStrong)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val enabled = when (index) { 1 -> hasAudio; 2 -> hasSubtitles; else -> true }
            val focused = index == cursor
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (focused) PlayerAccent else Color.White.copy(alpha = if (enabled) 0.08f else 0.035f))
                    .padding(horizontal = 10.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (focused) Color.Black else if (enabled) Color.White else PlayerMuted.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LiveChannelDrawer(
    modifier: Modifier,
    language: AppLanguage,
    channels: List<Channel>,
    cursor: Int,
    currentChannelId: String?
) {
    val state = rememberLazyListState()
    LaunchedEffect(cursor) { if (cursor in channels.indices) state.animateScrollToItem(cursor) }
    Column(
        modifier.fillMaxHeight().width(430.dp).background(PlayerPanelStrong)
            .padding(horizontal = 18.dp, vertical = 28.dp)
    ) {
        Text(pt(language, "CHANNELS", "القنوات"), color = PlayerAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(channels, key = { _, item -> item.id }) { index, channel ->
                val focused = index == cursor
                val playing = channel.id == currentChannelId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                        .background(if (focused) PlayerAccent else if (playing) Color(0xFF183A37) else Color.White.copy(alpha = 0.055f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF17232C)), contentAlignment = Alignment.Center) {
                        Text(channelMonogram(channel.name), color = if (focused) Color.Black else PlayerAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, color = if (focused) Color.Black else Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(channel.group, color = if (focused) Color.Black.copy(alpha = .7f) else PlayerMuted, fontSize = 10.sp, maxLines = 1)
                    }
                    if (playing) Text("●", color = if (focused) Color.Black else PlayerAccent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StreamQualityBadge(
    modifier: Modifier,
    language: AppLanguage,
    grade: ConnectionGrade,
    startupMs: Long,
    bufferEvents: Int,
    detailed: Boolean = false
) {
    val color = when (grade) {
        ConnectionGrade.EXCELLENT -> Color(0xFF62E6A7)
        ConnectionGrade.GOOD -> Color(0xFFFFD166)
        ConnectionGrade.WEAK -> Color(0xFFFF9F43)
        ConnectionGrade.OFFLINE -> Color(0xFFFF6B6B)
        ConnectionGrade.CHECKING -> PlayerMuted
    }
    val label = when (grade) {
        ConnectionGrade.EXCELLENT -> pt(language, "Excellent", "ممتاز")
        ConnectionGrade.GOOD -> pt(language, "Good", "جيد")
        ConnectionGrade.WEAK -> pt(language, "Weak", "ضعيف")
        ConnectionGrade.OFFLINE -> pt(language, "Offline", "غير متصل")
        ConnectionGrade.CHECKING -> pt(language, "Checking", "جاري الفحص")
    }
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .72f))
            .border(1.dp, color.copy(alpha = .55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▂▄▆█", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (detailed) {
                val startup = if (startupMs > 0L) "${startupMs}ms" else "—"
                Text(
                    pt(language, "Start $startup • buffers $bufferEvents", "بدء $startup • تقطيع $bufferEvents"),
                    color = PlayerMuted, fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LiveInfoPanel(
    modifier: Modifier,
    language: AppLanguage,
    channel: Channel?,
    epg: List<EpgProgram>,
    grade: ConnectionGrade,
    startupMs: Long,
    bufferEvents: Int
) {
    Column(
        modifier.width(430.dp).fillMaxHeight().background(PlayerPanelStrong).padding(horizontal = 22.dp, vertical = 32.dp)
    ) {
        Text(pt(language, "NOW PLAYING", "القناة الحالية"), color = PlayerAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(channel?.name ?: pt(language, "Live channel", "قناة مباشرة"), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        if (!channel?.group.isNullOrBlank()) Text(channel?.group.orEmpty(), color = PlayerMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        StreamQualityBadge(Modifier, language, grade, startupMs, bufferEvents, detailed = true)
        Spacer(Modifier.height(20.dp))
        Text("EPG", color = PlayerAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (epg.isEmpty()) {
            Text(pt(language, "No guide data available", "لا توجد بيانات للجدول"), color = PlayerMuted, fontSize = 13.sp)
        } else {
            epg.sortedBy { it.start }.take(5).forEach { program ->
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(program.title.ifBlank { pt(language, "Program", "برنامج") }, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    if (!program.description.isNullOrBlank()) Text(program.description.orEmpty(), color = PlayerMuted, fontSize = 11.sp, maxLines = 2)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(pt(language, "Press Back or → to close", "اضغط رجوع أو → للإغلاق"), color = PlayerMuted, fontSize = 11.sp)
    }
}

@Composable
private fun LiveZapToast(modifier: Modifier, language: AppLanguage, channel: Channel) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .84f))
            .border(1.dp, PlayerAccent.copy(alpha = .45f), RoundedCornerShape(18.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17232C)), contentAlignment = Alignment.Center) {
            Text(channelMonogram(channel.name), color = PlayerAccent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(channel.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(pt(language, "Switching channel…", "جاري تبديل القناة…"), color = PlayerMuted, fontSize = 11.sp)
        }
    }
}

private fun channelMonogram(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].firstOrNull() ?: 'S'}${parts[1].firstOrNull() ?: 'T'}".uppercase()
        name.isNotBlank() -> name.take(2).uppercase()
        else -> "TV"
    }
}

@Composable
private fun ModernPlayerControls(
    modifier: Modifier,
    language: AppLanguage,
    position: Long,
    duration: Long,
    buffered: Long,
    playing: Boolean,
    seekable: Boolean,
    hasTrackControls: Boolean
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (seekable) formatPlayerTime(position) else "LIVE", color = Color.White, fontSize = 14.sp)
                Text(if (seekable) formatPlayerTime(duration) else "● LIVE", color = if (seekable) PlayerMuted else PlayerAccent, fontSize = 14.sp)
            }
            Spacer(Modifier.height(9.dp))
            val playedFraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val bufferedFraction = if (duration > 0L) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.16f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bufferedFraction).background(Color.White.copy(alpha = 0.32f)))
                Box(Modifier.fillMaxHeight().fillMaxWidth(playedFraction).background(PlayerAccent))
            }
            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(pt(language, "Back  Exit", "رجوع  خروج"), color = PlayerMuted, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlayerKeyHint("◀", if (seekable) "-10s" else "")
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Text(if (playing) "Ⅱ" else "▶", color = Color.Black, fontSize = 22.sp)
                    }
                    PlayerKeyHint("▶", if (seekable) "+10s" else "")
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text("OK  ${pt(language, "Play / Pause", "تشغيل / إيقاف")}", color = PlayerMuted, fontSize = 12.sp)
                    if (hasTrackControls) {
                        Spacer(Modifier.width(18.dp)); Text("↓  ${pt(language, "Audio / Subtitles", "الصوت / الترجمة")}", color = PlayerMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTrackPanel(modifier: Modifier, language: AppLanguage, tab: TrackTab, options: List<TrackOption>, cursor: Int) {
    val listState = rememberLazyListState()
    LaunchedEffect(cursor, options.size) { if (cursor in options.indices) listState.animateScrollToItem(cursor) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PlayerPanel)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackTabChip(pt(language, "Audio", "الصوت"), tab == TrackTab.AUDIO)
            TrackTabChip(pt(language, "Subtitles", "الترجمة"), tab == TrackTab.SUBTITLES)
            Spacer(Modifier.weight(1f))
            Text(pt(language, "← → switch   ↑ ↓ choose   OK apply", "← → تبديل   ↑ ↓ اختيار   OK تطبيق"), color = PlayerMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (options.isEmpty()) Text(pt(language, "No tracks available", "لا توجد مسارات متاحة"), color = PlayerMuted, fontSize = 13.sp)
        else LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(options) { index, option ->
                val focused = index == cursor
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (focused) PlayerAccent else Color.White.copy(alpha = 0.08f))
                        .border(if (option.selected && !focused) 1.dp else 0.dp, PlayerAccent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.label, color = if (focused) Color.Black else Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    if (option.selected) Text("✓", color = if (focused) Color.Black else PlayerAccent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable private fun TrackTabChip(label: String, selected: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (selected) Color(0xFF244943) else Color.White.copy(alpha = 0.07f)).padding(horizontal = 13.dp, vertical = 7.dp)) {
        Text(label, color = if (selected) PlayerAccent else PlayerMuted, fontSize = 12.sp)
    }
}

@Composable private fun PlayerKeyHint(symbol: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontSize = 14.sp)
        }
        if (label.isNotBlank()) { Spacer(Modifier.width(7.dp)); Text(label, color = PlayerMuted, fontSize = 12.sp) }
    }
}

@OptIn(UnstableApi::class)
private fun trackOptions(player: Player, type: Int, language: AppLanguage): List<TrackOption> {
    val result = mutableListOf<TrackOption>()
    if (type == C.TRACK_TYPE_AUDIO) result += TrackOption(pt(language, "Auto", "تلقائي"), special = "auto")
    else if (type == C.TRACK_TYPE_TEXT) {
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        result += TrackOption(pt(language, "Off", "إيقاف"), selected = disabled, special = "off")
    }
    var ordinal = 1
    player.currentTracks.groups.filter { it.type == type }.forEach { group ->
        repeat(group.length) { index ->
            if (!group.isTrackSupported(index)) return@repeat
            val format = group.getTrackFormat(index)
            val prefix = if (type == C.TRACK_TYPE_AUDIO) pt(language, "Audio", "صوت") else pt(language, "Subtitle", "ترجمة")
            val label = format.label?.takeIf { it.isNotBlank() } ?: format.language?.takeIf { it.isNotBlank() }?.uppercase() ?: "$prefix $ordinal"
            result += TrackOption(label, group, index, group.isTrackSelected(index))
            ordinal++
        }
    }
    if (type == C.TRACK_TYPE_AUDIO && result.none { it.selected && it.special == null } && result.isNotEmpty()) result[0] = result[0].copy(selected = true)
    return result
}

private fun optionsIndexForSelected(options: List<TrackOption>): Int = options.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
private fun normalizedDuration(player: Player): Long = player.duration.let { if (it == C.TIME_UNSET || it < 0L) 0L else it }
private fun formatPlayerTime(valueMs: Long): String {
    val totalSeconds = max(0L, valueMs) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
