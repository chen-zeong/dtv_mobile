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
import androidx.compose.material3.darkColorScheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.platform.app.appForegroundState
import dtv.mobile.repo.DanmakuEvent
import dtv.mobile.repo.DanmakuFailure
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.DanmakuPhase
import dtv.mobile.repo.PlaybackSelectionResolver
import dtv.mobile.state.AppState
import dtv.mobile.state.PlaybackSessionController
import dtv.mobile.state.PlaybackSessionPhase
import dtv.mobile.theme.DtvColors
import dtv.mobile.ui.components.NetworkImage
import dtv.mobile.ui.player.DanmakuDisplayIdGenerator
import dtv.mobile.ui.player.StreamPlayer
import dtv.mobile.ui.player.PlayerSystemGestureLayer
import dtv.mobile.ui.player.messagesAfterDisplayId
import dtv.mobile.ui.system.FullscreenEffect
import dtv.mobile.ui.system.PlatformBackHandler
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
  appState: AppState,
  streamer: Streamer?,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val playbackController = remember(appState, scope) {
    PlaybackSessionController(
      repository = appState.repo,
      scope = scope,
      preferenceProvider = appState::playbackPreference,
      preferenceSaver = appState::updatePlaybackPreference,
    )
  }
  val playbackState by playbackController.state.collectAsState()
  var showSettings by remember(streamer?.roomId) { mutableStateOf(false) }

  var danmakuEnabled by remember(streamer?.roomId) { mutableStateOf(true) }
  var danmakuMessages by remember(streamer?.roomId) { mutableStateOf<List<DanmakuMessage>>(emptyList()) }
  var danmakuEvent by remember(streamer?.roomId) { mutableStateOf<DanmakuEvent?>(null) }
  var danmakuSequence by remember(streamer?.roomId) { mutableStateOf(0L) }
  var danmakuRetryNonce by remember(streamer?.roomId) { mutableIntStateOf(0) }
  val danmakuDisplayIds = remember(streamer?.roomId) { DanmakuDisplayIdGenerator() }
  var danmakuMax by remember { mutableIntStateOf(200) }
  val appForeground by appForegroundState().collectAsState()
  var videoAspectRatio by remember(streamer?.roomId) { mutableStateOf<Float?>(null) }
  var videoReady by remember(streamer?.roomId) { mutableStateOf(false) }
  var fullscreen by remember(streamer?.roomId) { mutableStateOf(false) }
  var fullscreenEntry by remember(streamer?.roomId) { mutableStateOf(FullscreenEntry.None) }

  DisposableEffect(Unit) {
    onDispose { appState.playerFullscreen = false }
  }
  DisposableEffect(playbackController) {
    onDispose { playbackController.close() }
  }
  LaunchedEffect(fullscreen) {
    appState.playerFullscreen = fullscreen
  }

  LaunchedEffect(playbackController, streamer?.platform, streamer?.roomId, streamer?.isLive) {
    val s = streamer
    videoAspectRatio = null
    videoReady = false
    if (s == null || s.isLive == false) {
      playbackController.clear()
    } else {
      playbackController.open(s.platform, s.roomId)
    }
  }

  val blockKeywordsLower by remember {
    derivedStateOf { appState.danmuBlockKeywords.map { it.lowercase() }.filter { it.isNotBlank() } }
  }
  val filteredDanmakuMessages by remember {
    derivedStateOf {
      val keywords = blockKeywordsLower
      if (keywords.isEmpty()) {
        danmakuMessages
      } else {
        danmakuMessages.filterNot { message ->
          val content = message.content.lowercase()
          keywords.any(content::contains)
        }
      }
    }
  }

  LaunchedEffect(streamer?.roomId, streamer?.platform, danmakuEnabled, appForeground, danmakuRetryNonce) {
    val s = streamer ?: return@LaunchedEffect
    if (!danmakuEnabled) return@LaunchedEffect
    if (!appForeground || s.isLive == false) return@LaunchedEffect
    danmakuSequence = 0L
    try {
      appState.repo.observeDanmaku(s.platform, s.roomId).collect { event ->
        if (event.sequence < danmakuSequence) return@collect
        danmakuSequence = event.sequence
        danmakuEvent = event
        event.message?.let { message ->
          val displayMessage = message.copy(displayId = danmakuDisplayIds.next())
          danmakuMessages = (danmakuMessages + displayMessage).takeLast(danmakuMax)
        }
      }
    } catch (t: Throwable) {
      if (t is CancellationException) throw t
    }
  }

  val playbackOptions = playbackState.options
  val selectedQualityId = playbackState.selectedQualityId
  val selectedLineId = playbackState.selectedLineId
  val currentLine = PlaybackSelectionResolver.effectiveLine(playbackOptions, selectedLineId)
  val currentQualities = currentLine?.qualities.orEmpty()
  val url = playbackState.playback?.url
  val error = playbackState.error
  val loading = playbackState.isBusy

  LaunchedEffect(url) {
    videoAspectRatio = null
    videoReady = false
  }
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .then(modifier),
  ) {
    val isPortraitLayout = maxHeight >= maxWidth
    val isLandscapeLayout = !isPortraitLayout

    FullscreenEffect(
      enabled = fullscreen,
      lockLandscape = fullscreenEntry == FullscreenEntry.Manual,
      exitToPortrait = fullscreenEntry == FullscreenEntry.ManualOff,
    )
    PlatformBackHandler(enabled = fullscreen) {
      fullscreen = false
      fullscreenEntry = FullscreenEntry.None
    }

    val settingsStreamer = streamer
    val showSettingsDrawer = showSettings && settingsStreamer != null && fullscreen && isLandscapeLayout
    val showSettingsSheet = showSettings && settingsStreamer != null && !showSettingsDrawer

    val settingsContent: @Composable () -> Unit = settingsContent@{
      val s = settingsStreamer ?: return@settingsContent
      Column(
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("播放设置", style = MaterialTheme.typography.titleMedium)

        Text("画质 · 当前线路", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrap(
          items = listOf("自动" to null) + currentQualities.map { it.label to it.id },
          selected = selectedQualityId,
          onSelect = playbackController::selectQuality,
          enabled = !playbackState.isBusy,
        )

        Text("线路", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrap(
          items = listOf("自动" to null) + playbackOptions.lines.map { it.label to it.id },
          selected = selectedLineId,
          onSelect = playbackController::selectLine,
          enabled = !playbackState.isBusy,
        )
        if (playbackState.isBusy) {
          Text(
            text = if (playbackState.phase == PlaybackSessionPhase.Switching) "正在切换播放线路…" else "正在加载播放信息…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
          )
        }
        if (playbackState.error != null) {
          Text(
            text = playbackState.error.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        Text("弹幕", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrap(
          items = listOf("开" to "on", "关" to "off"),
          selected = if (danmakuEnabled) "on" else "off",
          onSelect = { danmakuEnabled = it == "on" },
        )
        Text(
          text = danmakuStatusText(danmakuEvent, filteredDanmakuMessages.isNotEmpty()),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        if (danmakuEvent?.phase == DanmakuPhase.Degraded ||
          danmakuEvent?.phase == DanmakuPhase.Reconnecting ||
          danmakuEvent?.failure != null
        ) {
          TextButton(onClick = { danmakuRetryNonce += 1 }) {
            Text("立即重试")
          }
        }

        Text("弹幕字体大小", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrapFloat(
          items = listOf(
            "小" to 0.90f,
            "中" to 1.00f,
            "大" to 1.15f,
          ),
          selected = appState.danmakuFontScale,
          onSelect = { appState.updateDanmakuFontScale(it) },
        )

        Text("弹幕透明度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrapFloat(
          items = listOf(
            "100%" to 1.00f,
            "80%" to 0.85f,
            "60%" to 0.70f,
            "40%" to 0.55f,
          ),
          selected = appState.danmakuOpacity,
          onSelect = { appState.updateDanmakuOpacity(it) },
        )

        Text("弹幕显示区域", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        RowWrapFloat(
          items = listOf(
            "上1/4" to 0.25f,
            "上1/2" to 0.5f,
            "上3/4" to 0.75f,
            "全屏" to 1.0f,
          ),
          selected = appState.danmakuAreaFraction,
          onSelect = { appState.updateDanmakuAreaFraction(it) },
        )

        if (isLandscapeLayout) {
          Text("横屏弹幕字体", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
          RowWrapFloat(
            items = listOf(
              "小" to 1.0f,
              "中" to 1.15f,
              "大" to 1.30f,
              "特大" to 1.45f,
            ),
            selected = appState.landscapeDanmakuFontScale,
            onSelect = { appState.updateLandscapeDanmakuFontScale(it) },
          )
        }

        SpacerLine()
      }
    }

    PlatformBackHandler(enabled = showSettingsDrawer || showSettingsSheet) { showSettings = false }

    val effectiveAspect = videoAspectRatio?.takeIf { it > 0f }
    val isVideoAspectKnown = effectiveAspect != null
    val layoutAspect = effectiveAspect ?: (16f / 9f)
    val isVerticalVideo = isVideoAspectKnown && effectiveAspect < 1f
    val isHorizontalVideo = isVideoAspectKnown && !isVerticalVideo
    val verticalFullBleed = !fullscreen && isVerticalVideo
    val showFullPageLoading = !fullscreen &&
      !verticalFullBleed &&
      streamer?.isLive == true &&
      url == null &&
      error == null &&
      loading

    LaunchedEffect(isLandscapeLayout) {
      // Rotating to landscape should behave like fullscreen (hide bottom bar, system bars).
      if (isLandscapeLayout && !fullscreen && fullscreenEntry != FullscreenEntry.ManualOff) {
        fullscreen = true
        fullscreenEntry = FullscreenEntry.Auto
      } else if (!isLandscapeLayout && fullscreenEntry == FullscreenEntry.Auto) {
        fullscreen = false
        fullscreenEntry = FullscreenEntry.None
      } else if (!isLandscapeLayout && fullscreenEntry == FullscreenEntry.ManualOff) {
        fullscreenEntry = FullscreenEntry.None
      }
    }

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

        if (showFullPageLoading) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center,
          ) {
            CenteredLoadingIndicator()
          }
          return@Column
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
          filteredDanmakuMessages.isNotEmpty() &&
          isVideoAspectKnown &&
          videoReady &&
          url != null
        val canShowDanmakuPanel = danmakuEnabled &&
          danmakuEvent != null &&
          streamer?.isLive != false

        Surface(
          shape = videoSurfaceShape,
          color = videoSurfaceColor,
          modifier = videoSurfaceModifier,
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            val currentUrl = url
            if (currentUrl != null) {
              StreamPlayer(
                url = currentUrl,
                fullscreen = fullscreen,
                liveMode = true,
                zoomToFill = verticalFullBleed,
                onVideoAspectRatioChanged = {
                  videoAspectRatio = it
                  if (it != null && it > 0f) videoReady = true
                },
                onError = {
                  if (it.startsWith("__retry_http__:")) {
                    playbackController.replacePlaybackUrl(it.removePrefix("__retry_http__:"))
                  } else {
                    playbackController.reportPlayerFailure(it)
                  }
                },
                modifier = Modifier.fillMaxSize(),
              )
              if (fullscreen) {
                PlayerSystemGestureLayer(
                  enabled = !showSettings && !playbackState.isBusy && playbackState.playback != null,
                  modifier = Modifier.fillMaxSize(),
                )
              }
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
                  val currentError = error
                  Text(
                    text = currentError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fullscreen) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f),
                  )
                }
              }
            }

            if (playbackState.phase == PlaybackSessionPhase.Switching && playbackState.playback != null) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier
                  .align(Alignment.TopCenter)
                  .padding(top = 16.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                  )
                  Text(
                    text = "正在切换线路…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                  )
                }
              }
            }

            if (playbackState.phase == PlaybackSessionPhase.Failed && playbackState.playback != null) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .padding(16.dp),
              ) {
                Text(
                  text = playbackState.error ?: "切换播放失败，已保留当前播放",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.9f),
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
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
                  messages = filteredDanmakuMessages,
                  showUser = false,
                  areaFraction = appState.danmakuAreaFraction,
                  textScale = appState.landscapeDanmakuFontScale * appState.danmakuFontScale,
                  opacity = appState.danmakuOpacity,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                )
              } else {
                DanmakuOverlay(
                  messages = filteredDanmakuMessages,
                  showUser = true,
                  areaFraction = appState.danmakuAreaFraction,
                  transparentBackground = false,
                  textScale = appState.danmakuFontScale,
                  opacity = appState.danmakuOpacity,
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                )
              }
            }

            val danmakuNeedsAttention = danmakuEnabled &&
              (fullscreen || isVerticalVideo) &&
              (danmakuEvent?.phase == DanmakuPhase.Degraded ||
                (danmakuEvent?.phase == DanmakuPhase.Reconnecting && danmakuEvent?.failure != null))
            if (danmakuNeedsAttention) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(16.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = danmakuStatusText(danmakuEvent, filteredDanmakuMessages.isNotEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                  )
                  TextButton(onClick = { danmakuRetryNonce += 1 }) {
                    Text("重试", color = Color.White)
                  }
                }
              }
            }

            PlayerSideControlsOverlay(
              fullscreen = fullscreen,
              showFullscreen = isVideoAspectKnown && !isVerticalVideo,
              actionsEnabled = !playbackState.isBusy,
              onToggleFullscreen = {
                if (fullscreen) {
                  fullscreen = false
                  fullscreenEntry = if (isLandscapeLayout) FullscreenEntry.ManualOff else FullscreenEntry.None
                } else {
                  fullscreen = true
                  fullscreenEntry = FullscreenEntry.Manual
                }
              },
              onOpenSettings = { showSettings = true },
              onReload = playbackController::retry,
              modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            )
          }
        }

        if (!fullscreen && !verticalFullBleed) {
          if (canShowDanmakuPanel && isHorizontalVideo) {
            HubDanmakuPanel(
              messages = filteredDanmakuMessages,
              statusText = danmakuStatusText(danmakuEvent, filteredDanmakuMessages.isNotEmpty()),
              enhancedPortrait = isPortraitLayout,
              textScale = appState.danmakuFontScale,
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp, bottom = 0.dp),
            )
          }
        }
      }
    }

    PlayerBackground(fullscreen = fullscreen, content = content)

    PlayerSettingsDrawer(
      visible = showSettingsDrawer,
      onDismissRequest = { showSettings = false },
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
      ) {
        settingsContent()
      }
    }

    if (showSettingsSheet) {
      ModalBottomSheet(onDismissRequest = { showSettings = false }) {
        settingsContent()
      }
    }
  }
}

