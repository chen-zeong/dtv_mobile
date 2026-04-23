package dtv.mobile.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun StreamPlayer(
  url: String,
  fullscreen: Boolean = false,
  liveMode: Boolean = true,
  onVideoAspectRatioChanged: (Float?) -> Unit = {},
  onError: (String) -> Unit = {},
  modifier: Modifier = Modifier,
)
