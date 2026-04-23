package dtv.mobile.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.repo.DtvRepository
import dtv.mobile.repo.fake.FakeDtvRepository
import kotlinx.serialization.Serializable

@Serializable
data class SubscribedPartition(
  val id: String,
  val name: String,
  val platform: Platform? = null,
)

class AppState(
  val repo: DtvRepository,
  private val subscriptionStore: SubscriptionStore,
) {
  var themeMode: ThemeMode by mutableStateOf(ThemeMode.System)
  var platformSwitchLoading: Boolean by mutableStateOf(false)
  var selectedPlatform: Platform by mutableStateOf(Platform.Douyu)
  var currentScreen: Screen by mutableStateOf(Screen.Home)
  var currentStreamer: Streamer? by mutableStateOf(null)
  private var playerReturnScreen: Screen? by mutableStateOf(null)
  private var searchReturnScreen: Screen? by mutableStateOf(null)
  var playerFullscreen: Boolean by mutableStateOf(false)
  var currentPartition: SubscribedPartition? by mutableStateOf(null)

  val followedStreamers = mutableStateListOf<Streamer>()
  val subscribedPartitions = mutableStateListOf<SubscribedPartition>()
  private val simpleModeByPlatform = mutableStateMapOf<Platform, Boolean>()

  init {
    followedStreamers.addAll(subscriptionStore.loadFollowedStreamers())
    subscribedPartitions.addAll(subscriptionStore.loadSubscribedPartitions())

    subscriptionStore.loadSimpleModeByPlatform().forEach { entry ->
      simpleModeByPlatform[entry.platform] = entry.enabled
    }
  }

  val dockSelectedScreen: Screen
    get() = if (currentScreen == Screen.Search) searchReturnScreen ?: Screen.Home else currentScreen

  private fun streamerKey(streamer: Streamer): String = "${streamer.platform.name}:${streamer.roomId}"

  fun isFollowed(streamer: Streamer): Boolean {
    val key = streamerKey(streamer)
    return followedStreamers.any { streamerKey(it) == key }
  }

  fun toggleFollow(streamer: Streamer) {
    val key = streamerKey(streamer)
    val index = followedStreamers.indexOfFirst { streamerKey(it) == key }
    if (index >= 0) {
      followedStreamers.removeAt(index)
    } else {
      followedStreamers.add(streamer)
    }
    subscriptionStore.saveFollowedStreamers(followedStreamers.toList())
  }

  fun toggleTheme() {
    themeMode = when (themeMode) {
      ThemeMode.System -> ThemeMode.Dark
      ThemeMode.Dark -> ThemeMode.Light
      ThemeMode.Light -> ThemeMode.System
    }
  }

  fun toggleDayNight() {
    themeMode = when (themeMode) {
      ThemeMode.Dark -> ThemeMode.Light
      ThemeMode.Light, ThemeMode.System -> ThemeMode.Dark
    }
  }

  fun isSimpleMode(platform: Platform): Boolean = simpleModeByPlatform[platform] ?: false

  val simpleModeForSelectedPlatform: Boolean
    get() = isSimpleMode(selectedPlatform)

  fun toggleSimpleModeForSelectedPlatform() {
    val p = selectedPlatform
    val next = !(simpleModeByPlatform[p] ?: false)
    simpleModeByPlatform[p] = next
    val entries = simpleModeByPlatform.entries.map { (platform, enabled) -> SimpleModeEntry(platform = platform, enabled = enabled) }
    subscriptionStore.saveSimpleModeByPlatform(entries)
  }

  suspend fun refreshFollowedLiveStatus() {
    val snapshot = followedStreamers.toList()
    snapshot.forEach { streamer ->
      val live = repo.fetchLiveStatus(streamer) ?: return@forEach
      val key = streamerKey(streamer)
      val index = followedStreamers.indexOfFirst { streamerKey(it) == key }
      if (index >= 0) {
        val current = followedStreamers[index]
        if (current.isLive != live) {
          followedStreamers[index] = current.copy(isLive = live)
        }
      }
    }
  }

  suspend fun refreshFollowedStreamerCards() {
    val snapshot = followedStreamers.toList()
    val updated = snapshot.map { s ->
      repo.fetchFollowedStreamerSnapshot(s)?.let { it.copy(platform = s.platform, roomId = s.roomId) } ?: s
    }

    val sorted = updated.sortedWith(
      compareByDescending<Streamer> { it.isLive }
        .thenBy { it.platform.ordinal }
        .thenBy { it.name },
    )

    followedStreamers.clear()
    followedStreamers.addAll(sorted)
    subscriptionStore.saveFollowedStreamers(followedStreamers.toList())
  }

  private fun partitionKey(p: SubscribedPartition): String = "${p.platform?.name ?: "any"}:${p.id}"

  fun isPartitionSubscribed(p: SubscribedPartition): Boolean {
    val key = partitionKey(p)
    return subscribedPartitions.any { partitionKey(it) == key }
  }

  fun togglePartition(p: SubscribedPartition) {
    val key = partitionKey(p)
    val index = subscribedPartitions.indexOfFirst { partitionKey(it) == key }
    if (index >= 0) {
      subscribedPartitions.removeAt(index)
    } else {
      subscribedPartitions.add(p)
    }
    subscriptionStore.saveSubscribedPartitions(subscribedPartitions.toList())
  }

  fun openHome() {
    currentScreen = Screen.Home
  }

  fun selectPlatform(platform: Platform) {
    platformSwitchLoading = true
    selectedPlatform = platform
    if (currentScreen == Screen.Search) {
      // keep current screen for better UX when switching tabs during search
      return
    }
    currentScreen = Screen.Platform
  }

  fun openPlayer(streamer: Streamer, partition: SubscribedPartition? = null) {
    playerReturnScreen = currentScreen
    currentStreamer = streamer
    currentPartition = partition
    currentScreen = Screen.Player
    playerFullscreen = false
  }

  fun openSearch() {
    searchReturnScreen = currentScreen
    currentScreen = Screen.Search
  }

  fun back() {
    when (currentScreen) {
      Screen.Home -> Unit
      Screen.Platform -> currentScreen = Screen.Home
      Screen.Player -> {
        currentScreen = playerReturnScreen ?: Screen.Home
        playerReturnScreen = null
        currentStreamer = null
        currentPartition = null
        playerFullscreen = false
      }
      Screen.Search -> {
        currentScreen = searchReturnScreen ?: Screen.Home
        searchReturnScreen = null
      }
    }
  }
}

enum class ThemeMode { System, Light, Dark }

enum class Screen { Home, Platform, Player, Search }

@Composable
fun rememberAppState(
  repo: DtvRepository = FakeDtvRepository(),
  subscriptionStore: SubscriptionStore = InMemorySubscriptionStore,
): AppState {
  return remember(repo, subscriptionStore) { AppState(repo = repo, subscriptionStore = subscriptionStore) }
}
