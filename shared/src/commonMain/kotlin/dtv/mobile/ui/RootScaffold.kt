package dtv.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dtv.mobile.state.AppState
import dtv.mobile.state.Screen
import dtv.mobile.ui.screens.HomeScreen
import dtv.mobile.ui.screens.PlatformScreen
import dtv.mobile.ui.screens.PlayerScreen
import dtv.mobile.ui.screens.SearchScreen
import dtv.mobile.ui.screens.SyncScreen
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import dtv.mobile.ui.system.PlatformBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import dtv.mobile.model.Platform
import dtv.mobile.ui.components.BilibiliWebLoginSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScaffold(appState: AppState) {
  PlatformBackHandler(enabled = appState.currentScreen != Screen.Home) { appState.back() }
  val simpleModeEnabled = appState.subscribedPartitions.any { it.platform == appState.selectedPlatform }

  var showBilibiliLoginSheet by remember { mutableStateOf(false) }
  var bilibiliLoggedIn by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    if (appState.followedStreamers.isNotEmpty()) {
      runCatching { appState.refreshFollowedStreamerCards() }
      runCatching { appState.refreshFollowedLiveStatus() }
    }
  }

  LaunchedEffect(appState.currentScreen, appState.selectedPlatform) {
    if (appState.currentScreen != Screen.Platform) return@LaunchedEffect
    if (appState.selectedPlatform != Platform.Bilibili) return@LaunchedEffect
    bilibiliLoggedIn = !runCatching { appState.repo.getBilibiliCookie() }.getOrNull().isNullOrBlank()
  }

  if (showBilibiliLoginSheet) {
    BilibiliWebLoginSheet(
      onDismissRequest = { showBilibiliLoginSheet = false },
      onCookieCaptured = { cookieHeader ->
        scope.launch {
          appState.repo.mergeBilibiliCookie(cookieHeader)
          bilibiliLoggedIn = !appState.repo.getBilibiliCookie().isNullOrBlank()
        }
      },
    )
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = if (appState.currentScreen == Screen.Player) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
    topBar = {
      when (appState.currentScreen) {
        Screen.Player -> Unit
        Screen.Search -> {
          CenterAlignedTopAppBar(
            title = { Text(text = "搜索") },
            navigationIcon = {
              IconButton(onClick = { appState.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
              }
            },
            actions = {
              SimpleModeToggleIcon(
                enabled = simpleModeEnabled,
                selected = appState.simpleModeForSelectedPlatform,
                onClick = appState::toggleSimpleModeForSelectedPlatform,
              )
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
              containerColor = Color.Transparent,
              scrolledContainerColor = Color.Transparent,
            ),
          )
        }
        Screen.Sync -> {
          CenterAlignedTopAppBar(
            title = { Text(text = "数据同步") },
            navigationIcon = {
              IconButton(onClick = { appState.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
              }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
              containerColor = Color.Transparent,
              scrolledContainerColor = Color.Transparent,
            ),
          )
        }
        else -> {
          HubTopBar(
            title = if (appState.currentScreen == Screen.Home) "关注列表" else appState.selectedPlatform.title,
            onSearchClick = appState::openSearch,
            onSyncClick = appState::openSync,
            simpleMode = appState.simpleModeForSelectedPlatform,
            onSimpleModeToggle = appState::toggleSimpleModeForSelectedPlatform,
            simpleModeEnabled = simpleModeEnabled,
            showBilibiliLogin = appState.currentScreen == Screen.Platform && appState.selectedPlatform == Platform.Bilibili,
            bilibiliLoggedIn = bilibiliLoggedIn,
            onBilibiliLoginClick = { showBilibiliLoginSheet = true },
            onBilibiliLogoutClick = {
              scope.launch {
                appState.repo.clearBilibiliCookie()
                bilibiliLoggedIn = false
              }
            },
            showThemeToggle = appState.currentScreen == Screen.Home,
            onThemeToggle = appState::toggleDayNight,
            showSearch = appState.currentScreen != Screen.Home,
            showSync = appState.currentScreen == Screen.Home,
          )
        }
      }
    },
    bottomBar = {
      if (appState.currentScreen != Screen.Sync && !(appState.currentScreen == Screen.Player && appState.playerFullscreen)) {
        PlatformBottomBar(
          selectedScreen = appState.dockSelectedScreen,
          selectedPlatform = appState.selectedPlatform,
          onHomeClick = appState::openHome,
          onPlatformClick = appState::selectPlatform,
          switchingLoading = appState.platformSwitchLoading,
        )
      }
    },
  ) { padding ->
    AnimatedContent(
      targetState = appState.currentScreen,
      transitionSpec = {
        val enteringPlayer = targetState == Screen.Player && initialState != Screen.Player
        val leavingPlayer = initialState == Screen.Player && targetState != Screen.Player
        when {
          enteringPlayer -> (slideInHorizontally(animationSpec = tween(240)) { it / 6 } + fadeIn(animationSpec = tween(240)))
            .togetherWith(fadeOut(animationSpec = tween(120)))
          leavingPlayer -> fadeIn(animationSpec = tween(120))
            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { it / 6 } + fadeOut(animationSpec = tween(240)))
          else -> fadeIn(animationSpec = tween(140)).togetherWith(fadeOut(animationSpec = tween(140)))
        }
      },
      label = "screen",
      modifier = Modifier.fillMaxSize(),
    ) { screen ->
      when (screen) {
        Screen.Home -> HomeScreen(
          modifier = Modifier.padding(padding),
          appState = appState,
        )
        Screen.Platform -> PlatformScreen(
          modifier = Modifier.padding(padding),
          appState = appState,
        )
        Screen.Player -> PlayerScreen(
          modifier = Modifier.padding(padding),
          appState = appState,
          streamer = appState.currentStreamer,
        )
        Screen.Search -> SearchScreen(
          modifier = Modifier.padding(padding),
          appState = appState,
        )
        Screen.Sync -> SyncScreen(
          modifier = Modifier.padding(padding),
          appState = appState,
        )
      }
    }
  }
}

