package dtv.mobile.android

import android.app.Application
import dtv.mobile.platform.app.installAppForegroundTracker

class DtvApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    installAppForegroundTracker(this)
  }
}

