package dtv.mobile.state

import dtv.mobile.model.Streamer
import dtv.mobile.model.Platform
import kotlinx.serialization.Serializable

@Serializable
data class SimpleModeEntry(
  val platform: Platform,
  val enabled: Boolean,
)

interface SubscriptionStore {
  fun loadThemeMode(): ThemeMode
  fun saveThemeMode(value: ThemeMode)

  fun loadFollowedStreamers(): List<Streamer>
  fun saveFollowedStreamers(items: List<Streamer>)

  fun loadPinnedFollowedStreamerKeys(): List<String>
  fun savePinnedFollowedStreamerKeys(items: List<String>)

  fun loadLandscapeDanmakuFontScale(): Float
  fun saveLandscapeDanmakuFontScale(value: Float)

  fun loadDanmakuFontScale(): Float
  fun saveDanmakuFontScale(value: Float)

  fun loadDanmakuOpacity(): Float
  fun saveDanmakuOpacity(value: Float)

  fun loadDanmakuAreaFraction(): Float
  fun saveDanmakuAreaFraction(value: Float)

  fun loadSubscribedPartitions(): List<SubscribedPartition>
  fun saveSubscribedPartitions(items: List<SubscribedPartition>)

  fun loadDanmuBlockKeywords(): List<String>
  fun saveDanmuBlockKeywords(items: List<String>)

  fun loadSimpleModeByPlatform(): List<SimpleModeEntry>
  fun saveSimpleModeByPlatform(items: List<SimpleModeEntry>)
}

object InMemorySubscriptionStore : SubscriptionStore {
  private var themeMode: ThemeMode = ThemeMode.System
  private var followed: List<Streamer> = emptyList()
  private var pinnedKeys: List<String> = emptyList()
  private var landscapeDanmakuFontScale: Float = 1.2f
  private var danmakuFontScale: Float = 1.0f
  private var danmakuOpacity: Float = 1.0f
  private var danmakuAreaFraction: Float = 0.5f
  private var partitions: List<SubscribedPartition> = emptyList()
  private var danmuBlockKeywords: List<String> = emptyList()
  private var simpleModes: List<SimpleModeEntry> = emptyList()

  override fun loadThemeMode(): ThemeMode = themeMode

  override fun saveThemeMode(value: ThemeMode) {
    themeMode = value
  }

  override fun loadFollowedStreamers(): List<Streamer> = followed

  override fun saveFollowedStreamers(items: List<Streamer>) {
    followed = items.toList()
  }

  override fun loadPinnedFollowedStreamerKeys(): List<String> = pinnedKeys

  override fun savePinnedFollowedStreamerKeys(items: List<String>) {
    pinnedKeys = items.toList()
  }

  override fun loadLandscapeDanmakuFontScale(): Float = landscapeDanmakuFontScale

  override fun saveLandscapeDanmakuFontScale(value: Float) {
    landscapeDanmakuFontScale = value
  }

  override fun loadDanmakuFontScale(): Float = danmakuFontScale

  override fun saveDanmakuFontScale(value: Float) {
    danmakuFontScale = value
  }

  override fun loadDanmakuOpacity(): Float = danmakuOpacity

  override fun saveDanmakuOpacity(value: Float) {
    danmakuOpacity = value
  }

  override fun loadDanmakuAreaFraction(): Float = danmakuAreaFraction

  override fun saveDanmakuAreaFraction(value: Float) {
    danmakuAreaFraction = value
  }

  override fun loadSubscribedPartitions(): List<SubscribedPartition> = partitions

  override fun saveSubscribedPartitions(items: List<SubscribedPartition>) {
    partitions = items.toList()
  }

  override fun loadDanmuBlockKeywords(): List<String> = danmuBlockKeywords

  override fun saveDanmuBlockKeywords(items: List<String>) {
    danmuBlockKeywords = items.toList()
  }

  override fun loadSimpleModeByPlatform(): List<SimpleModeEntry> = simpleModes

  override fun saveSimpleModeByPlatform(items: List<SimpleModeEntry>) {
    simpleModes = items.toList()
  }
}
