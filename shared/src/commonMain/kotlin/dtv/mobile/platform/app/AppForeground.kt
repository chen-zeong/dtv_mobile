package dtv.mobile.platform.app

import kotlinx.coroutines.flow.StateFlow

expect fun appForegroundState(): StateFlow<Boolean>
