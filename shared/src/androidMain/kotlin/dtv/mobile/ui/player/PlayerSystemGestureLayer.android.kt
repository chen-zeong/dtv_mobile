package dtv.mobile.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AdjustMode { Brightness, Volume }

private data class GestureHud(
  val mode: AdjustMode,
  val value: Float,
)

@Composable
actual fun PlayerSystemGestureLayer(
  enabled: Boolean,
  modifier: Modifier,
) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  val scope = rememberCoroutineScope()
  val originalWindowBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }
  var hud by remember { mutableStateOf<GestureHud?>(null) }
  var hideJob by remember { mutableStateOf<Job?>(null) }

  DisposableEffect(activity) {
    onDispose {
      activity?.window?.let { window ->
        window.attributes = window.attributes.apply { screenBrightness = originalWindowBrightness }
      }
    }
  }

  fun currentBrightness(): Float {
    val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowValue >= 0f) return windowValue.coerceIn(0.01f, 1f)
    val system = runCatching {
      Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (system / 255f).coerceIn(0.01f, 1f)
  }

  fun currentVolume(): Float {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
  }

  fun setBrightness(value: Float) {
    val window = activity?.window ?: return
    window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
  }

  fun setVolume(value: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (value.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
  }

  fun scheduleHide() {
    hideJob?.cancel()
    hideJob = scope.launch {
      delay(1000)
      hud = null
    }
  }

  Box(
    modifier = modifier
      .pointerInput(enabled, activity, audioManager) {
        if (!enabled) return@pointerInput
        var mode = AdjustMode.Brightness
        var startValue = 0f
        var accumulatedY = 0f

        detectDragGestures(
          onDragStart = { offset ->
            hideJob?.cancel()
            mode = if (offset.x < size.width / 2f) AdjustMode.Brightness else AdjustMode.Volume
            startValue = if (mode == AdjustMode.Brightness) currentBrightness() else currentVolume()
            accumulatedY = 0f
            hud = GestureHud(mode = mode, value = startValue)
          },
          onDrag = { change, dragAmount ->
            change.consume()
            accumulatedY += dragAmount.y
            val value = systemValueAfterVerticalDrag(
              startValue = startValue,
              accumulatedY = accumulatedY,
              viewportHeightPx = size.height.toFloat(),
            )
            if (mode == AdjustMode.Brightness) {
              setBrightness(value.coerceAtLeast(0.01f))
            } else {
              setVolume(value)
            }
            hud = GestureHud(mode = mode, value = value)
          },
          onDragEnd = { scheduleHide() },
          onDragCancel = { scheduleHide() },
        )
      },
  ) {
    hud?.let { state ->
      Column(
        modifier = Modifier
          .align(if (state.mode == AdjustMode.Brightness) Alignment.CenterStart else Alignment.CenterEnd)
          .padding(horizontal = 28.dp)
          .width(150.dp)
          .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(14.dp))
          .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = if (state.mode == AdjustMode.Brightness) {
            Icons.Default.BrightnessMedium
          } else {
            Icons.AutoMirrored.Filled.VolumeUp
          },
          contentDescription = null,
          tint = Color.White,
        )
        Text(
          text = "${(state.value * 100).toInt()}%",
          color = Color.White,
        )
        LinearProgressIndicator(
          progress = { state.value },
          modifier = Modifier.fillMaxWidth(),
          color = Color.White,
          trackColor = Color.White.copy(alpha = 0.22f),
        )
      }
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