private enum class FullscreenEntry {
  None,
  Auto,
  Manual,
  ManualOff,
}

@Composable
private fun CenteredLoadingIndicator(
  modifier: Modifier = Modifier,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val border = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
  val bg = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
  val fg = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    color = bg,
    border = BorderStroke(1.dp, border),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      CircularProgressIndicator(
        strokeWidth = 3.dp,
        modifier = Modifier.size(22.dp),
        color = MaterialTheme.colorScheme.primary,
      )

      val t = rememberInfiniteTransition(label = "loadingDots")
      val phase = t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900, easing = LinearEasing)),
        label = "loadingDotsPhase",
      ).value
      val dots = when (((phase * 3f).toInt()) % 4) {
        0 -> ""
        1 -> "."
        2 -> ".."
        else -> "..."
      }

      Text(
        text = "加载中$dots",
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
      )
    }
  }
}

@Composable
private fun PlayerBackground(
  fullscreen: Boolean,
  content: @Composable () -> Unit,
) {
  val bg = MaterialTheme.colorScheme.background
  val accent = MaterialTheme.colorScheme.primary
  val isDark = bg.luminance() < 0.35f

  Box(
    modifier = Modifier
      .fillMaxSize()
      .then(if (fullscreen) Modifier.background(Color.Black) else Modifier),
  ) {
    if (!fullscreen) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(
              bg,
              accent.copy(alpha = if (isDark) 0.05f else 0.10f),
              bg,
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
          ),
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = if (isDark) 0.08f else 0.12f), Color.Transparent),
            center = Offset(size.width * 0.80f, size.height * 0.18f),
            radius = size.width * 0.70f,
          ),
          radius = size.width * 0.70f,
          center = Offset(size.width * 0.80f, size.height * 0.18f),
        )
      }
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
      val available = (maxWidth - closeSize - 10.dp).coerceAtLeast(0.dp)
      val infoWidth = (maxWidth * 0.66f)
        .coerceAtMost(available)
        .coerceAtLeast(200.dp.coerceAtMost(available))
      val avatarSize = closeSize - 8.dp

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          modifier = Modifier
            .width(infoWidth)
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
internal fun PlayerSideControlsOverlay(
  fullscreen: Boolean,
  showFullscreen: Boolean,
  actionsEnabled: Boolean,
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
    ControlFab(
      icon = Icons.Default.Settings,
      onClick = onOpenSettings,
      tag = "player_settings",
    )
    ControlFab(
      icon = Icons.Default.Refresh,
      onClick = onReload,
      enabled = actionsEnabled,
      tag = "player_reload",
    )
    if (showFullscreen) {
      ControlFab(
        icon = Icons.Default.FullscreenExit.takeIf { fullscreen } ?: Icons.Default.Fullscreen,
        onClick = onToggleFullscreen,
        enabled = actionsEnabled,
        tag = "player_fullscreen",
      )
    }
  }
}

