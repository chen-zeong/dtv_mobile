package dtv.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dtv.mobile.state.AppState
import dtv.mobile.ui.components.HomeStreamerCard
import dtv.mobile.ui.components.NetworkImage
import dtv.mobile.ui.components.PullToRefreshBox
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
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
  val pinnedKeys = appState.pinnedFollowedStreamerKeys.toList()
  val pinnedStreamers = run {
    val snapshot = pinnedKeys.mapNotNull { key -> displayItems.firstOrNull { "${it.platform.name}:${it.roomId}" == key } }
    val live = snapshot.filter { it.isLive }
    val offline = snapshot.filterNot { it.isLive }
    live + offline
  }
  var showPinnedPicker by remember { mutableStateOf(false) }
  var refreshing by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val gridState = rememberLazyGridState()

  var draggingKey by remember { mutableStateOf<String?>(null) }
  var dragOffset by remember { mutableStateOf(Offset.Zero) }
  var moveCount by remember { mutableIntStateOf(0) }

  if (items.isEmpty()) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(
        text = "暂无订阅主播",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
      )
    }
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
    LazyVerticalGrid(
      modifier = Modifier.fillMaxSize(),
      state = gridState,
      columns = GridCells.Fixed(2),
      contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 92.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(span = { GridItemSpan(2) }) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          lazyItems(pinnedStreamers, key = { "${it.platform}-${it.roomId}" }) { streamer ->
            PinnedAvatarItem(
              nickname = streamer.name,
              avatarUrl = streamer.avatarUrl,
              isLive = streamer.isLive,
              onClick = { appState.openPlayer(streamer) },
            )
          }
          item(key = "pinned-add") {
            PinnedAddItem(onClick = { showPinnedPicker = true })
          }
        }
      }

      items(displayItems, key = { "${it.platform}-${it.roomId}" }) { streamer ->
        val itemKey = "${streamer.platform}-${streamer.roomId}"
        val isDragging = draggingKey == itemKey
        HomeStreamerCard(
          streamer = streamer,
          followed = true,
          onClick = { if (draggingKey == null) appState.openPlayer(streamer) },
          onToggleFollow = { appState.toggleFollow(streamer) },
          modifier = Modifier
            .then(
              if (isDragging) {
                Modifier
                  .zIndex(2f)
                  .offset { IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
              } else {
                Modifier
              },
            )
            .pointerInput(itemKey, moveCount) {
              detectDragGesturesAfterLongPress(
                onDragStart = {
                  draggingKey = itemKey
                  dragOffset = Offset.Zero
                },
                onDragCancel = {
                  draggingKey = null
                  dragOffset = Offset.Zero
                },
                onDragEnd = {
                  draggingKey = null
                  dragOffset = Offset.Zero
                },
                onDrag = { change, dragAmount ->
                  change.consume()
                  if (draggingKey != itemKey) return@detectDragGesturesAfterLongPress

                  dragOffset += dragAmount

                  val fromIndex = displayItems.indexOfFirst { "${it.platform}-${it.roomId}" == itemKey }
                  if (fromIndex < 0) return@detectDragGesturesAfterLongPress

                  val draggingInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == fromIndex + 1 }
                    ?: return@detectDragGesturesAfterLongPress
                  val draggingStreamer = displayItems.getOrNull(fromIndex) ?: return@detectDragGesturesAfterLongPress
                  val draggingIsLive = draggingStreamer.isLive

                  val draggingCenter = Offset(
                    x = draggingInfo.offset.x + dragOffset.x + draggingInfo.size.width / 2f,
                    y = draggingInfo.offset.y + dragOffset.y + draggingInfo.size.height / 2f,
                  )

                  val targetInfo = gridState.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .filter { it.index in 1..displayItems.lastIndex + 1 }
                    .firstOrNull { info ->
                      if (info.index == draggingInfo.index) return@firstOrNull false
                      val idx = info.index - 1
                      val s = displayItems.getOrNull(idx) ?: return@firstOrNull false
                      if (s.isLive != draggingIsLive) return@firstOrNull false
                      val left = info.offset.x.toFloat()
                      val top = info.offset.y.toFloat()
                      val right = left + info.size.width
                      val bottom = top + info.size.height
                      draggingCenter.x in left..right && draggingCenter.y in top..bottom
                    }

                  val toIndex = targetInfo?.index?.minus(1) ?: return@detectDragGesturesAfterLongPress
                  if (toIndex == fromIndex) return@detectDragGesturesAfterLongPress
                  val targetStreamer = displayItems.getOrNull(toIndex) ?: return@detectDragGesturesAfterLongPress

                  val diff = Offset(
                    x = (draggingInfo.offset.x - targetInfo.offset.x).toFloat(),
                    y = (draggingInfo.offset.y - targetInfo.offset.y).toFloat(),
                  )

                  val fromBase = items.indexOfFirst { "${it.platform}-${it.roomId}" == itemKey }
                  val toBase = items.indexOfFirst { "${it.platform}-${it.roomId}" == "${targetStreamer.platform}-${targetStreamer.roomId}" }
                  if (fromBase < 0 || toBase < 0) return@detectDragGesturesAfterLongPress
                  appState.moveFollowedStreamer(fromIndex = fromBase, toIndex = toBase)
                  dragOffset += diff
                  moveCount += 1
                },
              )
            },
        )
      }

      item(span = { GridItemSpan(2) }) {
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }

  if (showPinnedPicker) {
    PinnedStreamerPickerDialog(
      items = displayItems,
      initialSelectedKeys = pinnedKeys,
      onDismiss = { showPinnedPicker = false },
      onConfirm = { keys ->
        appState.setPinnedFollowedStreamers(keys)
        showPinnedPicker = false
      },
    )
  }
}

