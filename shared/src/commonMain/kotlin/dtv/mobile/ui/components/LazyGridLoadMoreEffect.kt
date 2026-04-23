package dtv.mobile.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun LazyGridLoadMoreEffect(
  gridState: LazyGridState,
  enabled: Boolean,
  itemCount: Int,
  buffer: Int = 4,
  onLoadMore: suspend () -> Unit,
) {
  val enabledState = rememberUpdatedState(enabled)
  val itemCountState = rememberUpdatedState(itemCount)
  val bufferState = rememberUpdatedState(buffer)
  val onLoadMoreState = rememberUpdatedState(onLoadMore)

  LaunchedEffect(gridState) {
    snapshotFlow {
      val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      val canScrollForward = gridState.canScrollForward
      Triple(lastVisible, canScrollForward, itemCountState.value)
    }
      .map { (lastVisible, canScrollForward, count) ->
        val threshold = (count - bufferState.value).coerceAtLeast(0)
        (lastVisible >= threshold) || (count > 0 && !canScrollForward)
      }
      .distinctUntilChanged()
      .filter { it }
      .collect {
        if (enabledState.value) onLoadMoreState.value()
      }
  }
}
