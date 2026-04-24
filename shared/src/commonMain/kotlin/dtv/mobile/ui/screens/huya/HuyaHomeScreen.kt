package dtv.mobile.ui.screens.huya

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dtv.mobile.model.Streamer
import dtv.mobile.model.Platform
import dtv.mobile.repo.HuyaCate1
import dtv.mobile.repo.HuyaCate2
import dtv.mobile.repo.PagedResult
import dtv.mobile.state.AppState
import dtv.mobile.state.SubscribedPartition
import dtv.mobile.ui.components.CategoryPill
import dtv.mobile.ui.components.LazyGridLoadMoreEffect
import dtv.mobile.ui.components.PullToRefreshBox
import dtv.mobile.ui.components.StreamerCard
import dtv.mobile.ui.components.StreamerCardSkeleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuyaHomeScreen(
  appState: AppState,
  modifier: Modifier = Modifier,
) {
  var categories: List<HuyaCate1> by remember { mutableStateOf(emptyList()) }
  var selectedCate1: HuyaCate1? by remember { mutableStateOf(null) }
  var selectedCate2: HuyaCate2? by remember { mutableStateOf(null) }
  var showCate2Sheet by remember { mutableStateOf(false) }

  var rooms by remember { mutableStateOf<List<Streamer>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var loadingMore by remember { mutableStateOf(false) }
  var hasMore by remember { mutableStateOf(true) }
  var page by remember { mutableStateOf(1) }
  var refreshing by remember { mutableStateOf(false) }

  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()

  suspend fun loadPage(reset: Boolean) {
    val gid = selectedCate2?.gid ?: return
    val hadItems = rooms.isNotEmpty()
    if (reset) {
      rooms = emptyList()
      hasMore = true
      page = 1
    }
    if (!hasMore) return

    if (reset) loading = true else loadingMore = true
    val startMs = if (reset && !hadItems) System.currentTimeMillis() else 0L
    val resp: PagedResult<Streamer> = appState.repo.fetchHuyaLiveList(gid = gid, page = page, limit = PAGE_SIZE)
    val incoming = resp.items
    val old = rooms
    val (merged, addedCount) = if (reset) {
      incoming to incoming.size
    } else {
      val existing = old.asSequence().map { it.roomId }.toHashSet()
      val added = incoming.filter { existing.add(it.roomId) }
      (old + added) to added.size
    }
    rooms = merged
    hasMore = incoming.isNotEmpty() && addedCount > 0
    page += 1
    if (reset && !hadItems) {
      val elapsed = System.currentTimeMillis() - startMs
      val remaining = 180L - elapsed
      if (remaining > 0) delay(remaining)
    }
    if (reset) loading = false else loadingMore = false
    if (reset) appState.platformSwitchLoading = false
  }

  LaunchedEffect(Unit) {
    loading = true
    val data = appState.repo.fetchHuyaCategories()
    categories = data

    val savedGid = appState.currentPartition
      ?.takeIf { it.platform == Platform.Huya }
      ?.id
      ?.substringAfter("huya:", missingDelimiterValue = "")
      ?.takeIf { it.isNotBlank() }

    val saved = savedGid?.let { gid ->
      data.asSequence().mapNotNull { c1 ->
        val c2 = c1.cate2List.firstOrNull { it.gid == gid }
        c2?.let { c1 to it }
      }.firstOrNull()
    }

    selectedCate1 = saved?.first ?: data.firstOrNull()
    selectedCate2 = saved?.second ?: selectedCate1?.cate2List?.firstOrNull()
  }

  LaunchedEffect(selectedCate2?.gid) {
    if (selectedCate2 == null) return@LaunchedEffect
    appState.currentPartition = SubscribedPartition(
      id = "huya:${selectedCate2!!.gid}",
      name = selectedCate2!!.name,
      platform = Platform.Huya,
    )
    loadPage(reset = true)
    gridState.scrollToItem(0)
  }

  LazyGridLoadMoreEffect(
    gridState = gridState,
    enabled = !loading && !loadingMore && hasMore,
    itemCount = rooms.size,
  ) {
    loadPage(reset = false)
  }

  if (showCate2Sheet) {
    ModalBottomSheet(onDismissRequest = { showCate2Sheet = false }) {
      val list = selectedCate1?.cate2List.orEmpty()
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        item {
          Text("选择分类", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        }
        items(list, key = { it.gid }) { c2 ->
          val selected = c2.gid == selectedCate2?.gid
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
      }
    }
  }

  PullToRefreshBox(
    refreshing = refreshing,
    onRefresh = {
      if (refreshing || loading) return@PullToRefreshBox
      scope.launch {
        refreshing = true
        runCatching {
          loadPage(reset = true)
          gridState.scrollToItem(0)
        }
        refreshing = false
      }
    },
    modifier = modifier.fillMaxSize(),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(categories, key = { it.name }) { c1 ->
          CategoryPill(
            label = c1.name,
            selected = c1.name == selectedCate1?.name,
            onClick = {
              selectedCate1 = c1
              selectedCate2 = c1.cate2List.firstOrNull()
            },
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))
      val currentPartition: SubscribedPartition? = selectedCate2?.let {
        SubscribedPartition(
          id = "huya:${it.gid}",
          name = it.name,
          platform = Platform.Huya,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "当前:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
          )
          Text(
            text = selectedCate2?.name ?: "选择分类",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
              .clickable(enabled = selectedCate1 != null) { showCate2Sheet = true },
          )
          IconButton(onClick = { if (selectedCate1 != null) showCate2Sheet = true }) {
            Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "更多分类")
          }
        }

        if (currentPartition != null) {
          val subscribed = appState.isPartitionSubscribed(currentPartition)
          TextButton(onClick = { appState.togglePartition(currentPartition) }) {
            Text(text = if (subscribed) "已订阅" else "订阅")
          }
        }
      }
      Spacer(modifier = Modifier.height(8.dp))

      LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (loading || appState.platformSwitchLoading) {
          items(6, span = { GridItemSpan(1) }) {
            StreamerCardSkeleton()
          }
        } else {
          items(rooms.size, key = { rooms[it].roomId }, span = { GridItemSpan(1) }) { index ->
            val streamer = rooms[index]
            StreamerCard(
              streamer = streamer,
              followed = appState.isFollowed(streamer),
              onClick = { appState.openPlayer(streamer, partition = currentPartition) },
              onToggleFollow = { appState.toggleFollow(streamer) },
            )
          }

          item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(4.dp))
            when {
              loadingMore -> Text("加载更多…", style = MaterialTheme.typography.bodyMedium)
              hasMore -> Text("继续滑动加载更多", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
              else -> Spacer(modifier = Modifier.height(12.dp))
            }
          }
        }
      }
    }
  }
}
