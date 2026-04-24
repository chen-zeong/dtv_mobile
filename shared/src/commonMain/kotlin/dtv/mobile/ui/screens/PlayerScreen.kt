package dtv.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.DouyuPlayInfo
import dtv.mobile.state.AppState
import dtv.mobile.ui.components.DtvBackground
import dtv.mobile.ui.components.NetworkImage
import dtv.mobile.ui.player.StreamPlayer
import dtv.mobile.ui.system.FullscreenEffect
import dtv.mobile.ui.system.PlatformBackHandler
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
  var selectedDouyuQuality by remember(streamer?.roomId) { mutableStateOf<String?>(null) }
  var selectedDouyuCdn by remember(streamer?.roomId) { mutableStateOf<String?>(null) }
  var selectedDouyinQuality by remember(streamer?.roomId) { mutableStateOf<String?>(null) }
  var selectedBilibiliQn by remember(streamer?.roomId) { mutableStateOf<Int?>(null) }
  var showSettings by remember(streamer?.roomId) { mutableStateOf(false) }

  var danmakuEnabled by remember(streamer?.roomId) { mutableStateOf(true) }
  var danmakuMessages by remember(streamer?.roomId) { mutableStateOf<List<DanmakuMessage>>(emptyList()) }
  var danmakuMax by remember { mutableIntStateOf(200) }
  var danmakuAreaFraction by remember(streamer?.roomId) { mutableStateOf(1f / 3f) }
  var videoAspectRatio by remember(streamer?.roomId) { mutableStateOf<Float?>(null) }
  var videoReady by remember(streamer?.roomId) { mutableStateOf(false) }
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
    videoAspectRatio = null
    videoReady = false
    if (!s.isLive) {
      loading = false
      error = null
      url = null
      playInfo = null
      return@LaunchedEffect
    }
    loading = true
    error = null
    url = null
    playInfo = null
    selectedDouyuQuality = null
    selectedDouyuCdn = null
    selectedDouyinQuality = null
    selectedBilibiliQn = null
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
        runCatching { appState.repo.resolveDouyinStreamUrl(webRid = s.roomId, desiredQuality = selectedDouyinQuality) }
          .onSuccess { url = it }
          .onFailure { error = it.message ?: "获取抖音播放地址失败" }
      }
      Platform.Bilibili -> {
        runCatching { appState.repo.resolveBilibiliStreamUrl(roomId = s.roomId, qn = selectedBilibiliQn) }
          .onSuccess { url = it }
          .onFailure { error = it.message ?: "获取B站播放地址失败" }
      }
      else -> {
        error = "暂不支持的平台：${s.platform.title}"
      }
    }
    loading = false
  }

  LaunchedEffect(streamer?.roomId, streamer?.platform, danmakuEnabled, url) {
    val s = streamer ?: return@LaunchedEffect
    if (!danmakuEnabled) return@LaunchedEffect
    if (url == null) {
      danmakuMessages = emptyList()
      return@LaunchedEffect
    }

    val flow = when (s.platform) {
      Platform.Douyu -> appState.repo.observeDouyuDanmaku(s.roomId)
      Platform.Huya -> appState.repo.observeHuyaDanmaku(s.roomId)
      Platform.Douyin -> appState.repo.observeDouyinDanmaku(s.roomId)
      Platform.Bilibili -> appState.repo.observeBilibiliDanmaku(s.roomId)
      else -> null
    } ?: return@LaunchedEffect

    danmakuMessages = emptyList()
    try {
      flow.collectLatest { msg ->
        danmakuMessages = (danmakuMessages + msg).takeLast(danmakuMax)
      }
    } catch (t: Throwable) {
      if (t is CancellationException) throw t
      // Swallow network/parse errors to avoid crashing the player UI.
    }
  }

  fun reloadUrl() {
    val s = streamer ?: return
    scope.launch {
      loading = true
      error = null
      url = null
      videoAspectRatio = null
      videoReady = false
      val result = when (s.platform) {
        Platform.Douyu -> runCatching {
          appState.repo.resolveDouyuStreamUrl(
            roomId = s.roomId,
            quality = selectedDouyuQuality,
            cdn = selectedDouyuCdn,
          )
        }
        Platform.Huya -> runCatching { appState.repo.resolveHuyaStreamUrl(roomId = s.roomId) }
        Platform.Douyin -> runCatching { appState.repo.resolveDouyinStreamUrl(webRid = s.roomId, desiredQuality = selectedDouyinQuality) }
        Platform.Bilibili -> runCatching { appState.repo.resolveBilibiliStreamUrl(roomId = s.roomId, qn = selectedBilibiliQn) }
        else -> Result.failure(IllegalStateException("暂不支持的平台：${s.platform.title}"))
      }
      result
        .onSuccess { url = it }
        .onFailure { error = it.message ?: "获取播放地址失败" }
      loading = false
    }
  }
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .then(modifier),
  ) {
    val isPortraitLayout = maxHeight >= maxWidth
    val isLandscapeLayout = !isPortraitLayout

    if (showSettings && streamer != null) {
      ModalBottomSheet(onDismissRequest = { showSettings = false }) {
        Column(
          modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("播放设置", style = MaterialTheme.typography.titleMedium)

          if (streamer.platform == Platform.Douyu || streamer.platform == Platform.Douyin || streamer.platform == Platform.Bilibili) {
            Text("画质", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            when (streamer.platform) {
              Platform.Douyu -> {
                RowWrap(
                  items = listOf("自动" to null) + (playInfo?.variants.orEmpty().map { it.name to it.name }),
                  selected = selectedDouyuQuality,
                  onSelect = {
                    selectedDouyuQuality = it
                    reloadUrl()
                  },
                )
              }
              Platform.Douyin -> {
                RowWrap(
                  items = listOf(
                    "自动" to null,
                    "原画" to "ORIGIN",
                    "超清" to "FULL_HD1",
                    "高清" to "HD1",
                    "标清" to "SD1",
                  ),
                  selected = selectedDouyinQuality,
                  onSelect = {
                    selectedDouyinQuality = it
                    reloadUrl()
                  },
                )
              }
              Platform.Bilibili -> {
                RowWrapInt(
                  items = listOf(
                    "自动" to null,
                    "原画" to 10000,
                    "蓝光" to 400,
                    "超清" to 250,
                    "高清" to 150,
                    "流畅" to 80,
                  ),
                  selected = selectedBilibiliQn,
                  onSelect = {
                    selectedBilibiliQn = it
                    reloadUrl()
                  },
                )
              }
              else -> Unit
            }
          }

          if (streamer.platform == Platform.Douyu) {
            Text("线路", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            RowWrap(
              items = listOf("自动" to null) + (playInfo?.cdns.orEmpty().map { it to it }),
              selected = selectedDouyuCdn,
              onSelect = {
                selectedDouyuCdn = it
                reloadUrl()
              },
            )
          }

          Text("弹幕", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
          RowWrap(
            items = listOf("开" to "on", "关" to "off"),
            selected = if (danmakuEnabled) "on" else "off",
            onSelect = { danmakuEnabled = it == "on" },
          )

          if (isLandscapeLayout) {
            Text("弹幕位置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            RowWrapFloat(
              items = listOf(
                "上方1/4" to 0.25f,
                "上方1/3" to (1f / 3f),
                "上方1/2" to 0.5f,
                "上方3/4" to 0.75f,
              ),
              selected = danmakuAreaFraction,
              onSelect = { danmakuAreaFraction = it },
            )
          }

          SpacerLine()
        }
      }
    }

    val effectiveAspect = videoAspectRatio?.takeIf { it > 0f }
    val isVideoAspectKnown = effectiveAspect != null
    val layoutAspect = effectiveAspect ?: (16f / 9f)
    val isVerticalVideo = isVideoAspectKnown && (effectiveAspect!! < 1f)
    val isHorizontalVideo = isVideoAspectKnown && !isVerticalVideo
    val verticalFullBleed = !fullscreen && isVerticalVideo

    val content: @Composable () -> Unit = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .then(if (fullscreen) Modifier.background(Color.Black) else Modifier),
      ) {
        if (!fullscreen && !verticalFullBleed) {
          PlayerHeader(
            streamer = streamer,
            onBack = appState::back,
            followed = streamer?.let(appState::isFollowed) == true,
            onToggleFollow = { s -> appState.toggleFollow(s) },
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(10.dp))
        }

        val videoSurfaceShape = RoundedCornerShape(0.dp)
        val videoSurfaceColor = Color.Black
        val videoSurfaceModifier = if (fullscreen) {
          Modifier.fillMaxSize()
        } else if (verticalFullBleed) {
          Modifier.fillMaxSize()
        } else {
          Modifier.fillMaxWidth().aspectRatio(layoutAspect)
        }

        val canShowDanmaku = danmakuEnabled &&
          danmakuMessages.isNotEmpty() &&
          isVideoAspectKnown &&
          videoReady &&
          url != null &&
          !loading &&
          error == null

        Surface(
          shape = videoSurfaceShape,
          color = videoSurfaceColor,
          modifier = videoSurfaceModifier.animateContentSize(animationSpec = tween(durationMillis = 260)),
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            if (url != null) {
              StreamPlayer(
                url = url!!,
                fullscreen = fullscreen,
                liveMode = true,
                zoomToFill = verticalFullBleed,
                onVideoAspectRatioChanged = {
                  videoAspectRatio = it
                  if (it != null && it > 0f) videoReady = true
                },
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
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(16.dp),
                contentAlignment = Alignment.Center,
              ) {
                when {
                  streamer == null -> {
                    Text(
                      text = "未选择直播间",
                      style = MaterialTheme.typography.titleMedium,
                      color = if (fullscreen) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f),
                    )
                  }
                streamer.isLive == false -> {
                  Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (fullscreen) Color.Black.copy(alpha = 0.30f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                  ) {
                    Column(
                      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                      Text(
                        text = "主播未开播",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = if (fullscreen) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                      )
                      Text(
                        text = "当前直播间没有在直播",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (fullscreen) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                      )
                    }
                  }
                }
                loading -> {
                  CircularProgressIndicator(
                    color = if (fullscreen) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                  )
                }
                error != null -> {
                  Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fullscreen) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f),
                  )
                }
              }
            }
          }

            if (verticalFullBleed) {
              PlayerHeader(
                streamer = streamer,
                onBack = appState::back,
                followed = streamer?.let(appState::isFollowed) == true,
                onToggleFollow = { s -> appState.toggleFollow(s) },
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .fillMaxWidth(),
                overlay = true,
              )
            }

            val overlayDanmaku = canShowDanmaku && (fullscreen || isVerticalVideo)
            if (overlayDanmaku) {
              if (fullscreen && isHorizontalVideo) {
                ScrollingDanmakuOverlay(
                  resetKey = streamer?.roomId,
                  messages = danmakuMessages,
                  showUser = false,
                  areaFraction = danmakuAreaFraction,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                )
              } else {
                DanmakuOverlay(
                  messages = danmakuMessages,
                  showUser = true,
                  areaFraction = danmakuAreaFraction,
                  transparentBackground = false,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                )
              }
            }

            PlayerSideControlsOverlay(
              fullscreen = fullscreen,
              showFullscreen = isVideoAspectKnown && !isVerticalVideo,
              onToggleFullscreen = { fullscreen = !fullscreen },
              onOpenSettings = { showSettings = true },
              onReload = { reloadUrl() },
              modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            )
          }
        }

        if (!fullscreen && !verticalFullBleed) {
          if (canShowDanmaku && isHorizontalVideo) {
            HubDanmakuPanel(
              messages = danmakuMessages,
              enhancedPortrait = isPortraitLayout,
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp, bottom = 0.dp),
            )
          }
        }
      }
    }

    if (fullscreen) content() else PlayerBackground(content = content)
  }
}

