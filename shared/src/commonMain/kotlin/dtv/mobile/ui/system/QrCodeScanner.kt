package dtv.mobile.ui.system

import androidx.compose.runtime.Composable

@Composable
expect fun rememberQrCodeScanLauncher(
  onResult: (text: String?) -> Unit,
): () -> Unit

