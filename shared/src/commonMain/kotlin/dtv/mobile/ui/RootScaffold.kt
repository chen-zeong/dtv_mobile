package dtv.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dtv.mobile.state.AppState
import dtv.mobile.state.Screen
import dtv.mobile.state.ThemeMode
import dtv.mobile.theme.dtvExtras
import dtv.mobile.ui.screens.HomeScreen
import dtv.mobile.ui.screens.PlayerScreen
import dtv.mobile.ui.screens.SearchScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScaffold(appState: AppState) {
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      val hideTopBar = appState.currentScreen == Screen.Player && appState.playerFullscreen
      if (!hideTopBar) {
        CenterAlignedTopAppBar(
          title = {
            Text(
              text = when (appState.currentScreen) {
                Screen.Home -> appState.selectedPlatform.title
                Screen.Player -> "播放"
                Screen.Search -> "搜索"
              },
            )
          },
          navigationIcon = {
            if (appState.currentScreen != Screen.Home) {
              IconButton(onClick = { appState.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
              }
            }
          },
          actions = {
            if (appState.currentScreen == Screen.Home) {
              IconButton(onClick = { appState.openSearch() }) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
              }
            }
            IconButton(onClick = { appState.toggleTheme() }) {
              val icon = when (appState.themeMode) {
                ThemeMode.System -> Icons.Default.MoreVert
                ThemeMode.Light -> Icons.Default.LightMode
                ThemeMode.Dark -> Icons.Default.DarkMode
              }
              Icon(icon, contentDescription = "主题")
            }
          },
          colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
          ),
        )
      }
    },
    bottomBar = {
      if (appState.currentScreen == Screen.Home || appState.currentScreen == Screen.Search) {
        PlatformBottomBar(
          selected = appState.selectedPlatform,
          onSelected = appState::selectPlatform,
          accentGradient = MaterialTheme.dtvExtras.accentGradient,
        )
      }
    },
  ) { padding ->
    when (appState.currentScreen) {
      Screen.Home -> HomeScreen(
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
    }
  }
}