@Composable
private fun HomeAvatarItem(
  nickname: String,
  avatarUrl: String?,
  isLive: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accent = MaterialTheme.colorScheme.primary
  val bg = MaterialTheme.colorScheme.background
  val avatar = normalizeHttpUrl(avatarUrl)

  Column(
    modifier = modifier
      .width(60.dp)
      .clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
    ) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .clip(CircleShape)
          .border(width = 2.dp, color = accent, shape = CircleShape)
          .padding(2.dp)
          .clip(CircleShape)
          .border(width = 2.dp, color = bg, shape = CircleShape)
          .clip(CircleShape),
      ) {
        if (avatar != null) {
          NetworkImage(url = avatar, contentDescription = nickname, modifier = Modifier.matchParentSize())
        } else {
          Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            Text(text = nickname.take(1), style = MaterialTheme.typography.titleMedium)
          }
        }
      }

      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .offset(x = 1.dp, y = 1.dp)
          .size(14.dp)
          .clip(CircleShape)
          .background(if (isLive) accent else Color(0xFF9CA3AF))
          .border(width = 2.dp, color = bg, shape = CircleShape),
      )
    }

    Text(
      text = nickname,
      fontSize = 9.sp,
      fontWeight = FontWeight.Black,
      letterSpacing = (-0.2).sp,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun PinnedAvatarItem(
  nickname: String,
  avatarUrl: String?,
  isLive: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  HomeAvatarItem(
    nickname = nickname,
    avatarUrl = avatarUrl,
    isLive = isLive,
    onClick = onClick,
    modifier = modifier,
  )
}

@Composable
private fun PinnedAddItem(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
  Column(
    modifier = modifier
      .width(60.dp)
      .clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .drawBehind {
          val strokeWidth = 1.5.dp.toPx()
          val radius = (size.minDimension - strokeWidth) / 2f
          drawCircle(
            color = outline,
            radius = radius,
            style = Stroke(
              width = strokeWidth,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()), 0f),
            ),
          )
        },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "添加置顶",
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
      )
    }
    Text(
      text = "添加",
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    )
  }
}

@Composable
private fun PinnedStreamerPickerDialog(
  items: List<dtv.mobile.model.Streamer>,
  initialSelectedKeys: List<String>,
  onDismiss: () -> Unit,
  onConfirm: (List<String>) -> Unit,
) {
  var selected by remember { mutableStateOf(initialSelectedKeys.toSet()) }
  val scrollState = rememberScrollState()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("选择置顶主播") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (items.isEmpty()) {
          Text("暂无关注主播。", style = MaterialTheme.typography.bodyMedium)
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { streamer ->
              val key = "${streamer.platform.name}:${streamer.roomId}"
              val checked = selected.contains(key)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    selected = if (checked) selected - key else selected + key
                  }
                  .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = { next ->
                    selected = if (next) selected + key else selected - key
                  },
                )
                val avatar = normalizeHttpUrl(streamer.avatarUrl)
                Box(
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                  contentAlignment = Alignment.Center,
                ) {
                  if (avatar != null) {
                    NetworkImage(url = avatar, contentDescription = streamer.name, modifier = Modifier.fillMaxSize())
                  } else {
                    Text(text = streamer.name.take(1), style = MaterialTheme.typography.titleMedium)
                  }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(streamer.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                  Text(
                    "${streamer.platform.title} · ${if (streamer.isLive) "直播中" else "未开播"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(selected.toList()) }) { Text("完成") }
    },
    dismissButton = {
      FilledTonalButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

// (removed) end-of-feed footer
