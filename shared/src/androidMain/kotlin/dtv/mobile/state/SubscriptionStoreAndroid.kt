package dtv.mobile.state

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import dtv.mobile.model.Streamer

class SubscriptionStoreAndroid(
  appContext: Context,
) : SubscriptionStore {
  private val prefs = appContext.getSharedPreferences("dtv_subscriptions", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }

  override fun loadFollowedStreamers(): List<Streamer> {
    val raw = prefs.getString("followed_streamers", null)?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString(ListSerializer(Streamer.serializer()), raw) }.getOrElse { emptyList() }
  }

  override fun saveFollowedStreamers(items: List<Streamer>) {
    val raw = json.encodeToString(ListSerializer(Streamer.serializer()), items)
    prefs.edit().putString("followed_streamers", raw).apply()
  }

  override fun loadPinnedFollowedStreamerKeys(): List<String> {
    val raw = prefs.getString("pinned_followed_streamer_keys", null)?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrElse { emptyList() }
  }

  override fun savePinnedFollowedStreamerKeys(items: List<String>) {
    val raw = json.encodeToString(ListSerializer(String.serializer()), items)
    prefs.edit().putString("pinned_followed_streamer_keys", raw).apply()
  }

  override fun loadLandscapeDanmakuFontScale(): Float {
    val raw = prefs.getFloat("landscape_danmaku_font_scale", 1.2f)
    return raw.coerceIn(0.85f, 2.0f)
  }

  override fun saveLandscapeDanmakuFontScale(value: Float) {
    prefs.edit().putFloat("landscape_danmaku_font_scale", value.coerceIn(0.85f, 2.0f)).apply()
  }

  override fun loadDanmakuFontScale(): Float {
    val raw = prefs.getFloat("danmaku_font_scale", 1.0f)
    return raw.coerceIn(0.85f, 1.3f)
  }

  override fun saveDanmakuFontScale(value: Float) {
    prefs.edit().putFloat("danmaku_font_scale", value.coerceIn(0.85f, 1.3f)).apply()
  }

  override fun loadDanmakuOpacity(): Float {
    val raw = prefs.getFloat("danmaku_opacity", 1.0f)
    return raw.coerceIn(0.35f, 1.0f)
  }

  override fun saveDanmakuOpacity(value: Float) {
    prefs.edit().putFloat("danmaku_opacity", value.coerceIn(0.35f, 1.0f)).apply()
  }

  override fun loadDanmakuAreaFraction(): Float {
    val raw = prefs.getFloat("danmaku_area_fraction", 0.5f)
    return raw.coerceIn(0.25f, 1.0f)
  }

  override fun saveDanmakuAreaFraction(value: Float) {
    prefs.edit().putFloat("danmaku_area_fraction", value.coerceIn(0.25f, 1.0f)).apply()
  }

  override fun loadSubscribedPartitions(): List<SubscribedPartition> {
    val raw = prefs.getString("subscribed_partitions", null)?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString(ListSerializer(SubscribedPartition.serializer()), raw) }.getOrElse { emptyList() }
  }

  override fun saveSubscribedPartitions(items: List<SubscribedPartition>) {
    val raw = json.encodeToString(ListSerializer(SubscribedPartition.serializer()), items)
    prefs.edit().putString("subscribed_partitions", raw).apply()
  }

  override fun loadDanmuBlockKeywords(): List<String> {
    val raw = prefs.getString("danmu_block_keywords", null)?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrElse { emptyList() }
  }

  override fun saveDanmuBlockKeywords(items: List<String>) {
    val raw = json.encodeToString(ListSerializer(String.serializer()), items)
    prefs.edit().putString("danmu_block_keywords", raw).apply()
  }

  override fun loadSimpleModeByPlatform(): List<SimpleModeEntry> {
    val raw = prefs.getString("simple_mode_by_platform", null)?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString(ListSerializer(SimpleModeEntry.serializer()), raw) }.getOrElse { emptyList() }
  }

  override fun saveSimpleModeByPlatform(items: List<SimpleModeEntry>) {
    val raw = json.encodeToString(ListSerializer(SimpleModeEntry.serializer()), items)
    prefs.edit().putString("simple_mode_by_platform", raw).apply()
  }
}
