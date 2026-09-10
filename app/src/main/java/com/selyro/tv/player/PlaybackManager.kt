package com.selyro.tv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class PlaybackManager(context: Context, initialProfile: StreamingProfile = StreamingProfile.BALANCED) {
    private val appContext = context.applicationContext
    private var currentProfile = initialProfile
    private var playerInternal: ExoPlayer = PlayerFactory.create(appContext, currentProfile)

    val player: ExoPlayer get() = playerInternal

    fun play(url: String, title: String? = null) {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
            .build()
        playerInternal.setMediaItem(item)
        playerInternal.prepare()
        playerInternal.playWhenReady = true
    }

    fun setProfile(profile: StreamingProfile) {
        if (profile == currentProfile) return
        val wasPlaying = playerInternal.playWhenReady
        val item = playerInternal.currentMediaItem
        val position = playerInternal.currentPosition
        playerInternal.release()
        currentProfile = profile
        playerInternal = PlayerFactory.create(appContext, profile)
        if (item != null) {
            playerInternal.setMediaItem(item, position)
            playerInternal.prepare()
            playerInternal.playWhenReady = wasPlaying
        }
    }

    fun stop() = playerInternal.stop()
    fun release() = playerInternal.release()
}
