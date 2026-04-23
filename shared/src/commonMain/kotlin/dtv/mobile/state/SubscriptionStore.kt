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
  fun loadFollowedStreamers(): List<Streamer>
  fun saveFollowedStreamers(items: List<Streamer>)

  fun loadSubscribedPartitions(): List<SubscribedPartition>
  fun saveSubscribedPartitions(items: List<SubscribedPartition>)

  fun loadSimpleModeByPlatform(): List<SimpleModeEntry>
  fun saveSimpleModeByPlatform(items: List<SimpleModeEntry>)
}

object InMemorySubscriptionStore : SubscriptionStore {
  private var followed: List<Streamer> = emptyList()
  private var partitions: List<SubscribedPartition> = emptyList()
  private var simpleModes: List<SimpleModeEntry> = emptyList()

  override fun loadFollowedStreamers(): List<Streamer> = followed

  override fun saveFollowedStreamers(items: List<Streamer>) {
    followed = items.toList()
  }

  override fun loadSubscribedPartitions(): List<SubscribedPartition> = partitions

  override fun saveSubscribedPartitions(items: List<SubscribedPartition>) {
    partitions = items.toList()
  }

  override fun loadSimpleModeByPlatform(): List<SimpleModeEntry> = simpleModes

  override fun saveSimpleModeByPlatform(items: List<SimpleModeEntry>) {
    simpleModes = items.toList()
  }
}
