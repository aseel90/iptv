package com.selyro.tv.ui

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.max

private val PlayerAccent = Color(0xFF6BE4D2)
private val PlayerMuted = Color(0xFFB4C0CC)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(player: Player, onBack: () -> Unit) {
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(max(0L, player.currentPosition)) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(max(0L, player.bufferedPosition)) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var bufferPercent by remember { mutableIntStateOf(player.bufferedPercentage.coerceIn(0, 100)) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun revealControls() {
        controlsVisible = true
        interactionVersion++
    }

    fun seekBy(deltaMs: Long) {
        val rawDuration = player.duration
        if (rawDuration == C.TIME_UNSET || rawDuration <= 0L || !player.isCurrentMediaItemSeekable) {
            revealControls()
            return
        }
        val next = (player.currentPosition + deltaMs).coerceIn(0L, rawDuration)
        player.seekTo(next)
        position = next
        seekHint = if (deltaMs > 0L) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        revealControls()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                playing = player.isPlaying
                bufferPercent = player.bufferedPercentage.coerceIn(0, 100)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                revealControls()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    BackHandler {
        player.stop()
        onBack()
    }

    LaunchedEffect(player) {
        focusRequester.requestFocus()
        while (true) {
            position = max(0L, player.currentPosition)
            val rawDuration = player.duration
            duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0L) 0L else rawDuration
            buffered = max(0L, player.bufferedPosition)
            bufferPercent = player.bufferedPercentage.coerceIn(0, 100)
            playing = player.isPlaying
            buffering = player.playbackState == Player.STATE_BUFFERING
            delay(350)
        }
    }

    LaunchedEffect(controlsVisible, interactionVersion, playing) {
        if (controlsVisible && playing) {
            val version = interactionVersion
            delay(4000)
            if (version == interactionVersion) {
                controlsVisible = false
                seekHint = null
            }
        }
    }

    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(1100)
            seekHint = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-10_000L); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(10_000L); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30_000L); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000L); true }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        playing = player.isPlaying
                        revealControls()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> { revealControls(); true }
                    else -> false
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
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = player }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.38f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.86f)
                        )
                    )
                )
            ) {
                Column(Modifier.align(Alignment.TopStart).padding(horizontal = 48.dp, vertical = 34.dp)) {
                    Text("SELYRO TV", color = PlayerAccent, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        player.mediaMetadata.title?.toString().orEmpty().ifBlank { "Now playing" },
                        color = Color.White,
                        fontSize = 25.sp,
                        maxLines = 1
                    )
                }

                if (seekHint != null) {
                    Box(
                        Modifier.align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.76f))
                            .padding(horizontal = 28.dp, vertical = 17.dp)
                    ) {
                        Text(seekHint.orEmpty(), color = Color.White, fontSize = 25.sp)
                    }
                }

                if (buffering) {
                    Column(
                        Modifier.align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Buffering $bufferPercent%", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.width(180.dp).height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.15f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((bufferPercent / 100f).coerceIn(0.04f, 1f)).background(PlayerAccent))
                        }
                    }
                }

                ModernPlayerControls(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 52.dp, vertical = 36.dp),
                    position = position,
                    duration = duration,
                    buffered = buffered,
                    playing = playing,
                    seekable = player.isCurrentMediaItemSeekable
                )
            }
        }
    }
}

@Composable
private fun ModernPlayerControls(
    modifier: Modifier,
    position: Long,
    duration: Long,
    buffered: Long,
    playing: Boolean,
    seekable: Boolean
) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerKeyHint("◀", if (seekable) "-10s" else "")
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text(if (playing) "Ⅱ" else "▶", color = Color.Black, fontSize = 21.sp)
            }
            Spacer(Modifier.width(16.dp))
            PlayerKeyHint("▶", if (seekable) "+10s" else "")
            Spacer(Modifier.width(26.dp))
            Text("OK  Play / Pause", color = PlayerMuted, fontSize = 13.sp)
            Spacer(Modifier.width(22.dp))
            Text("Back  Exit", color = PlayerMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlayerKeyHint(symbol: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) { Text(symbol, color = Color.White, fontSize = 14.sp) }
        if (label.isNotBlank()) {
            Spacer(Modifier.width(7.dp))
            Text(label, color = PlayerMuted, fontSize = 12.sp)
        }
    }
}

private fun formatPlayerTime(valueMs: Long): String {
    val totalSeconds = max(0L, valueMs) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
