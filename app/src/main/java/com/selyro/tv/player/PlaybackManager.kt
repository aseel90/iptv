package com.selyro.tv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlaybackManager(context: Context, initialProfile: StreamingProfile = StreamingProfile.BALANCED) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentProfile = initialProfile
    private var retryCount = 0
    private val maxRetries = 3

    private val reconnectListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            scheduleReconnect()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) retryCount = 0
        }
    }

    private var playerInternal: ExoPlayer = newPlayer(currentProfile)

    val player: ExoPlayer get() = playerInternal

    private fun newPlayer(profile: StreamingProfile): ExoPlayer =
        PlayerFactory.create(appContext, profile).also { it.addListener(reconnectListener) }

    fun play(url: String, title: String? = null) {
        retryCount = 0
        mainHandler.removeCallbacksAndMessages(null)
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
        playerInternal.removeListener(reconnectListener)
        playerInternal.release()
        currentProfile = profile
        playerInternal = newPlayer(profile)
        if (item != null) {
            playerInternal.setMediaItem(item, position)
            playerInternal.prepare()
            playerInternal.playWhenReady = wasPlaying
        }
    }

    private fun scheduleReconnect() {
        if (retryCount >= maxRetries || playerInternal.currentMediaItem == null) return
        retryCount += 1
        val delayMs = when (retryCount) {
            1 -> 750L
            2 -> 1_500L
            else -> 3_000L
        }
        mainHandler.postDelayed({
            if (playerInternal.currentMediaItem != null) {
                playerInternal.prepare()
                playerInternal.playWhenReady = true
            }
        }, delayMs)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        retryCount = 0
        playerInternal.stop()
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        playerInternal.removeListener(reconnectListener)
        playerInternal.release()
    }
}
