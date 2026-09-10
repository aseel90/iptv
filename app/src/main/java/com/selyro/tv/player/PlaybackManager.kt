package com.selyro.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class PlaybackManager(private val context:Context){
 var profile=StreamingProfile.BALANCED
 fun player(url:String):ExoPlayer=PlayerFactory.create(context,profile).apply{setMediaItem(MediaItem.fromUri(url));prepare();playWhenReady=true}
}
