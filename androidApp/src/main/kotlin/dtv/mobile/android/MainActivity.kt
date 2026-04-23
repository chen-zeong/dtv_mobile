package dtv.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import dtv.mobile.App
import dtv.mobile.repo.android.AndroidDtvRepository
import dtv.mobile.state.SubscriptionStoreAndroid
import dtv.mobile.util.AppLog

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppLog.init(applicationContext)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContent {
      val repo = remember { AndroidDtvRepository(applicationContext) }
      val subscriptionStore = remember { SubscriptionStoreAndroid(applicationContext) }
      App(repo = repo, subscriptionStore = subscriptionStore)
    }
  }
}
