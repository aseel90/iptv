package com.selyro.tv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlaybackManager(context: Context, initialProfile: StreamingProfile = StreamingProfile.BALANCED) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentProfile = initialProfile
    private var retryCount = 0
    private val maxRetries = 5
    private var playbackGeneration = 0L
    private var startupWatchdog: Runnable? = null
    private var retryRunnable: Runnable? = null

    private val reconnectListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            scheduleReconnect(playerInternal.currentMediaItem?.mediaId.orEmpty(), playbackGeneration)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                cancelStartupWatchdog()
                cancelRetry()
            } else if (playbackState == Player.STATE_IDLE && playerInternal.currentMediaItem != null) {
                scheduleReconnect(playerInternal.currentMediaItem?.mediaId.orEmpty(), playbackGeneration)
            }
        }
    }

    private var playerInternal: ExoPlayer = newPlayer(currentProfile)

    val player: ExoPlayer get() = playerInternal

    private fun newPlayer(profile: StreamingProfile): ExoPlayer =
        PlayerFactory.create(appContext, profile).also { it.addListener(reconnectListener) }

    fun play(url: String, title: String? = null, startPositionMs: Long = 0L, isLive: Boolean = false) {
        playbackGeneration += 1
        val generation = playbackGeneration
        retryCount = 0
        mainHandler.removeCallbacksAndMessages(null)
        startupWatchdog = null
        retryRunnable = null

        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
            .build()

        playerInternal.playWhenReady = false
        if (isLive) {
            playerInternal.setMediaItem(item, true)
        } else if (startPositionMs > 0L) {
            playerInternal.setMediaItem(item, startPositionMs)
        } else {
            playerInternal.setMediaItem(item)
        }
        playerInternal.prepare()
        playerInternal.playWhenReady = true
        scheduleStartupWatchdog(item.mediaId, generation)
    }

    fun setProfile(profile: StreamingProfile) {
        if (profile == currentProfile) return
        playbackGeneration += 1
        cancelStartupWatchdog()
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
            scheduleStartupWatchdog(item.mediaId, playbackGeneration)
        }
    }

    private fun scheduleStartupWatchdog(mediaId: String, generation: Long) {
        cancelStartupWatchdog()
        if (mediaId.isBlank()) return
        val runnable = Runnable {
            if (generation != playbackGeneration) return@Runnable
            if (playerInternal.currentMediaItem?.mediaId != mediaId) return@Runnable
            if (playerInternal.playbackState != Player.STATE_READY) scheduleReconnect(mediaId, generation)
        }
        startupWatchdog = runnable
        mainHandler.postDelayed(runnable, 12_000L)
    }

    private fun cancelStartupWatchdog() {
        startupWatchdog?.let(mainHandler::removeCallbacks)
        startupWatchdog = null
    }

    private fun cancelRetry() {
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
    }

    private fun scheduleReconnect(mediaId: String, generation: Long) {
        if (generation != playbackGeneration || mediaId.isBlank()) return
        if (retryRunnable != null) return
        if (retryCount >= maxRetries || playerInternal.currentMediaItem?.mediaId != mediaId) return
        cancelStartupWatchdog()
        retryCount += 1
        val delayMs = when (retryCount) {
            1 -> 700L
            2 -> 1_400L
            3 -> 2_800L
            4 -> 5_000L
            else -> 8_000L
        }
        val resumePosition = if (playerInternal.isCurrentMediaItemSeekable && playerInternal.duration != C.TIME_UNSET) {
            playerInternal.currentPosition.coerceAtLeast(0L)
        } else 0L

        val runnable = Runnable {
            retryRunnable = null
            if (generation != playbackGeneration) return@Runnable
            if (playerInternal.currentMediaItem?.mediaId != mediaId) return@Runnable
            playerInternal.prepare()
            if (resumePosition > 0L) playerInternal.seekTo(resumePosition)
            playerInternal.playWhenReady = true
            scheduleStartupWatchdog(mediaId, generation)
        }
        retryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    fun stop() {
        playbackGeneration += 1
        mainHandler.removeCallbacksAndMessages(null)
        startupWatchdog = null
        retryRunnable = null
        retryCount = 0
        playerInternal.playWhenReady = false
        playerInternal.stop()
        playerInternal.clearMediaItems()
    }

    fun release() {
        playbackGeneration += 1
        mainHandler.removeCallbacksAndMessages(null)
        startupWatchdog = null
        retryRunnable = null
        playerInternal.removeListener(reconnectListener)
        playerInternal.release()
    }
}
