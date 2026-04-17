package dtv.mobile.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.repo.DtvRepository
import dtv.mobile.repo.fake.FakeDtvRepository

class AppState(
  val repo: DtvRepository,
) {
  var themeMode: ThemeMode by mutableStateOf(ThemeMode.System)
  var selectedPlatform: Platform by mutableStateOf(Platform.Douyu)
  var currentScreen: Screen by mutableStateOf(Screen.Home)
  var currentStreamer: Streamer? by mutableStateOf(null)
  private var previousScreen: Screen? by mutableStateOf(null)
  var playerFullscreen: Boolean by mutableStateOf(false)

  fun toggleTheme() {
    themeMode = when (themeMode) {
      ThemeMode.System -> ThemeMode.Dark
      ThemeMode.Dark -> ThemeMode.Light
      ThemeMode.Light -> ThemeMode.System
    }
  }

  fun selectPlatform(platform: Platform) {
    selectedPlatform = platform
    if (currentScreen == Screen.Home || currentScreen == Screen.Search) {
      // keep current screen for better UX when switching tabs during search
      return
    }
    currentScreen = Screen.Home
  }

  fun openPlayer(streamer: Streamer) {
    previousScreen = currentScreen
    currentStreamer = streamer
    currentScreen = Screen.Player
    playerFullscreen = false
  }

  fun openSearch() {
    previousScreen = null
    currentScreen = Screen.Search
  }

  fun back() {
    when (currentScreen) {
      Screen.Home -> Unit
      Screen.Player -> {
        currentScreen = previousScreen ?: Screen.Home
        previousScreen = null
        currentStreamer = null
        playerFullscreen = false
      }
      Screen.Search -> {
        previousScreen = null
        currentScreen = Screen.Home
      }
    }
  }
}

enum class ThemeMode { System, Light, Dark }

enum class Screen { Home, Player, Search }

@Composable
fun rememberAppState(repo: DtvRepository = FakeDtvRepository()): AppState {
  return remember(repo) { AppState(repo = repo) }
}
