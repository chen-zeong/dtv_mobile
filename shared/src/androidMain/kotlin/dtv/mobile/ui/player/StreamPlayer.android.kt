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
import dtv.mobile.util.AppLog
import android.view.View

@Composable
actual fun StreamPlayer(
  url: String,
  fullscreen: Boolean,
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
    val listener = object : Player.Listener {
      override fun onPlayerError(error: PlaybackException) {
        AppLog.e("DTV-Player", "ExoPlayer error url=$url code=${error.errorCodeName}", error)
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
          AppLog.w("DTV-Player", "retrying with http due to toASCII failure. url=$url")
          onError("__retry_http__:" + url.replaceFirst("https://", "http://"))
          return
        }

        onError(error.message ?: "播放器错误: ${error.errorCodeName}")
      }

      override fun onTracksChanged(tracks: Tracks) {
        val selectedVideo = tracks.groups
          .filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
          .flatMap { g -> (0 until g.length).mapNotNull { idx -> if (g.isTrackSelected(idx)) g.getTrackFormat(idx) else null } }
        val selectedAudio = tracks.groups
          .filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
          .flatMap { g -> (0 until g.length).mapNotNull { idx -> if (g.isTrackSelected(idx)) g.getTrackFormat(idx) else null } }

        val videoDesc = selectedVideo.firstOrNull()?.let { f ->
          "mime=${f.sampleMimeType} codecs=${f.codecs} ${f.width}x${f.height}"
        } ?: "none"
        val audioDesc = selectedAudio.firstOrNull()?.let { f ->
          "mime=${f.sampleMimeType} codecs=${f.codecs} ch=${f.channelCount} sr=${f.sampleRate}"
        } ?: "none"
        AppLog.i("DTV-Player", "tracks url=$url video=$videoDesc audio=$audioDesc")
      }

      override fun onVideoSizeChanged(videoSize: VideoSize) {
        val rawW = videoSize.width
        val rawH = videoSize.height
        val rotation = videoSize.unappliedRotationDegrees
        val pixelRatio = videoSize.pixelWidthHeightRatio

        val (w, h) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH
        val aspect = if (w > 0 && h > 0) (w.toFloat() * pixelRatio) / h.toFloat() else null

        AppLog.i(
          "DTV-Player",
          "video size url=$url size=${rawW}x${rawH} rotation=$rotation pixelRatio=$pixelRatio aspect=$aspect",
        )
        onVideoAspectRatioChanged(aspect)
      }

      override fun onRenderedFirstFrame() {
        AppLog.i("DTV-Player", "rendered first frame url=$url")
      }
    }
    player.addListener(listener)
    onDispose { player.removeListener(listener) }
  }

  DisposableEffect(player) {
    onDispose { player.release() }
  }

  AndroidView(
    modifier = modifier,
    factory = {
      PlayerView(it).apply {
        useController = true
        controllerAutoShow = true
        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        this.player = player

        applyControls(fullscreen = fullscreen)
      }
    },
    update = { view ->
      view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      view.useController = true
      view.controllerAutoShow = true
      view.player = player

      view.applyControls(fullscreen = fullscreen)
    },
  )
}

private fun PlayerView.applyControls(fullscreen: Boolean) {
  // In fullscreen: only keep play/pause. Hide seek/time UI and skip buttons.
  setShowPreviousButton(!fullscreen)
  setShowNextButton(!fullscreen)
  setShowRewindButton(!fullscreen)
  setShowFastForwardButton(!fullscreen)

  val visibility = if (fullscreen) View.GONE else View.VISIBLE

  findViewById<View?>(androidx.media3.ui.R.id.exo_progress)?.visibility = visibility
  findViewById<View?>(androidx.media3.ui.R.id.exo_position)?.visibility = visibility
  findViewById<View?>(androidx.media3.ui.R.id.exo_duration)?.visibility = visibility
  findViewById<View?>(androidx.media3.ui.R.id.exo_rew)?.visibility = visibility
  findViewById<View?>(androidx.media3.ui.R.id.exo_ffwd)?.visibility = visibility
  findViewById<View?>(androidx.media3.ui.R.id.exo_prev)?.visibility = visibility
  findViewById<View?>(androidx.media3.ui.R.id.exo_next)?.visibility = visibility
}