@Composable
private fun PlayerBackground(
  content: @Composable () -> Unit,
) {
  val bg = MaterialTheme.colorScheme.background
  val accent = MaterialTheme.colorScheme.primary
  val isDark = bg.luminance() < 0.35f
  if (isDark) {
    DtvBackground(content = content)
    return
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(
        brush = Brush.linearGradient(
          colors = listOf(
            bg,
            accent.copy(alpha = 0.10f),
            bg,
          ),
          start = Offset(0f, 0f),
          end = Offset(size.width, size.height),
        ),
      )
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent),
          center = Offset(size.width * 0.80f, size.height * 0.18f),
          radius = size.width * 0.70f,
        ),
        radius = size.width * 0.70f,
        center = Offset(size.width * 0.80f, size.height * 0.18f),
      )
    }
    content()
  }
}

@Composable
private fun PlayerHeader(
  streamer: Streamer?,
  onBack: () -> Unit,
  followed: Boolean,
  onToggleFollow: (Streamer) -> Unit,
  modifier: Modifier = Modifier,
  overlay: Boolean = false,
  ) {
    val liveDot = if (streamer?.isLive == true) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF)
    val glassBg = Brush.linearGradient(
      colors = listOf(
        Color.Black.copy(alpha = 0.42f),
        Color.Black.copy(alpha = 0.22f),
      ),
    )
    val glassBorder = Color.White.copy(alpha = 0.16f)

  Column(
    modifier = modifier
      .statusBarsPadding()
      .padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    val closeSize = 40.dp
    val infoShape = RoundedCornerShape(closeSize / 2)
    val infoPrimary = Color.White.copy(alpha = 0.92f)
    val infoSecondary = Color.White.copy(alpha = 0.72f)
    val closeFg = Color.White.copy(alpha = 0.92f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
      val maxInfoWidth = ((maxWidth - closeSize - 10.dp).coerceAtLeast(0.dp)) * 0.66f
      val avatarSize = closeSize - 8.dp

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          modifier = Modifier
            .widthIn(max = maxInfoWidth)
            .height(closeSize)
            .clip(infoShape),
          shape = infoShape,
          color = Color.Transparent,
          border = BorderStroke(1.dp, glassBorder),
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
        ) {
          Box(modifier = Modifier.background(glassBg)) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
          val avatar = normalizeHttpUrl(streamer?.avatarUrl)
          Box(modifier = Modifier.size(avatarSize)) {
            Box(
              modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
              contentAlignment = Alignment.Center,
            ) {
              if (avatar != null) {
                NetworkImage(url = avatar, contentDescription = streamer?.name, modifier = Modifier.matchParentSize())
              } else {
                Text(
                  text = streamer?.name?.take(1).orEmpty(),
                  color = infoPrimary,
                  style = MaterialTheme.typography.titleSmall,
                  textAlign = TextAlign.Center,
                )
              }
            }

            if (streamer != null) {
              Box(
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .offset(x = 1.dp, y = 1.dp)
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(liveDot)
                  .border(width = 2.dp, color = Color.Transparent, shape = CircleShape),
              )
            }
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
          ) {
            Text(
              text = streamer?.name.orEmpty(),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
              color = infoPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = streamer?.title?.trim().orEmpty(),
              style = MaterialTheme.typography.labelSmall,
              color = infoSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          if (streamer != null) {
            val iconTint = if (followed) Color(0xFFE11D48) else infoSecondary
            Box(
              modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .clickable { onToggleFollow(streamer) },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (followed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (followed) "已收藏" else "收藏",
                tint = iconTint,
              )
            }
          }
            }
          }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
          modifier = Modifier
            .size(closeSize)
            .clip(CircleShape)
            .clickable(onClick = onBack),
          shape = CircleShape,
          color = Color.Transparent,
          border = BorderStroke(1.dp, glassBorder),
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(glassBg),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "关闭",
              tint = closeFg,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PlayerSideControlsOverlay(
  fullscreen: Boolean,
  showFullscreen: Boolean,
  onToggleFullscreen: () -> Unit,
  onOpenSettings: () -> Unit,
  onReload: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(14.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ControlFab(icon = Icons.Default.Settings, onClick = onOpenSettings)
    ControlFab(icon = Icons.Default.Refresh, onClick = onReload)
    if (showFullscreen) {
      ControlFab(icon = Icons.Default.FullscreenExit.takeIf { fullscreen } ?: Icons.Default.Fullscreen, onClick = onToggleFullscreen)
    }
  }
}

@Composable
private fun ControlFab(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
    shape = CircleShape,
    color = Color.White.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.92f),
      )
    }
  }
}

