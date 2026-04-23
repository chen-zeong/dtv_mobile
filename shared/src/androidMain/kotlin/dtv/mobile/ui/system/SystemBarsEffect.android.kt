package dtv.mobile.ui.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? {
  return when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
}

@Composable
actual fun SystemBarsEffect(
  darkTheme: Boolean,
) {
  val view = LocalView.current
  val context = LocalContext.current

  DisposableEffect(view, context, darkTheme) {
    val activity = context.findActivity()
    val window = activity?.window
    if (window == null) {
      onDispose {}
    } else {
      val controller = WindowCompat.getInsetsController(window, view)
      val prevLightStatus = controller.isAppearanceLightStatusBars
      val prevLightNav = controller.isAppearanceLightNavigationBars
      val prevStatusBarColor = window.statusBarColor
      val prevNavBarColor = window.navigationBarColor

      // Draw behind the system bars; content background will show through.
      window.statusBarColor = Color.TRANSPARENT
      window.navigationBarColor = Color.TRANSPARENT
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
      }

      controller.isAppearanceLightStatusBars = !darkTheme
      controller.isAppearanceLightNavigationBars = !darkTheme

      onDispose {
        window.statusBarColor = prevStatusBarColor
        window.navigationBarColor = prevNavBarColor
        controller.isAppearanceLightStatusBars = prevLightStatus
        controller.isAppearanceLightNavigationBars = prevLightNav
      }
    }
  }
}
