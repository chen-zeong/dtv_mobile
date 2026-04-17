package dtv.mobile.ui.screens.douyin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dtv.mobile.model.Streamer
import dtv.mobile.repo.DouyinCate1
import dtv.mobile.repo.DouyinCate2
import dtv.mobile.repo.PagedResult
import dtv.mobile.state.AppState
import dtv.mobile.ui.components.StreamerCard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.random.Random

private const val PAGE_SIZE = 15

private fun generateMsToken(length: Int = 107): String {
  val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
  return buildString(length) {
    repeat(length) { append(charset[Random.nextInt(charset.length)]) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DouyinHomeScreen(
  appState: AppState,
  modifier: Modifier = Modifier,
) {
  var categories: List<DouyinCate1> by remember { mutableStateOf(emptyList()) }
  var selectedCate1: DouyinCate1? by remember { mutableStateOf(null) }
  var selectedCate2: DouyinCate2? by remember { mutableStateOf(null) }
  var showCate2Sheet by remember { mutableStateOf(false) }

  var rooms by remember { mutableStateOf<List<Streamer>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var loadingMore by remember { mutableStateOf(false) }
  var hasMore by remember { mutableStateOf(true) }
  var offset by remember { mutableIntStateOf(0) }
  var msToken by remember { mutableStateOf(generateMsToken()) }

  val gridState = rememberLazyGridState()

  suspend fun loadPage(reset: Boolean) {
    val cate2 = selectedCate2 ?: return
    if (reset) {
      rooms = emptyList()
      hasMore = true
      offset = 0
      msToken = generateMsToken()
    }
    if (!hasMore) return

    if (reset) loading = true else loadingMore = true
    val resp: PagedResult<Streamer> = appState.repo.fetchDouyinPartitionLiveList(
      partition = cate2.partition,
      partitionType = cate2.partitionType,
      offset = offset,
      limit = PAGE_SIZE,
      msToken = msToken,
    )
    rooms = if (reset) resp.items else rooms + resp.items
    hasMore = resp.items.size == PAGE_SIZE
    offset += resp.items.size
    if (reset) loading = false else loadingMore = false
  }

  LaunchedEffect(Unit) {
    loading = true
    val data = appState.repo.fetchDouyinCategories()
    categories = data
    selectedCate1 = data.firstOrNull()
    selectedCate2 = selectedCate1?.cate2List?.firstOrNull()
    loading = false
  }

  LaunchedEffect(selectedCate2?.partition, selectedCate2?.partitionType) {
    if (selectedCate2 == null) return@LaunchedEffect
    loadPage(reset = true)
    gridState.scrollToItem(0)
  }

  LaunchedEffect(gridState, loading, loadingMore, hasMore, rooms.size) {
    if (loading) return@LaunchedEffect
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .map { lastVisible -> lastVisible >= (rooms.size - 4).coerceAtLeast(0) }
      .distinctUntilChanged()
      .filter { it }
      .collectLatest {
        if (hasMore && !loadingMore) {
          loadPage(reset = false)
        }
      }
  }

  if (showCate2Sheet) {
    ModalBottomSheet(onDismissRequest = { showCate2Sheet = false }) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("选择分类", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        selectedCate1?.cate2List.orEmpty().forEach { c2 ->
          val selected = c2.partition == selectedCate2?.partition && c2.partitionType == selectedCate2?.partitionType
          TextButton(
            onClick = {
              selectedCate2 = c2
              showCate2Sheet = false
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(text = c2.name, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
          }
        }
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  Column(modifier = modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      items(categories, key = { it.name }) { c1 ->
        FilterChip(
          selected = c1.name == selectedCate1?.name,
          onClick = {
            selectedCate1 = c1
            selectedCate2 = c1.cate2List.firstOrNull()
          },
          label = { Text(c1.name) },
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))
    TextButton(onClick = { if (selectedCate1 != null) showCate2Sheet = true }) {
      Text(text = selectedCate2?.name ?: "选择分类", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(modifier = Modifier.height(10.dp))

    LazyVerticalGrid(
      modifier = Modifier.fillMaxSize(),
      state = gridState,
      columns = GridCells.Fixed(2),
      contentPadding = PaddingValues(bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (loading) {
        items(6, span = { GridItemSpan(1) }) { idx ->
          StreamerCard(
            streamer = Streamer(
              platform = dtv.mobile.model.Platform.Douyin,
              roomId = "loading-$idx",
              name = "加载中…",
              title = "正在请求抖音数据",
              viewerText = "",
              isLive = true,
            ),
            onClick = {},
          )
        }
      } else {
        items(rooms.size, key = { rooms[it].roomId }, span = { GridItemSpan(1) }) { index ->
          val streamer = rooms[index]
          StreamerCard(streamer = streamer, onClick = { appState.openPlayer(streamer) })
        }

        item(span = { GridItemSpan(2) }) {
          Spacer(modifier = Modifier.height(4.dp))
          when {
            loadingMore -> Text("加载更多…", style = MaterialTheme.typography.bodyMedium)
            hasMore -> Text("继续滑动加载更多", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            else -> Text("没有更多了", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
          }
        }
      }
    }
  }
}
