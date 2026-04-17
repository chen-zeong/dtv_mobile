package dtv.mobile.ui.screens.bilibili

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dtv.mobile.model.Streamer
import dtv.mobile.repo.BilibiliCate1
import dtv.mobile.repo.BilibiliCate2
import dtv.mobile.repo.BilibiliQrStatus
import dtv.mobile.repo.PagedResult
import dtv.mobile.state.AppState
import dtv.mobile.ui.components.QrCodeImage
import dtv.mobile.ui.components.StreamerCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliHomeScreen(
  appState: AppState,
  modifier: Modifier = Modifier,
) {
  var categories: List<BilibiliCate1> by remember { mutableStateOf(emptyList()) }
  var selectedCate1: BilibiliCate1? by remember { mutableStateOf(null) }
  var selectedCate2: BilibiliCate2? by remember { mutableStateOf(null) }
  var showCate2Sheet by remember { mutableStateOf(false) }

  var rooms by remember { mutableStateOf<List<Streamer>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var loadingMore by remember { mutableStateOf(false) }
  var hasMore by remember { mutableStateOf(true) }
  var page by remember { mutableIntStateOf(1) }

  var showLoginSheet by remember { mutableStateOf(false) }
  var loggedIn by remember { mutableStateOf(false) }

  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()

  suspend fun loadPage(reset: Boolean) {
    val cate2 = selectedCate2 ?: return
    if (reset) {
      rooms = emptyList()
      hasMore = true
      page = 1
    }
    if (!hasMore) return
    if (reset) loading = true else loadingMore = true

    val resp: PagedResult<Streamer> = appState.repo.fetchBilibiliLiveList(
      parentAreaId = cate2.parentAreaId,
      areaId = cate2.areaId,
      page = page,
      pageSize = PAGE_SIZE,
    )
    rooms = if (reset) resp.items else rooms + resp.items
    hasMore = resp.items.isNotEmpty()
    page += 1
    if (reset) loading = false else loadingMore = false
  }

  LaunchedEffect(Unit) {
    loggedIn = !appState.repo.getBilibiliCookie().isNullOrBlank()
  }

  LaunchedEffect(Unit) {
    loading = true
    val data = appState.repo.fetchBilibiliCategories()
    categories = data
    selectedCate1 = data.firstOrNull()
    selectedCate2 = selectedCate1?.cate2List?.firstOrNull()
    loading = false
  }

  LaunchedEffect(selectedCate2?.parentAreaId, selectedCate2?.areaId) {
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

  if (showLoginSheet) {
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var qrKey by remember { mutableStateOf<String?>(null) }
    var qrStatus by remember { mutableStateOf(BilibiliQrStatus.Waiting) }
    var qrMessage by remember { mutableStateOf<String?>(null) }
    var polling by remember { mutableStateOf(false) }
    var regen by remember { mutableIntStateOf(0) }

    LaunchedEffect(regen) {
      polling = false
      qrUrl = null
      qrKey = null
      qrStatus = BilibiliQrStatus.Waiting
      qrMessage = "正在生成二维码…"
      polling = true
      qrStatus = BilibiliQrStatus.Waiting
      runCatching { appState.repo.generateBilibiliQrCode() }
        .onSuccess {
          qrUrl = it.url
          qrKey = it.qrcodeKey
          qrMessage = "请使用 B站 App 扫码登录"
        }
        .onFailure { qrMessage = it.message ?: "二维码生成失败" }
    }

    LaunchedEffect(qrKey) {
      val key = qrKey ?: return@LaunchedEffect
      while (polling) {
        val r = runCatching { appState.repo.pollBilibiliQrCode(key) }.getOrNull()
        if (r != null) {
          qrStatus = r.status
          qrMessage = r.message
          if (r.status == BilibiliQrStatus.Confirmed) {
            loggedIn = !appState.repo.getBilibiliCookie().isNullOrBlank()
            break
          }
          if (r.status == BilibiliQrStatus.Expired || r.status == BilibiliQrStatus.Failed) break
        }
        delay(1600)
      }
      polling = false
    }

    ModalBottomSheet(
      onDismissRequest = { polling = false; showLoginSheet = false },
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("B站扫码登录", style = MaterialTheme.typography.titleMedium)
        if (!qrUrl.isNullOrBlank()) {
          QrCodeImage(data = qrUrl!!, modifier = Modifier.fillMaxWidth().height(260.dp))
        }
        Text(
          text = when (qrStatus) {
            BilibiliQrStatus.Waiting -> qrMessage ?: "未扫码"
            BilibiliQrStatus.Scanned -> qrMessage ?: "已扫码，等待确认"
            BilibiliQrStatus.Confirmed -> "登录成功"
            BilibiliQrStatus.Expired -> "二维码已过期，请重新生成"
            BilibiliQrStatus.Failed -> qrMessage ?: "登录失败"
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          TextButton(onClick = { polling = false; showLoginSheet = false }) { Text("关闭") }
          TextButton(
            onClick = {
              regen += 1
            },
          ) { Text("重新生成") }
        }
        Spacer(modifier = Modifier.height(18.dp))
      }
    }
  }

  if (showCate2Sheet) {
    ModalBottomSheet(onDismissRequest = { showCate2Sheet = false }) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("选择分区", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        selectedCate1?.cate2List.orEmpty().forEach { c2 ->
          val selected = c2.parentAreaId == selectedCate2?.parentAreaId && c2.areaId == selectedCate2?.areaId
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      TextButton(onClick = { showLoginSheet = true }) { Text(if (loggedIn) "已登录" else "登录", style = MaterialTheme.typography.titleMedium) }
      if (loggedIn) {
        TextButton(onClick = { scope.launch { appState.repo.clearBilibiliCookie(); loggedIn = false } }) { Text("退出") }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      items(categories, key = { it.parentAreaId }) { c1 ->
        FilterChip(
          selected = c1.parentAreaId == selectedCate1?.parentAreaId,
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
      Text(text = selectedCate2?.name ?: "选择分区", style = MaterialTheme.typography.titleMedium)
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
              platform = dtv.mobile.model.Platform.Bilibili,
              roomId = "loading-$idx",
              name = "加载中…",
              title = "正在请求B站数据",
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
