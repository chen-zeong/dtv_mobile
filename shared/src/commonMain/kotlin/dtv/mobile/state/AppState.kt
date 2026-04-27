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
  var landscapeDanmakuFontScale: Float by mutableStateOf(1.2f)
  var danmakuFontScale: Float by mutableStateOf(1.0f)
  var danmakuOpacity: Float by mutableStateOf(1.0f)
  var danmakuAreaFraction: Float by mutableStateOf(0.5f)
  var platformSwitchLoading: Boolean by mutableStateOf(false)
  var selectedPlatform: Platform by mutableStateOf(Platform.Douyu)
  var currentScreen: Screen by mutableStateOf(Screen.Home)
  var currentStreamer: Streamer? by mutableStateOf(null)
  private var playerReturnScreen: Screen? by mutableStateOf(null)
  private var searchReturnScreen: Screen? by mutableStateOf(null)
  var playerFullscreen: Boolean by mutableStateOf(false)
  var currentPartition: SubscribedPartition? by mutableStateOf(null)

  val followedStreamers = mutableStateListOf<Streamer>()
  val pinnedFollowedStreamerKeys = mutableStateListOf<String>()
  val subscribedPartitions = mutableStateListOf<SubscribedPartition>()
  val danmuBlockKeywords = mutableStateListOf<String>()
  private val simpleModeByPlatform = mutableStateMapOf<Platform, Boolean>()

  init {
    followedStreamers.addAll(subscriptionStore.loadFollowedStreamers())
    pinnedFollowedStreamerKeys.addAll(subscriptionStore.loadPinnedFollowedStreamerKeys())
    subscribedPartitions.addAll(subscriptionStore.loadSubscribedPartitions())
    danmuBlockKeywords.addAll(subscriptionStore.loadDanmuBlockKeywords())
    landscapeDanmakuFontScale = subscriptionStore.loadLandscapeDanmakuFontScale()
    danmakuFontScale = subscriptionStore.loadDanmakuFontScale()
    danmakuOpacity = subscriptionStore.loadDanmakuOpacity()
    danmakuAreaFraction = subscriptionStore.loadDanmakuAreaFraction()

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
      if (pinnedFollowedStreamerKeys.remove(key)) {
        subscriptionStore.savePinnedFollowedStreamerKeys(pinnedFollowedStreamerKeys.toList())
      }
    } else {
      followedStreamers.add(streamer)
    }
    subscriptionStore.saveFollowedStreamers(followedStreamers.toList())
  }

  fun setPinnedFollowedStreamers(keys: List<String>) {
    val trimmed = keys.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    pinnedFollowedStreamerKeys.clear()
    pinnedFollowedStreamerKeys.addAll(trimmed.distinct())
    subscriptionStore.savePinnedFollowedStreamerKeys(pinnedFollowedStreamerKeys.toList())
  }

  fun removePinnedFollowedStreamer(key: String) {
    if (pinnedFollowedStreamerKeys.remove(key)) {
      subscriptionStore.savePinnedFollowedStreamerKeys(pinnedFollowedStreamerKeys.toList())
    }
  }

  fun updateLandscapeDanmakuFontScale(value: Float) {
    landscapeDanmakuFontScale = value.coerceIn(0.85f, 2.0f)
    subscriptionStore.saveLandscapeDanmakuFontScale(landscapeDanmakuFontScale)
  }

  fun updateDanmakuFontScale(value: Float) {
    danmakuFontScale = value.coerceIn(0.85f, 1.3f)
    subscriptionStore.saveDanmakuFontScale(danmakuFontScale)
  }

  fun updateDanmakuOpacity(value: Float) {
    danmakuOpacity = value.coerceIn(0.35f, 1.0f)
    subscriptionStore.saveDanmakuOpacity(danmakuOpacity)
  }

  fun updateDanmakuAreaFraction(value: Float) {
    danmakuAreaFraction = value.coerceIn(0.25f, 1.0f)
    subscriptionStore.saveDanmakuAreaFraction(danmakuAreaFraction)
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
    followedStreamers.clear()
    followedStreamers.addAll(updated)
    subscriptionStore.saveFollowedStreamers(followedStreamers.toList())
  }

  fun moveFollowedStreamer(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    if (fromIndex !in 0 until followedStreamers.size) return
    if (toIndex !in 0 until followedStreamers.size) return

    val item = followedStreamers.removeAt(fromIndex)
    followedStreamers.add(index = toIndex, element = item)
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

  private fun normalizeDanmuBlockKeywords(keywords: List<String>): List<String> {
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    for (raw in keywords) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) continue
      val normalized = trimmed.take(40)
      val key = normalized.lowercase()
      if (!seen.add(key)) continue
      out.add(normalized)
      if (out.size >= 40) break
    }
    return out
  }

  fun setDanmuBlockKeywords(keywords: List<String>) {
    val next = normalizeDanmuBlockKeywords(keywords)
    danmuBlockKeywords.clear()
    danmuBlockKeywords.addAll(next)
    subscriptionStore.saveDanmuBlockKeywords(next)
  }

  fun mergeDanmuBlockKeywords(keywords: List<String>): Int {
    val existing = danmuBlockKeywords.mapTo(HashSet()) { it.lowercase() }
    val merged = danmuBlockKeywords.toMutableList()
    var added = 0
    for (raw in keywords) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) continue
      val normalized = trimmed.take(40)
      val key = normalized.lowercase()
      if (!existing.add(key)) continue
      merged.add(normalized)
      added += 1
      if (merged.size >= 40) break
    }
    if (added > 0) setDanmuBlockKeywords(merged)
    return added
  }

  fun mergeFollowedStreamers(incoming: List<Streamer>): Int {
    if (incoming.isEmpty()) return 0
    val existing = followedStreamers.asSequence().map { streamerKey(it) }.toHashSet()
    val added = incoming.filter { existing.add(streamerKey(it)) }
    if (added.isEmpty()) return 0
    followedStreamers.addAll(added)
    subscriptionStore.saveFollowedStreamers(followedStreamers.toList())
    return added.size
  }

  fun mergeSubscribedPartitions(incoming: List<SubscribedPartition>): Int {
    if (incoming.isEmpty()) return 0
    val existing = subscribedPartitions.asSequence().map { partitionKey(it) }.toHashSet()
    val added = incoming.filter { existing.add(partitionKey(it)) }
    if (added.isEmpty()) return 0
    subscribedPartitions.addAll(added)
    subscriptionStore.saveSubscribedPartitions(subscribedPartitions.toList())
    return added.size
  }

  fun openHome() {
    currentScreen = Screen.Home
  }

  fun selectPlatform(platform: Platform) {
    platformSwitchLoading = true
    selectedPlatform = platform
    currentPartition = null
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

  fun openSync() {
    currentScreen = Screen.Sync
  }

  fun back() {
    when (currentScreen) {
      Screen.Home -> Unit
      Screen.Platform -> currentScreen = Screen.Home
      Screen.Player -> {
        currentScreen = playerReturnScreen ?: Screen.Home
        playerReturnScreen = null
        currentStreamer = null
        playerFullscreen = false
      }
      Screen.Search -> {
        currentScreen = searchReturnScreen ?: Screen.Home
        searchReturnScreen = null
      }
      Screen.Sync -> currentScreen = Screen.Home
    }
  }
}

enum class ThemeMode { System, Light, Dark }

enum class Screen { Home, Platform, Player, Search, Sync }

@Composable
fun rememberAppState(
  repo: DtvRepository = FakeDtvRepository(),
  subscriptionStore: SubscriptionStore = InMemorySubscriptionStore,
): AppState {
  return remember(repo, subscriptionStore) { AppState(repo = repo, subscriptionStore = subscriptionStore) }
}