@Composable
private fun ControlFab(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  enabled: Boolean = true,
  tag: String? = null,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .size(38.dp)
      .clip(CircleShape)
      .then(if (tag == null) Modifier else Modifier.testTag(tag))
      .clickable(enabled = enabled, onClick = onClick),
    shape = CircleShape,
    color = Color.White.copy(alpha = if (enabled) 0.10f else 0.05f),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        modifier = Modifier.size(25.dp),
        imageVector = icon,
        contentDescription = null,
        tint = Color.White.copy(alpha = if (enabled) 0.92f else 0.35f),
      )
    }
  }
}

@Composable
private fun PlayerSettingsDrawer(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val nightScheme = remember {
    darkColorScheme(
      primary = DtvColors.NightAccent,
      onPrimary = DtvColors.NightTextPrimary,
      secondary = DtvColors.NightBgTertiary,
      onSecondary = DtvColors.NightTextPrimary,
      background = DtvColors.NightBgPrimary,
      onBackground = DtvColors.NightTextPrimary,
      surface = DtvColors.NightBgSecondary,
      onSurface = DtvColors.NightTextPrimary,
      outline = DtvColors.NightBorder,
    )
  }

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(animationSpec = tween(durationMillis = 140)),
      exit = fadeOut(animationSpec = tween(durationMillis = 140)),
      label = "settings_scrim",
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.45f))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismissRequest,
          ),
      )
    }

    AnimatedVisibility(
      visible = visible,
      enter = slideInHorizontally(animationSpec = tween(durationMillis = 220)) { -it } + fadeIn(animationSpec = tween(durationMillis = 140)),
      exit = slideOutHorizontally(animationSpec = tween(durationMillis = 220)) { -it } + fadeOut(animationSpec = tween(durationMillis = 140)),
      label = "settings_drawer",
    ) {
      Surface(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(0.78f)
          .widthIn(max = 320.dp),
        shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = DtvColors.NightTextPrimary,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f)),
        ) {
          MaterialTheme(colorScheme = nightScheme) {
            content()
          }
        }
      }
    }
  }
}

