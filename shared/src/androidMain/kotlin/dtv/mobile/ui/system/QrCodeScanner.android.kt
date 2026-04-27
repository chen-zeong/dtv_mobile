package dtv.mobile.ui.system

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
actual fun rememberQrCodeScanLauncher(
  onResult: (text: String?) -> Unit,
): () -> Unit {
  val context = LocalContext.current
  val onResultUpdated by rememberUpdatedState(onResult)

  var pendingLaunch by remember { mutableStateOf(false) }

  val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
    val text = result.contents?.trim().takeIf { !it.isNullOrBlank() }
    onResultUpdated(text)
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (pendingLaunch && granted) {
        pendingLaunch = false
        val options = ScanOptions()
          .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
          .setBeepEnabled(false)
          .setOrientationLocked(false)
        scanLauncher.launch(options)
      } else {
        pendingLaunch = false
        onResultUpdated(null)
      }
    }

  fun canUseCamera(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }

  return remember(context) {
    {
      if (canUseCamera()) {
        val options = ScanOptions()
          .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
          .setBeepEnabled(false)
          .setOrientationLocked(false)
        scanLauncher.launch(options)
      } else {
        pendingLaunch = true
        permissionLauncher.launch(Manifest.permission.CAMERA)
      }
    }
  }
}
