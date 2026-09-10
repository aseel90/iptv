package com.selyro.tv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(player: Player, onBack: () -> Unit) {
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var percent by remember { mutableIntStateOf(player.bufferedPercentage.coerceIn(0, 100)) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                percent = player.bufferedPercentage.coerceIn(0, 100)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, buffering) {
        while (buffering) {
            percent = player.bufferedPercentage.coerceIn(0, 100)
            delay(300)
        }
    }

    BackHandler {
        player.stop()
        onBack()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = true
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

        if (buffering) {
            Column(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC0D131A))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Buffering ${percent}%", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(180.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF26313C))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((percent / 100f).coerceIn(0.04f, 1f)).background(Color(0xFF63D8C6)))
                }
            }
        }
    }
}