@Composable
private fun DanmakuOverlay(
  messages: List<DanmakuMessage>,
  showUser: Boolean,
  areaFraction: Float,
  transparentBackground: Boolean,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxHeight(areaFraction)
      .clipToBounds(),
    contentAlignment = Alignment.BottomStart,
  ) {
    val maxBubbleWidth = maxWidth * 0.92f
    Column(verticalArrangement = Arrangement.Bottom) {
      messages.takeLast(10).forEach { msg ->
        DanmakuBubble(
          user = msg.user,
          content = msg.content,
          showUser = showUser,
          transparentBackground = transparentBackground,
          modifier = Modifier.widthIn(max = maxBubbleWidth),
          maxLines = 1,
          compact = true,
        )
        SpacerLine(4.dp)
      }
    }
  }
}

@Composable
private fun DanmakuBubble(
  user: String,
  content: String,
  showUser: Boolean = true,
  transparentBackground: Boolean = false,
  modifier: Modifier = Modifier,
  maxLines: Int = 1,
  compact: Boolean = false,
) {
  val displayUser = user.trim().ifBlank { "匿名" }
  val displayContent = content.trim()

  val text = buildAnnotatedString {
    if (showUser) {
      withStyle(
        SpanStyle(
          color = Color(0xFFFFE082),
          fontWeight = FontWeight.SemiBold,
        ),
      ) {
        append(displayUser)
      }
      append("  ")
    }
    append(displayContent)
  }

  val hPad = if (compact) 8.dp else 12.dp
  val vPad = if (compact) 4.dp else 8.dp
  val bubbleShape = if (compact) RoundedCornerShape(16.dp) else RoundedCornerShape(14.dp)

  Surface(
    modifier = modifier,
    shape = bubbleShape,
    color = if (transparentBackground) Color.Transparent else Color.Black.copy(alpha = 0.26f),
    border = null,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = Color.White.copy(alpha = 0.95f),
      modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun HubDanmakuPanel(
  messages: List<DanmakuMessage>,
  enhancedPortrait: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
  val itemSpacing = if (enhancedPortrait) 12.dp else 8.dp
  val bgBrush = if (isDark) {
    Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.03f),
      ),
    )
  } else {
    Brush.verticalGradient(
      colors = listOf(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
      ),
    )
  }

  val display = remember(messages) { messages.asReversed() }
  LaunchedEffect(display.size) {
    if (display.isNotEmpty()) listState.scrollToItem(index = 0)
  }

  Surface(
    modifier = modifier.fillMaxWidth().clip(shape),
    shape = shape,
    color = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    border = null,
  ) {
    Box(modifier = Modifier.background(bgBrush)) {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxSize()
          .padding(horizontal = 10.dp, vertical = 10.dp),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
      ) {
        itemsIndexed(
          display,
          key = { index, msg -> "${msg.roomId}:${msg.user}:${msg.content}:$index" },
        ) { _, msg ->
          HubDanmakuRow(
            user = msg.user.trim().ifBlank { "匿名" },
            content = msg.content.trim(),
            enhancedPortrait = enhancedPortrait,
          )
        }
      }
    }
  }
}

