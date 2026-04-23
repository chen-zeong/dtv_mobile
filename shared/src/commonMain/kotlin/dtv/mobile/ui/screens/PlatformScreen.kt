package dtv.mobile.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dtv.mobile.model.Platform
import dtv.mobile.state.AppState
import dtv.mobile.ui.screens.bilibili.BilibiliHomeScreen
import dtv.mobile.ui.screens.douyin.DouyinHomeScreen
import dtv.mobile.ui.screens.douyu.DouyuHomeScreen
import dtv.mobile.ui.screens.huya.HuyaHomeScreen

@Composable
fun PlatformScreen(
  appState: AppState,
  modifier: Modifier = Modifier,
) {
  val subscribedForPlatform = appState.subscribedPartitions.filter { it.platform == appState.selectedPlatform }
  if (appState.simpleModeForSelectedPlatform && subscribedForPlatform.isNotEmpty()) {
    SimpleModePlatformScreen(
      appState = appState,
      partitions = subscribedForPlatform,
      modifier = modifier.fillMaxSize(),
    )
    return
  }

  when (appState.selectedPlatform) {
    Platform.Douyu -> DouyuHomeScreen(appState = appState, modifier = modifier.fillMaxSize())
    Platform.Huya -> HuyaHomeScreen(appState = appState, modifier = modifier.fillMaxSize())
    Platform.Douyin -> DouyinHomeScreen(appState = appState, modifier = modifier.fillMaxSize())
    Platform.Bilibili -> BilibiliHomeScreen(appState = appState, modifier = modifier.fillMaxSize())
    else -> Text(
      text = "暂未实现：${appState.selectedPlatform.title}",
      style = MaterialTheme.typography.bodyMedium,
      modifier = modifier,
    )
  }
}