@Composable
private fun DanmakuOverlay(
  messages: List<DanmakuMessage>,
  showUser: Boolean,
  areaFraction: Float,
  transparentBackground: Boolean,
  textScale: Float,
  opacity: Float,
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
          textScale = textScale,
          opacity = opacity,
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
  textScale: Float = 1f,
  opacity: Float = 1f,
) {
  val displayUser = user.trim().ifBlank { "匿名" }
  val displayContent = content.trim()
  val effectiveOpacity = opacity.coerceIn(0.35f, 1.0f)

  val text = buildAnnotatedString {
    if (showUser) {
      withStyle(
        SpanStyle(
          color = Color(0xFFFFE082).copy(alpha = 0.92f * effectiveOpacity),
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
    color = if (transparentBackground) Color.Transparent else Color.Black.copy(alpha = 0.26f * effectiveOpacity),
    border = null,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    val base = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
    val style =
      if (textScale == 1f) base
      else base.copy(
        fontSize = if (base.fontSize == TextUnit.Unspecified) base.fontSize else base.fontSize * textScale,
        lineHeight = if (base.lineHeight == TextUnit.Unspecified) base.lineHeight else base.lineHeight * textScale,
      )
    Text(
      text = text,
      style = style,
      color = Color.White.copy(alpha = 0.92f * effectiveOpacity),
      modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun HubDanmakuPanel(
  messages: List<DanmakuMessage>,
  statusText: String,
  enhancedPortrait: Boolean = false,
  textScale: Float = 1f,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val itemSpacing = if (enhancedPortrait) 8.dp else 6.dp

  val display = remember(messages) { messages.asReversed() }
  LaunchedEffect(display.firstOrNull()?.displayId) {
    if (display.isNotEmpty()) listState.scrollToItem(index = 0)
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .fillMaxSize()
      .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Text(
      text = statusText,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
    if (display.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "暂无新弹幕",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
      ) {
        itemsIndexed(
          display,
          key = { index, msg ->
            if (msg.displayId > 0L) "message:${msg.displayId}"
            else "${msg.roomId}:${msg.user}:${msg.content}:$index"
          },
        ) { _, msg ->
          HubDanmakuRow(
            user = msg.user.trim().ifBlank { "匿名" },
            content = msg.content.trim(),
            enhancedPortrait = enhancedPortrait,
            textScale = textScale,
          )
        }
      }
    }
  }
}

private fun danmakuStatusText(
  event: DanmakuEvent?,
  hasMessages: Boolean,
): String {
  return when (event?.phase) {
    null -> "弹幕尚未连接"
    DanmakuPhase.Connecting -> "正在连接弹幕…"
    DanmakuPhase.Authenticating -> "正在认证弹幕连接…"
    DanmakuPhase.Ready -> if (hasMessages) "弹幕已连接" else "暂无新弹幕"
    DanmakuPhase.Reconnecting -> {
      val failure = event.failure?.let { "：${danmakuFailureText(it)}" }.orEmpty()
      "弹幕重连中$failure"
    }
    DanmakuPhase.Degraded -> {
      val failure = event.failure?.let { "：${danmakuFailureText(it)}" }.orEmpty()
      "弹幕连接异常，正在自动重试$failure"
    }
  }
}

private fun danmakuFailureText(failure: DanmakuFailure): String {
  return when (failure) {
    DanmakuFailure.Network -> "网络异常"
    DanmakuFailure.Http -> "服务异常"
    DanmakuFailure.Authentication -> "认证失败"
    DanmakuFailure.Protocol -> "连接超时"
    DanmakuFailure.RoomUnavailable -> "直播间不可用"
    DanmakuFailure.RateLimited -> "请求过于频繁"
    DanmakuFailure.Unknown -> "未知错误"
  }
}

@Composable
private fun HubDanmakuRow(
  user: String,
  content: String,
  enhancedPortrait: Boolean = false,
  textScale: Float = 1f,
  modifier: Modifier = Modifier,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val userChipBg = if (isDark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
  val userChipBorder = if (isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
  val userFg = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.72f else 0.90f)
  val contentFg = if (isDark) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(if (enhancedPortrait) 8.dp else 6.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = userChipBg,
      border = userChipBorder,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = if (enhancedPortrait) 8.dp else 6.dp, vertical = if (enhancedPortrait) 4.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = user,
          style = scaleTextStyle(
            if (enhancedPortrait) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            textScale,
          ),
          color = userFg,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Text(
      text = content,
      style = scaleTextStyle(
        if (enhancedPortrait) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        else MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        textScale,
      ),
      color = contentFg,
      modifier = Modifier
        .weight(1f)
        .padding(top = 0.dp),
      maxLines = if (enhancedPortrait) 4 else 3,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun ScrollingDanmakuOverlay(
  resetKey: Any?,
  messages: List<DanmakuMessage>,
  showUser: Boolean,
  areaFraction: Float,
  textScale: Float = 1f,
  opacity: Float = 1f,
  modifier: Modifier = Modifier,
) {
  data class Active(
    val id: Long,
    val user: String,
    val content: String,
    val track: Int,
  )

  var lastDisplayId by remember(resetKey) { mutableStateOf(0L) }
  val maxActive = 48

  BoxWithConstraints(modifier = modifier) {
    val density = LocalDensity.current
    val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val regionTopPx = heightPx * 0.04f
    val regionHeightPx = (heightPx * areaFraction).coerceAtLeast(1f)
    val usableHeightPx = (regionHeightPx - regionTopPx).coerceAtLeast(1f)

    // Avoid overlap when text size changes: compute track count based on estimated bubble height.
    val base = MaterialTheme.typography.bodySmall
    val fontSizePx = with(density) {
      (if (base.fontSize == TextUnit.Unspecified) 12.sp else base.fontSize).toPx()
    }
    val lineHeightPx = with(density) {
      if (base.lineHeight == TextUnit.Unspecified) fontSizePx * 1.2f else base.lineHeight.toPx()
    }
    val vPadPx = with(density) { 4.dp.toPx() }
    val hPadPx = with(density) { 8.dp.toPx() }
    val trackGapPx = with(density) { 2.dp.toPx() }
    val minTrackHeightPx = ((lineHeightPx * textScale.coerceAtLeast(0.85f)) + vPadPx * 2f)
      .coerceAtLeast(1f)
    val trackStepPx = (minTrackHeightPx + trackGapPx).coerceAtLeast(1f)
    val trackCount = (usableHeightPx / trackStepPx).toInt().coerceIn(1, 24)
    val active = remember(resetKey, trackCount) { mutableStateListOf<Active>() }
    val laneAvailableAt = remember(resetKey, trackCount) { MutableList(trackCount) { 0L } }

    fun estimatedTextWidthPx(user: String, content: String): Float {
      val text = if (showUser) "$user  $content" else content
      val weightedChars = text.sumOf { ch ->
        when {
          ch.code <= 0x007F -> 1
          else -> 2
        }
      }
      return weightedChars * fontSizePx * textScale.coerceAtLeast(0.85f) * 0.56f + hPadPx * 2f
    }

    fun chooseTrack(now: Long): Int? {
      var bestReadyTrack = -1
      var bestReadyAt = Long.MAX_VALUE
      laneAvailableAt.forEachIndexed { index, readyAt ->
        if (readyAt <= now) return index
        if (readyAt < bestReadyAt) {
          bestReadyAt = readyAt
          bestReadyTrack = index
        }
      }
      return bestReadyTrack.takeIf { bestReadyAt <= now + 120L }
    }

    LaunchedEffect(messages, trackCount, widthPx, textScale) {
      val newItems = messages.messagesAfterDisplayId(lastDisplayId)
      if (newItems.isEmpty()) return@LaunchedEffect
      newItems.forEach { msg ->
        val user = msg.user.trim().ifBlank { "匿名" }
        val content = msg.content.trim()
        if (content.isNotEmpty()) {
          val now = System.nanoTime() / 1_000_000L
          val track = chooseTrack(now)
          if (track != null) {
            if (active.size >= maxActive) active.removeAt(0)
            active.add(
              Active(
                id = msg.displayId,
                user = user,
                content = content,
                track = track,
              ),
            )
            val textWidthPx = estimatedTextWidthPx(user, content).coerceAtLeast(1f)
            val travelWidthPx = widthPx + textWidthPx
            val minDelayMs = ceil((textWidthPx / travelWidthPx) * 9000f).toLong() + 80L
            laneAvailableAt[track] = now + minDelayMs
          }
        }
      }
      lastDisplayId = newItems.last().displayId
    }

    active.forEach { item ->
      key(item.id) {
        ScrollingDanmakuItem(
          user = item.user,
          content = item.content,
          showUser = showUser,
          transparentBackground = true,
          textScale = textScale,
          opacity = opacity,
          startX = widthPx,
          endX = -widthPx,
          y = regionTopPx + trackStepPx * (item.track % trackCount),
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
  textScale: Float,
  opacity: Float,
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
      textScale = textScale,
      opacity = opacity,
    )
  }
}

private fun scaleTextStyle(
  base: TextStyle,
  scale: Float,
): TextStyle {
  val s = scale.coerceIn(0.85f, 1.3f)
  if (s == 1f) return base
  return base.copy(
    fontSize = if (base.fontSize == TextUnit.Unspecified) base.fontSize else base.fontSize * s,
    lineHeight = if (base.lineHeight == TextUnit.Unspecified) base.lineHeight else base.lineHeight * s,
  )
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
  enabled: Boolean = true,
) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(items, key = { it.first }) { (label, value) ->
      val isSelected = value == selected || (value == null && selected == null)
      FilterChip(
        selected = isSelected,
        onClick = { onSelect(value) },
        enabled = enabled,
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
