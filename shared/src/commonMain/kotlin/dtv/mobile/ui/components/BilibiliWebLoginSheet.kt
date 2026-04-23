package dtv.mobile.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun BilibiliWebLoginSheet(
  onDismissRequest: () -> Unit,
  onCookieCaptured: (cookieHeader: String) -> Unit,
)

