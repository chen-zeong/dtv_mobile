package dtv.mobile.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlayerSystemGestureLayer(
  enabled: Boolean,
  modifier: Modifier = Modifier,
)
