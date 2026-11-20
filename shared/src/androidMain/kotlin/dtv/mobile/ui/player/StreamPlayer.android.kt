package dtv.mobile.ui.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
actual fun StreamPlayer(
  url: String,
  fullscreen: Boolean,
  liveMode: Boolean,
  zoomToFill: Boolean,
  onVideoAspectRatioChanged: (Float?) -> Unit,
  onError: (String) -> Unit,
  modifier: Modifier,
) {
  val context = LocalContext.current
  val player = remember(url) {
    val headers = buildMap {
      put(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
      )
      val u = url.lowercase()
      when {
        "huya" in u -> put("Referer", "https://www.huya.com/")
        "bilibili" in u -> put("Referer", "https://live.bilibili.com/")
        "douyin" in u -> put("Referer", "https://live.douyin.com/")
        "douyu" in u -> put("Referer", "https://www.douyu.com/")
      }
    }

    val httpFactory = DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true)
      .setDefaultRequestProperties(headers)

    ExoPlayer.Builder(context)
      .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
      .build()
      .apply {
      setMediaItem(MediaItem.fromUri(Uri.parse(url)))
      playWhenReady = true
      prepare()
    }
  }

  DisposableEffect(player, url) {
    fun reportVideoSize(videoSize: VideoSize) {
      val rawW = videoSize.width
      val rawH = videoSize.height
      val rotation = videoSize.unappliedRotationDegrees
      val pixelRatio = videoSize.pixelWidthHeightRatio

      val (w, h) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH
      val aspect = if (w > 0 && h > 0) (w.toFloat() * pixelRatio) / h.toFloat() else null
      onVideoAspectRatioChanged(aspect)
    }

    val listener = object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
          // live HLS sometimes falls behind; reset and retry
          runCatching {
            player.seekToDefaultPosition()
            player.prepare()
          }
          return
        }

        val causeMsg = generateSequence(error.cause) { it.cause }
          .mapNotNull { it.message }
          .firstOrNull { it.contains("Invalid input to toASCII") }

        if (causeMsg != null && url.startsWith("https://")) {
          // Workaround for some CDN hosts containing '_' which breaks IDN/SNI on Android.
          onError("__retry_http__:" + url.replaceFirst("https://", "http://"))
          return
        }

        onError(error.message ?: "播放器错误: ${error.errorCodeName}")
      }

      override fun onTracksChanged(tracks: Tracks) = Unit

      override fun onVideoSizeChanged(videoSize: VideoSize) = reportVideoSize(videoSize)

      override fun onRenderedFirstFrame() = reportVideoSize(player.videoSize)
    }
    player.addListener(listener)
    reportVideoSize(player.videoSize)
    onDispose { player.removeListener(listener) }
  }

  DisposableEffect(player) {
    onDispose { player.release() }
  }

  AndroidView(
    modifier = modifier,
    factory = {
      PlayerView(it).apply {
        useController = false
        controllerAutoShow = false
        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        resizeMode = if (zoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        // Keep screen on during playback (some Android 16 devices will otherwise follow a short system timeout).
        keepScreenOn = true
        this.player = player
      }
    },
    update = { view ->
      view.resizeMode = if (zoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
      view.useController = false
      view.controllerAutoShow = false
      view.keepScreenOn = true
      if (view.player !== player) {
        view.player = player
      }
      view.post {
        view.requestLayout()
        view.invalidate()
      }
    },
  )
}
