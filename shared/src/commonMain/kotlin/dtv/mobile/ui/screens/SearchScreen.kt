package dtv.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.state.AppState
import dtv.mobile.ui.components.StreamerCard
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
  appState: AppState,
  modifier: Modifier = Modifier,
) {
  var query by remember { mutableStateOf("") }
  var results by remember(appState.selectedPlatform) { mutableStateOf<List<Streamer>>(emptyList()) }
  var loading by remember(appState.selectedPlatform) { mutableStateOf(false) }
  var error by remember(appState.selectedPlatform) { mutableStateOf<String?>(null) }

  val platform = appState.selectedPlatform
  val supported = platform == Platform.Douyu || platform == Platform.Huya || platform == Platform.Bilibili || platform == Platform.Douyin

  val placeholder = when (platform) {
    Platform.Huya -> "搜索虎牙主播/房间..."
    Platform.Bilibili -> "搜索B站直播间..."
    Platform.Douyin -> "输入抖音房间号（webRid）..."
    Platform.Custom -> "自定义分区暂不支持搜索"
    else -> "搜索斗鱼主播/房间..."
  }

  LaunchedEffect(platform, query) {
    val trimmed = query.trim()
    error = null
    if (!supported || trimmed.isEmpty()) {
      results = emptyList()
      loading = false
      return@LaunchedEffect
    }

    loading = true
    delay(220)
    runCatching { appState.repo.searchAnchors(platform = platform, keyword = trimmed) }
      .onSuccess { results = it }
      .onFailure {
        results = emptyList()
        error = it.message ?: "搜索失败"
      }
    loading = false
  }

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      placeholder = { Text(placeholder) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )

    if (!supported) {
      Text(
        text = placeholder,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
      )
    } else {
      if (loading) {
        CircularProgressIndicator(modifier = Modifier.padding(top = 6.dp))
      }

      error?.let { msg ->
        Text(
          text = msg,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (!loading && error == null && query.trim().isNotEmpty() && results.isEmpty()) {
        Text(
          text = "没有找到相关直播间",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(results, key = { "${it.platform}-${it.roomId}" }) { s ->
          StreamerCard(
            streamer = s,
            onClick = { appState.openPlayer(s) },
          )
        }
      }
    }
  }
}
