package com.simplebilibili.live.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

class LivePlayer(
    context: Context,
    private val surfaceView: AspectRatioSurfaceView,
    private val onFatalError: (String) -> Unit
) {
    private val player = ExoPlayer.Builder(context).build()
    private var retryCount = 0
    private var currentUrl: String? = null

    init {
        player.setVideoSurfaceView(surfaceView)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val retryUrl = currentUrl
                if (retryCount < MAX_RETRY && retryUrl != null) {
                    retryCount += 1
                    play(retryUrl, resetRetry = false)
                } else {
                    onFatalError("播放失败：${error.message ?: "无法连接直播流"}")
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                surfaceView.setVideoSize(videoSize.width, videoSize.height)
            }
        })
    }

    fun play(url: String) {
        play(url, resetRetry = true)
    }

    private fun play(url: String, resetRetry: Boolean) {
        if (resetRetry) retryCount = 0
        currentUrl = url
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    fun stop() {
        retryCount = 0
        currentUrl = null
        surfaceView.setVideoSize(0, 0)
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        player.clearVideoSurfaceView(surfaceView)
        player.release()
    }

    companion object {
        private const val MAX_RETRY = 2
    }
}
