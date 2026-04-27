package dtv.mobile.ui.system

import androidx.compose.runtime.Composable

@Composable
expect fun FullscreenEffect(
  enabled: Boolean,
  lockLandscape: Boolean,
  exitToPortrait: Boolean,
)

