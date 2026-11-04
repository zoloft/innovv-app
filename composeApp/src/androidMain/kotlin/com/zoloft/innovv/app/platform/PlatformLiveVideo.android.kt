package com.zoloft.innovv.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(UnstableApi::class)
@Composable
actual fun PlatformLiveVideo(
    url: String,
    modifier: Modifier,
    onFrame: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val source = if (url.startsWith("rtsp", ignoreCase = true)) {
                RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .createMediaSource(MediaItem.fromUri(url))
            } else {
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(url))
            }
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnError by rememberUpdatedState(onError)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error.message ?: "Live stream playback error")
            }
        }
        val frameListener = VideoFrameMetadataListener { _, _, _, _ -> currentOnFrame() }
        player.addListener(listener)
        player.setVideoFrameMetadataListener(frameListener)
        onDispose {
            player.removeListener(listener)
            player.clearVideoFrameMetadataListener(frameListener)
            player.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view ->
            view.player = player
        },
    )
}
