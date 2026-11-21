package dtv.mobile.platform.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private object AppForegroundTracker {
  private val foreground = MutableStateFlow(false)
  private var startedActivities = 0
  private var installed = false

  fun install(application: Application) {
    if (installed) return
    installed = true
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
          startedActivities += 1
          foreground.value = true
        }

        override fun onActivityStopped(activity: Activity) {
          startedActivities = (startedActivities - 1).coerceAtLeast(0)
          foreground.value = startedActivities > 0
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      },
    )
  }

  fun state(): StateFlow<Boolean> = foreground.asStateFlow()
}

fun installAppForegroundTracker(application: Application) {
  AppForegroundTracker.install(application)
}

actual fun appForegroundState(): StateFlow<Boolean> = AppForegroundTracker.state()
