package dtv.mobile.sync

import kotlinx.serialization.Serializable

const val LAN_SYNC_KIND: String = "dtv-lan-sync"
const val LAN_SYNC_VERSION: Int = 1
const val LAN_SYNC_FIXED_PORT: Int = 38999
const val LAN_SYNC_HTTP_PATH: String = "/dtv-sync"
const val LAN_SYNC_PAYLOAD_PATH: String = "/dtv-sync/payload"
const val LAN_SYNC_DEFAULT_TOKEN: String = "dtv"

@Serializable
data class LanSyncSource(
  val client: String,
  val appVersion: String? = null,
)

@Serializable
data class LanSyncPayload(
  val kind: String = LAN_SYNC_KIND,
  val version: Int = LAN_SYNC_VERSION,
  val exportedAt: String,
  val source: LanSyncSource,
  val entries: Map<String, String>,
)

@Serializable
data class LanSyncSummary(
  val followedStreamers: Int = 0,
  val followFolders: Int = 0,
  val followListOrder: Int = 0,
  val customCategories: Int = 0,
  val totalBytes: Int = 0,
)

@Serializable
data class LanSyncManifest(
  val kind: String = LAN_SYNC_KIND,
  val version: Int = LAN_SYNC_VERSION,
  val exportedAt: String,
  val source: LanSyncSource,
  val summary: LanSyncSummary,
)

@Serializable
data class LanSyncServerInfo(
  val port: Int,
  val hosts: List<String>,
  val token: String,
)

@Serializable
data class LanSyncDiscoveredPeer(
  val name: String,
  val host: String,
  val port: Int,
  val token: String? = null,
  val baseUrl: String,
)

