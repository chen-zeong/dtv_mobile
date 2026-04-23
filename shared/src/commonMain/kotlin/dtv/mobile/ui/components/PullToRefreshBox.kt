package dtv.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun PullToRefreshBox(
  refreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
  Box(modifier = modifier.pullRefresh(pullRefreshState)) {
    content()
    PullRefreshIndicator(
      refreshing = refreshing,
      state = pullRefreshState,
      modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}
