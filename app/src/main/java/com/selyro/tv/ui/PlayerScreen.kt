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
fun PlayerScreen(
    player: Player,
    title: String,
    kind: String,
    onBack: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun revealControls() {
        controlsVisible = true
        interactionVersion++
    }

    fun seekBy(deltaMs: Long) {
        val safeDuration = duration.takeIf { it > 0L } ?: return
        val next = (player.currentPosition + deltaMs).coerceIn(0L, safeDuration)
        player.seekTo(next)
        position = next
        seekHint = if (deltaMs > 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        revealControls()
    }

    BackHandler {
        player.stop()
        onBack()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        while (true) {
            position = max(0L, player.currentPosition)
            val rawDuration = player.duration
            duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0L) 0L else rawDuration
            buffered = max(0L, player.bufferedPosition)
            playing = player.isPlaying
            delay(400)
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
            delay(1200)
            seekHint = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        seekBy(-10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        seekBy(10_000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        playing = player.isPlaying
                        revealControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-30_000L)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(30_000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        revealControls()
                        true
                    }
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
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
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
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.38f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.84f)
                            )
                        )
                    )
            ) {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 48.dp, vertical = 34.dp)
                ) {
                    Text(
                        text = when (kind) {
                            "live" -> "LIVE"
                            "episode" -> "EPISODE"
                            else -> "MOVIE"
                        },
                        color = PlayerAccent,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(title, color = Color.White, fontSize = 25.sp, maxLines = 1)
                }

                if (seekHint != null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 26.dp, vertical = 16.dp)
                    ) {
                        Text(seekHint.orEmpty(), color = Color.White, fontSize = 24.sp)
                    }
                }

                PlayerControls(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 52.dp, vertical = 36.dp),
                    position = position,
                    duration = duration,
                    buffered = buffered,
                    playing = playing,
                    isLive = kind == "live"
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    modifier: Modifier,
    position: Long,
    duration: Long,
    buffered: Long,
    playing: Boolean,
    isLive: Boolean
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isLive) "Live stream" else formatTime(position), color = Color.White, fontSize = 14.sp)
            Text(
                if (isLive) "● LIVE" else formatTime(duration),
                color = if (isLive) PlayerAccent else PlayerMuted,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(9.dp))
        PlaybackProgress(position = position, duration = duration, buffered = buffered)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeyHint("◀", "-10s")
            Spacer(Modifier.width(18.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(if (playing) "Ⅱ" else "▶", color = Color.Black, fontSize = 21.sp)
            }
            Spacer(Modifier.width(18.dp))
            KeyHint("▶", "+10s")
            Spacer(Modifier.width(26.dp))
            Text("OK  Play / Pause", color = PlayerMuted, fontSize = 13.sp)
            Spacer(Modifier.width(22.dp))
            Text("Back  Exit player", color = PlayerMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlaybackProgress(position: Long, duration: Long, buffered: Long) {
    val playedFraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0L) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(bufferedFraction)
                .background(Color.White.copy(alpha = 0.34f))
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(playedFraction)
                .background(PlayerAccent)
        )
    }
}

@Composable
private fun KeyHint(symbol: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = PlayerMuted, fontSize = 13.sp)
    }
}

private fun formatTime(valueMs: Long): String {
    val totalSeconds = max(0L, valueMs) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
