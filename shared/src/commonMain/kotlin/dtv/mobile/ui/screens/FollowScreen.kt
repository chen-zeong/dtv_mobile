package dtv.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dtv.mobile.state.AppState
import dtv.mobile.ui.components.PullToRefreshBox
import dtv.mobile.ui.components.StreamerCard
import kotlinx.coroutines.launch

@Composable
fun FollowScreen(
  appState: AppState,
  modifier: Modifier = Modifier,
) {
  val items = appState.followedStreamers
  val displayItems = run {
    val snapshot = items.toList()
    val live = snapshot.filter { it.isLive }
    val offline = snapshot.filterNot { it.isLive }
    live + offline
  }
  var refreshing by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  if (items.isEmpty()) {
    Text(
      text = "还没有关注的主播",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
      modifier = modifier.fillMaxSize().padding(16.dp),
    )
    return
  }

  PullToRefreshBox(
    refreshing = refreshing,
    onRefresh = {
      if (refreshing) return@PullToRefreshBox
      scope.launch {
        refreshing = true
        runCatching { appState.refreshFollowedStreamerCards() }
        refreshing = false
      }
    },
    modifier = modifier.fillMaxSize(),
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
      contentPadding = PaddingValues(bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(displayItems, key = { "${it.platform}-${it.roomId}" }) { s ->
        StreamerCard(
          streamer = s,
          followed = true,
          highlightLiveBorder = true,
          onClick = { appState.openPlayer(s) },
          onToggleFollow = { appState.toggleFollow(s) },
        )
      }
    }
  }
}
