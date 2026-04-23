package dtv.mobile.state

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
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

  override fun loadSubscribedPartitions(): List<SubscribedPartition> {
    val raw = prefs.getString("subscribed_partitions", null)?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { json.decodeFromString(ListSerializer(SubscribedPartition.serializer()), raw) }.getOrElse { emptyList() }
  }

  override fun saveSubscribedPartitions(items: List<SubscribedPartition>) {
    val raw = json.encodeToString(ListSerializer(SubscribedPartition.serializer()), items)
    prefs.edit().putString("subscribed_partitions", raw).apply()
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
