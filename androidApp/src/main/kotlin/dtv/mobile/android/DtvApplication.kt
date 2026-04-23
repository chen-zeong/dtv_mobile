package dtv.mobile.android

import android.app.Application
import dtv.mobile.util.AppLog
import dtv.mobile.util.CrashFileLogger

class DtvApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    AppLog.init(this)
    CrashFileLogger.install(this)
  }
}