@Composable
private fun HubTopBar(
  title: String,
  onSearchClick: () -> Unit,
  onSyncClick: () -> Unit,
  simpleMode: Boolean,
  onSimpleModeToggle: () -> Unit,
  simpleModeEnabled: Boolean,
  showBilibiliLogin: Boolean,
  bilibiliLoggedIn: Boolean,
  onBilibiliLoginClick: () -> Unit,
  onBilibiliLogoutClick: () -> Unit,
  showThemeToggle: Boolean,
  onThemeToggle: () -> Unit,
  showSearch: Boolean,
  showSync: Boolean,
  modifier: Modifier = Modifier,
) {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  Surface(
    modifier = modifier.statusBarsPadding(),
    color = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Black,
          fontStyle = FontStyle.Italic,
        ),
        modifier = Modifier.padding(top = 4.dp),
      )

      if (showSearch) {
        Surface(
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onSearchClick() },
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        ) {
          Row(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = "搜索",
              tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
              modifier = Modifier.padding(top = 12.dp),
            )
            Text(
              text = "搜索直播、主播",
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
        }
      } else {
        Spacer(modifier = Modifier.weight(1f))
      }

      if (showSync) {
        IconButton(onClick = onSyncClick) {
          Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = "数据同步",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
          )
        }
      }

      if (showThemeToggle) {
        IconButton(onClick = onThemeToggle) {
          Icon(
             imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
              contentDescription = "日夜模式",
             tint = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
          )
        }
      } else {
        if (showBilibiliLogin) {
          IconButton(
            onClick = if (bilibiliLoggedIn) onBilibiliLogoutClick else onBilibiliLoginClick,
          ) {
            Icon(
              imageVector = if (bilibiliLoggedIn) Icons.AutoMirrored.Filled.Logout else Icons.Default.AccountCircle,
              contentDescription = if (bilibiliLoggedIn) "退出登录" else "登录",
              tint = if (bilibiliLoggedIn) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
              } else {
                MaterialTheme.colorScheme.primary
              },
            )
          }
        }
        SimpleModeToggleIcon(
          enabled = simpleModeEnabled,
          selected = simpleMode,
          onClick = onSimpleModeToggle,
        )
      }
    }
  }
}

@Composable
private fun SimpleModeToggleIcon(
  enabled: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tint = when {
    !enabled -> Color(0xFF9CA3AF)
    selected -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
  }
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
  ) {
    Icon(
      imageVector = Icons.Default.Apps,
      contentDescription = "简易模式",
      tint = tint,
    )
  }
}
