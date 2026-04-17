package dtv.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.DouyuPlayInfo
import dtv.mobile.state.AppState
import dtv.mobile.ui.components.NetworkImage
import dtv.mobile.ui.player.StreamPlayer
import dtv.mobile.ui.system.FullscreenEffect
import dtv.mobile.ui.system.PlatformBackHandler
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
  appState: AppState,
  streamer: Streamer?,
  modifier: Modifier = Modifier,
) {
  var url by remember(streamer?.roomId) { mutableStateOf<String?>(null) }
  var error by remember(streamer?.roomId) { mutableStateOf<String?>(null) }
  var loading by remember(streamer?.roomId) { mutableStateOf(false) }
  var playInfo by remember(streamer?.roomId) { mutableStateOf<DouyuPlayInfo?>(null) }
  var selectedQuality by remember(streamer?.roomId) { mutableStateOf<String?>(null) }
  var selectedCdn by remember(streamer?.roomId) { mutableStateOf<String?>(null) }
  var showSettings by remember(streamer?.roomId) { mutableStateOf(false) }

  var danmakuEnabled by remember(streamer?.roomId) { mutableStateOf(true) }
  var danmakuMessages by remember(streamer?.roomId) { mutableStateOf<List<DanmakuMessage>>(emptyList()) }
  var danmakuMax by remember { mutableIntStateOf(200) }
  var videoAspectRatio by remember(streamer?.roomId) { mutableStateOf<Float?>(null) }
  var fullscreen by remember(streamer?.roomId) { mutableStateOf(false) }

  val scope = rememberCoroutineScope()

  FullscreenEffect(enabled = fullscreen)
  PlatformBackHandler(enabled = fullscreen) { fullscreen = false }

  DisposableEffect(Unit) {
    onDispose { appState.playerFullscreen = false }
  }
  LaunchedEffect(fullscreen) {
    appState.playerFullscreen = fullscreen
  }

  LaunchedEffect(streamer?.roomId) {
    val s = streamer ?: return@LaunchedEffect
    loading = true
    error = null
    url = null
    playInfo = null
    selectedQuality = null
    selectedCdn = null
    when (s.platform) {
      Platform.Douyu -> {
        runCatching { appState.repo.fetchDouyuPlayInfo(roomId = s.roomId) }
          .onSuccess { playInfo = it }
          .onFailure { error = it.message ?: "获取清晰度信息失败" }
        runCatching { appState.repo.resolveDouyuStreamUrl(roomId = s.roomId) }
          .onSuccess { url = it }
          .onFailure { error = it.message ?: "获取播放地址失败" }
      }
      Platform.Huya -> {
        runCatching { appState.repo.resolveHuyaStreamUrl(roomId = s.roomId) }
          .onSuccess { url = it }
          .onFailure { error = it.message ?: "获取虎牙播放地址失败" }
      }
      Platform.Douyin -> {
        runCatching { appState.repo.resolveDouyinStreamUrl(webRid = s.roomId) }
          .onSuccess { url = it }
          .onFailure { error = it.message ?: "获取抖音播放地址失败" }
      }
      Platform.Bilibili -> {
        runCatching { appState.repo.resolveBilibiliStreamUrl(roomId = s.roomId) }
          .onSuccess { url = it }
          .onFailure { error = it.message ?: "获取B站播放地址失败" }
      }
      else -> {
        error = "暂不支持的平台：${s.platform.title}"
      }
    }
    loading = false
  }

  LaunchedEffect(streamer?.roomId, streamer?.platform, danmakuEnabled) {
    val s = streamer ?: return@LaunchedEffect
    if (!danmakuEnabled) return@LaunchedEffect

    val flow = when (s.platform) {
      Platform.Douyu -> appState.repo.observeDouyuDanmaku(s.roomId)
      Platform.Huya -> appState.repo.observeHuyaDanmaku(s.roomId)
      Platform.Douyin -> appState.repo.observeDouyinDanmaku(s.roomId)
      Platform.Bilibili -> appState.repo.observeBilibiliDanmaku(s.roomId)
      else -> null
    } ?: return@LaunchedEffect

    danmakuMessages = emptyList()
    flow.collectLatest { msg ->
      danmakuMessages = (danmakuMessages + msg).takeLast(danmakuMax)
    }
  }

  fun reloadUrl() {
    val s = streamer ?: return
    scope.launch {
      loading = true
      error = null
      url = null
      val result = when (s.platform) {
        Platform.Douyu -> runCatching {
          appState.repo.resolveDouyuStreamUrl(
            roomId = s.roomId,
            quality = selectedQuality,
            cdn = selectedCdn,
          )
        }
        Platform.Huya -> runCatching { appState.repo.resolveHuyaStreamUrl(roomId = s.roomId) }
        Platform.Douyin -> runCatching { appState.repo.resolveDouyinStreamUrl(webRid = s.roomId) }
        Platform.Bilibili -> runCatching { appState.repo.resolveBilibiliStreamUrl(roomId = s.roomId) }
        else -> Result.failure(IllegalStateException("暂不支持的平台：${s.platform.title}"))
      }
      result
        .onSuccess { url = it }
        .onFailure { error = it.message ?: "获取播放地址失败" }
      loading = false
    }
  }

  if (showSettings && streamer != null && streamer.platform == Platform.Douyu) {
    ModalBottomSheet(onDismissRequest = { showSettings = false }) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("播放设置", style = MaterialTheme.typography.titleMedium)

        Text("清晰度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrap(
          items = listOf("自动" to null) + (playInfo?.variants.orEmpty().map { it.name to it.name }),
          selected = selectedQuality,
          onSelect = { selectedQuality = it },
        )

        Text("线路", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrap(
          items = listOf("自动" to null) + (playInfo?.cdns.orEmpty().map { it to it }),
          selected = selectedCdn,
          onSelect = { selectedCdn = it },
        )

        Text("弹幕", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrap(
          items = listOf("开" to "on", "关" to "off"),
          selected = if (danmakuEnabled) "on" else "off",
          onSelect = { danmakuEnabled = it == "on" },
        )

        TextButton(onClick = { showSettings = false; reloadUrl() }, modifier = Modifier.fillMaxWidth()) { Text("应用并重载") }
        SpacerLine()
      }
    }
  }

  val layoutAspect = videoAspectRatio?.takeIf { it > 0f } ?: (16f / 9f)
  val videoAspectForDanmaku = videoAspectRatio?.takeIf { it > 0f } ?: 0.75f
  val isVerticalVideo = videoAspectForDanmaku < 1f
  val isHorizontalVideo = !isVerticalVideo

  Column(
    modifier = Modifier
      .fillMaxSize()
      .then(if (fullscreen) Modifier.background(Color.Black) else Modifier)
      .then(if (fullscreen) modifier else modifier.padding(14.dp)),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val videoSurfaceShape = if (fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp)
    val videoSurfaceColor = if (fullscreen) Color.Black else MaterialTheme.colorScheme.secondary
    val videoSurfaceModifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(layoutAspect)

    Surface(shape = videoSurfaceShape, color = videoSurfaceColor, modifier = videoSurfaceModifier) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (url != null) {
          StreamPlayer(
            url = url!!,
            fullscreen = fullscreen,
            onVideoAspectRatioChanged = { videoAspectRatio = it },
            onError = {
              if (it.startsWith("__retry_http__:")) {
                error = null
                url = it.removePrefix("__retry_http__:")
              } else {
                error = it
                url = null
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = when {
                streamer == null -> "未选择直播间"
                loading -> "正在获取播放地址…"
                error != null -> error!!
                else -> "准备播放…"
              },
              style = MaterialTheme.typography.titleMedium,
              color = if (fullscreen) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f),
            )
          }
        }

        val overlayDanmaku = danmakuEnabled && danmakuMessages.isNotEmpty() && (fullscreen || isVerticalVideo)
        if (overlayDanmaku) {
          if (fullscreen && isHorizontalVideo) {
            ScrollingDanmakuOverlay(
              resetKey = streamer?.roomId,
              messages = danmakuMessages,
              modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            )
          } else {
            DanmakuOverlay(
              messages = danmakuMessages,
              modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            )
          }
        }

        PlayerTopOverlay(
          streamer = streamer,
          fullscreen = fullscreen,
          onToggleFullscreen = { fullscreen = !fullscreen },
          onOpenSettings = { showSettings = true },
          onReload = { reloadUrl() },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    if (!fullscreen) {
      if (danmakuEnabled && danmakuMessages.isNotEmpty() && isHorizontalVideo) {
        DanmakuBelowPanel(
          messages = danmakuMessages,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (!streamer?.title.isNullOrBlank()) {
        Text(
          text = streamer?.title.orEmpty(),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
        )
      }
    }
  }
}

@Composable
private fun PlayerTopOverlay(
  streamer: Streamer?,
  fullscreen: Boolean,
  onToggleFullscreen: () -> Unit,
  onOpenSettings: () -> Unit,
  onReload: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bg = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent))
  Row(
    modifier = modifier
      .background(bg)
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      val avatar = normalizeHttpUrl(streamer?.avatarUrl)
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
      ) {
        if (avatar != null) {
          NetworkImage(url = avatar, contentDescription = streamer?.name, modifier = Modifier.matchParentSize())
        } else {
          Text(text = streamer?.name?.take(1).orEmpty(), color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
      }
      Column(modifier = Modifier.padding(start = 10.dp)) {
        Text(
          text = streamer?.name.orEmpty(),
          style = MaterialTheme.typography.titleSmall,
          color = Color.White.copy(alpha = 0.95f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!streamer?.viewerText.isNullOrBlank()) {
          Text(
            text = streamer?.viewerText.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      IconButton(onClick = onReload) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = "重载", tint = Color.White.copy(alpha = 0.92f))
      }
      if (streamer?.platform == Platform.Douyu) {
        IconButton(onClick = onOpenSettings) {
          Icon(imageVector = Icons.Default.Settings, contentDescription = "设置", tint = Color.White.copy(alpha = 0.92f))
        }
      }
      IconButton(onClick = onToggleFullscreen) {
        Icon(
          imageVector = if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
          contentDescription = if (fullscreen) "退出全屏" else "全屏",
          tint = Color.White.copy(alpha = 0.92f),
        )
      }
    }
  }
}

@Composable
private fun DanmakuOverlay(
  messages: List<DanmakuMessage>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.Bottom,
  ) {
    messages.takeLast(14).forEach { msg ->
      Text(
        text = "${msg.user}: ${msg.content}",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White,
        modifier = Modifier
          .background(Color.Black.copy(alpha = 0.22f), shape = RoundedCornerShape(10.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        maxLines = 1,
      )
      SpacerLine(6.dp)
    }
  }
}

@Composable
private fun DanmakuBelowPanel(
  messages: List<DanmakuMessage>,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val panelHeight = if (expanded) 420.dp else 220.dp
  val displayMessages = messages.asReversed()

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    tonalElevation = 0.dp,
    shadowElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          "弹幕（${messages.size}）",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        TextButton(onClick = { expanded = !expanded }) {
          Text(if (expanded) "收起" else "展开")
        }
      }
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(panelHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(displayMessages) { msg ->
          Text(
            text = "${msg.user}: ${msg.content}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun ScrollingDanmakuOverlay(
  resetKey: Any?,
  messages: List<DanmakuMessage>,
  modifier: Modifier = Modifier,
) {
  data class Active(
    val id: Long,
    val text: String,
    val track: Int,
  )

  val active = remember(resetKey) { mutableStateListOf<Active>() }
  var lastCount by remember(resetKey) { mutableIntStateOf(0) }
  var nextTrack by remember(resetKey) { mutableIntStateOf(0) }
  val trackCount = 8
  val maxActive = 30

  LaunchedEffect(messages.size) {
    if (messages.size <= lastCount) {
      lastCount = messages.size
      return@LaunchedEffect
    }
    val newItems = messages.subList(lastCount, messages.size)
    newItems.forEach { msg ->
      val text = "${msg.user}: ${msg.content}".trim()
      if (text.isNotEmpty()) {
        if (active.size >= maxActive) active.removeAt(0)
        active.add(
          Active(
            id = System.nanoTime(),
            text = text,
            track = nextTrack,
          ),
        )
        nextTrack = (nextTrack + 1) % trackCount
      }
    }
    lastCount = messages.size
  }

  BoxWithConstraints(modifier = modifier) {
    val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val topPaddingPx = heightPx * 0.18f
    val trackHeightPx = (heightPx * 0.52f) / trackCount

    active.forEach { item ->
      key(item.id) {
        ScrollingDanmakuItem(
          text = item.text,
          startX = widthPx,
          endX = -widthPx,
          y = topPaddingPx + trackHeightPx * item.track,
          onFinished = { active.remove(item) },
        )
      }
    }
  }
}

@Composable
private fun ScrollingDanmakuItem(
  text: String,
  startX: Float,
  endX: Float,
  y: Float,
  onFinished: () -> Unit,
) {
  val x = remember { Animatable(startX) }
  LaunchedEffect(text, startX, endX) {
    x.snapTo(startX)
    x.animateTo(
      targetValue = endX,
      animationSpec = tween(durationMillis = 9000, easing = LinearEasing),
    )
    onFinished()
  }

  Box(
    modifier = Modifier.offset {
      IntOffset(x.value.roundToInt(), y.roundToInt())
    },
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = Color.White,
      modifier = Modifier
        .background(Color.Black.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      maxLines = 1,
    )
  }
}

@Composable
private fun SpacerLine(height: androidx.compose.ui.unit.Dp = 10.dp) {
  androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(height))
}

@Composable
private fun RowWrap(
  items: List<Pair<String, String?>>,
  selected: String?,
  onSelect: (String?) -> Unit,
) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(items, key = { it.first }) { (label, value) ->
      val isSelected = value == selected || (value == null && selected == null)
      FilterChip(
        selected = isSelected,
        onClick = { onSelect(value) },
        label = { Text(label) },
      )
    }
  }
}
