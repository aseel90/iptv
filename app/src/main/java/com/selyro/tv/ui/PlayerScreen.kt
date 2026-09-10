package com.selyro.tv.ui

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.selyro.tv.player.PlaybackManager

@OptIn(UnstableApi::class)
@Composable fun PlayerScreen(url:String,modifier:Modifier=Modifier){
 val context=LocalContext.current
 val player=remember(url){PlaybackManager(context).player(url)}
 DisposableEffect(player){onDispose{player.release()}}
 AndroidView(modifier=modifier,factory{PlayerView(it).apply{this.player=player;useController=true;layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)}})
}