@Composable
private fun HubDanmakuRow(
  user: String,
  content: String,
  enhancedPortrait: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val userChipBg = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
  val userChipBorder = if (isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
  val userFg = if (isDark) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
  val contentFg = if (isDark) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
  val accent = remember(user) { accentColorForUser(user) }

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(if (enhancedPortrait) 10.dp else 8.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = userChipBg,
      border = userChipBorder,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = if (enhancedPortrait) 10.dp else 8.dp, vertical = if (enhancedPortrait) 5.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (enhancedPortrait) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(accent),
          )
        }
        Text(
          text = user,
          style = if (enhancedPortrait) {
            MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
          } else {
            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
          },
          color = userFg,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Text(
      text = content,
      style = if (enhancedPortrait) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
      } else {
        MaterialTheme.typography.bodySmall
      },
      color = contentFg,
      modifier = Modifier
        .weight(1f)
        .padding(top = 1.dp),
      maxLines = if (enhancedPortrait) 4 else 3,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun accentColorForUser(user: String): Color {
  val hue = ((user.hashCode() % 360) + 360) % 360
  return Color.hsv(hue.toFloat(), 0.65f, 0.90f)
}

@Composable
private fun ScrollingDanmakuOverlay(
  resetKey: Any?,
  messages: List<DanmakuMessage>,
  showUser: Boolean,
  areaFraction: Float,
  modifier: Modifier = Modifier,
) {
  data class Active(
    val id: Long,
    val user: String,
    val content: String,
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
      val user = msg.user.trim().ifBlank { "匿名" }
      val content = msg.content.trim()
      if (content.isNotEmpty()) {
        if (active.size >= maxActive) active.removeAt(0)
        active.add(
          Active(
            id = System.nanoTime(),
            user = user,
            content = content,
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
    val regionTopPx = heightPx * 0.04f
    val regionHeightPx = (heightPx * areaFraction).coerceAtLeast(1f)
    val usableHeightPx = (regionHeightPx - regionTopPx).coerceAtLeast(1f)
    val trackHeightPx = usableHeightPx / trackCount

    active.forEach { item ->
      key(item.id) {
        ScrollingDanmakuItem(
          user = item.user,
          content = item.content,
          showUser = showUser,
          transparentBackground = true,
          startX = widthPx,
          endX = -widthPx,
          y = regionTopPx + trackHeightPx * item.track,
          onFinished = { active.remove(item) },
        )
      }
    }
  }
}

@Composable
private fun ScrollingDanmakuItem(
  user: String,
  content: String,
  showUser: Boolean,
  transparentBackground: Boolean,
  startX: Float,
  endX: Float,
  y: Float,
  onFinished: () -> Unit,
) {
  val x = remember { Animatable(startX) }
  LaunchedEffect(user, content, startX, endX) {
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
    DanmakuBubble(
      user = user,
      content = content,
      showUser = showUser,
      transparentBackground = transparentBackground,
      maxLines = 1,
      compact = true,
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

@Composable
private fun RowWrapInt(
  items: List<Pair<String, Int?>>,
  selected: Int?,
  onSelect: (Int?) -> Unit,
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

@Composable
private fun RowWrapFloat(
  items: List<Pair<String, Float>>,
  selected: Float,
  onSelect: (Float) -> Unit,
) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(items, key = { it.first }) { (label, value) ->
      val isSelected = (value - selected).let { if (it < 0f) -it else it } < 0.0001f
      FilterChip(
        selected = isSelected,
        onClick = { onSelect(value) },
        label = { Text(label) },
      )
    }
  }
}
