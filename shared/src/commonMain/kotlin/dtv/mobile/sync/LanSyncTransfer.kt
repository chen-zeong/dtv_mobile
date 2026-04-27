package dtv.mobile.sync

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.repo.BilibiliCate1
import dtv.mobile.repo.DouyinCate1
import dtv.mobile.repo.DouyuCategories
import dtv.mobile.repo.HuyaCate1
import dtv.mobile.state.AppState
import dtv.mobile.state.SubscribedPartition
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
private data class DesktopFollowedStreamer(
  val id: String,
  val platform: String,
  val nickname: String? = null,
  val avatarUrl: String? = null,
  val roomTitle: String? = null,
  val currentRoomId: String,
  val liveStatus: String? = null,
)

@Serializable
private data class DesktopCustomCategoryEntry(
  val key: String,
  val platform: String,
  val cate2Name: String,
  val cate1Name: String? = null,
  val cate1Href: String? = null,
  val cate2Href: String? = null,
  val douyuId: String? = null,
)

@Serializable
private data class DesktopFollowFolder(
  val id: String? = null,
  val name: String? = null,
  val streamerIds: List<String> = emptyList(),
)

data class LanSyncImportResult(
  val addedFollowedStreamers: Int,
  val addedSubscribedPartitions: Int,
  val addedDanmuBlockKeywords: Int,
  val skippedPartitions: Int,
)

private val lanSyncJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

private fun platformFromDesktop(value: String): Platform? {
  return when (value.trim().uppercase()) {
    "DOUYU" -> Platform.Douyu
    "DOUYIN" -> Platform.Douyin
    "HUYA" -> Platform.Huya
    "BILIBILI" -> Platform.Bilibili
    else -> null
  }
}

private fun desktopPlatformFromMobile(value: Platform): String {
  return when (value) {
    Platform.Douyu -> "DOUYU"
    Platform.Douyin -> "DOUYIN"
    Platform.Huya -> "HUYA"
    Platform.Bilibili -> "BILIBILI"
    else -> value.name.uppercase()
  }
}

private fun safeDecodeStringList(raw: String?): List<String> {
  if (raw.isNullOrBlank()) return emptyList()
  return runCatching { lanSyncJson.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrElse { emptyList() }
}

private fun <T> safeDecodeList(raw: String?, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> {
  if (raw.isNullOrBlank()) return emptyList()
  return runCatching { lanSyncJson.decodeFromString(serializer, raw) }.getOrElse { emptyList() }
}

private fun normalizeStreamerKey(raw: String): Pair<String, String>? {
  val parts = raw.split(":")
  if (parts.size < 2) return null
  val platform = parts[0].trim().uppercase()
  val id = parts[1].trim()
  if (platform.isEmpty() || id.isEmpty()) return null
  return platform to id
}

private fun normalizeCustomCategoryPlatform(raw: String): String? {
  val p = raw.trim().lowercase()
  return if (p == "douyu" || p == "douyin" || p == "huya" || p == "bilibili") p else null
}

private fun extractIdFromKey(platform: String, key: String): String? {
  val prefix = "$platform:"
  if (!key.startsWith(prefix)) return null
  return key.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
}

suspend fun applyIncrementalLanSyncImport(appState: AppState, payload: LanSyncPayload): LanSyncImportResult {
  val entries = payload.entries

  val followedFromKey = safeDecodeList(entries["followedStreamers"], ListSerializer(DesktopFollowedStreamer.serializer()))
  val followFolders = safeDecodeList(entries["followFolders"], ListSerializer(DesktopFollowFolder.serializer()))
  val followListOrderRaw = entries["followListOrder"]

  val incomingFollowed = ArrayList<Streamer>()

  if (followedFromKey.isNotEmpty()) {
    for (s in followedFromKey) {
      val p = platformFromDesktop(s.platform) ?: continue
      val roomId = s.currentRoomId.ifBlank { s.id }
      if (roomId.isBlank()) continue
      incomingFollowed.add(
        Streamer(
          platform = p,
          roomId = roomId,
          name = (s.nickname ?: roomId).ifBlank { roomId },
          title = s.roomTitle ?: "",
          viewerText = "",
          avatarUrl = s.avatarUrl,
          isLive = s.liveStatus?.uppercase() == "LIVE",
        ),
      )
    }
  } else {
    val keysFromFolders = followFolders.asSequence()
      .flatMap { it.streamerIds.asSequence() }
      .mapNotNull { normalizeStreamerKey(it) }
      .toSet()

    val keysFromOrder: Set<Pair<String, String>> = if (!followListOrderRaw.isNullOrBlank()) {
      // Follow list order is complex union type; parse as JsonElement and extract "platform:id" from strings if present.
      runCatching {
        val root = lanSyncJson.parseToJsonElement(followListOrderRaw)
        val out = HashSet<Pair<String, String>>()
        val arr = root as? kotlinx.serialization.json.JsonArray ?: return@runCatching out
        for (item in arr) {
          val obj = item as? kotlinx.serialization.json.JsonObject ?: continue
          val type = obj["type"] as? kotlinx.serialization.json.JsonPrimitive
          if (type?.content != "streamer") continue
          val data = obj["data"] as? kotlinx.serialization.json.JsonObject ?: continue
          val platform = (data["platform"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
          val id = (data["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
          val key = normalizeStreamerKey("${platform.uppercase()}:$id") ?: continue
          out.add(key)
        }
        out
      }.getOrElse { emptySet() }
    } else {
      emptySet()
    }

    for ((platformRaw, id) in (keysFromFolders + keysFromOrder)) {
      val p = platformFromDesktop(platformRaw) ?: continue
      incomingFollowed.add(
        Streamer(
          platform = p,
          roomId = id,
          name = id,
          title = "",
          viewerText = "",
          avatarUrl = null,
          isLive = true,
        ),
      )
    }
  }

  val addedFollowed = appState.mergeFollowedStreamers(incomingFollowed)

  val incomingKeywords = safeDecodeStringList(entries["danmu_block_keywords"])
  val addedKeywords = appState.mergeDanmuBlockKeywords(incomingKeywords)

  val incomingPartitions = ArrayList<SubscribedPartition>()
  var skippedPartitions = 0

  // Prefer mobile-native payload if present.
  val mobilePartitions = safeDecodeList(
    entries["dtv_mobile_subscribed_partitions_v1"],
    ListSerializer(SubscribedPartition.serializer()),
  )
  if (mobilePartitions.isNotEmpty()) {
    incomingPartitions.addAll(mobilePartitions)
  } else {
    val remoteCustomCategories = safeDecodeList(
      entries["dtv_custom_categories_v1"],
      ListSerializer(DesktopCustomCategoryEntry.serializer()),
    )
    if (remoteCustomCategories.isNotEmpty()) {
      val mapped = mapDesktopCustomCategoriesToMobile(appState, remoteCustomCategories)
      incomingPartitions.addAll(mapped.mapped)
      skippedPartitions += mapped.skipped
    }
  }

  val addedPartitions = appState.mergeSubscribedPartitions(incomingPartitions)

  return LanSyncImportResult(
    addedFollowedStreamers = addedFollowed,
    addedSubscribedPartitions = addedPartitions,
    addedDanmuBlockKeywords = addedKeywords,
    skippedPartitions = skippedPartitions,
  )
}

private data class PartitionMappingResult(
  val mapped: List<SubscribedPartition>,
  val skipped: Int,
)

private suspend fun mapDesktopCustomCategoriesToMobile(
  appState: AppState,
  remote: List<DesktopCustomCategoryEntry>,
): PartitionMappingResult {
  var douyuCategories: DouyuCategories? = null
  var huyaCategories: List<HuyaCate1>? = null
  var bilibiliCategories: List<BilibiliCate1>? = null
  var douyinCategories: List<DouyinCate1>? = null

  val out = ArrayList<SubscribedPartition>()
  var skipped = 0

  for (entry in remote) {
    val p = normalizeCustomCategoryPlatform(entry.platform)
    if (p == null) {
      skipped += 1
      continue
    }
    when (p) {
      "douyu" -> {
        val shortName = (entry.douyuId ?: extractIdFromKey("douyu", entry.key)).orEmpty().trim()
        if (shortName.isEmpty()) {
          skipped += 1
          continue
        }
        if (douyuCategories == null) douyuCategories = runCatching { appState.repo.fetchDouyuCategories() }.getOrNull()
        val categories = douyuCategories
        if (categories == null) {
          skipped += 1
          continue
        }
        val match = categories.cate1List.asSequence()
          .flatMap { it.cate2List.asSequence() }
          .firstOrNull { it.shortName == shortName }
        if (match == null) {
          skipped += 1
          continue
        }
        out.add(SubscribedPartition(id = "douyu:c2:${match.id}", name = match.name, platform = Platform.Douyu))
      }
      "huya" -> {
        val href = (entry.cate2Href ?: extractIdFromKey("huya", entry.key)).orEmpty().trim()
        if (href.isEmpty()) {
          skipped += 1
          continue
        }
        if (huyaCategories == null) huyaCategories = runCatching { appState.repo.fetchHuyaCategories() }.getOrNull()
        val categories = huyaCategories
        if (categories == null) {
          skipped += 1
          continue
        }
        val match = categories.asSequence().flatMap { it.cate2List.asSequence() }.firstOrNull { it.href == href }
          ?: categories.asSequence().flatMap { it.cate2List.asSequence() }.firstOrNull { it.gid == href.removePrefix("/g/") }
        if (match == null) {
          skipped += 1
          continue
        }
        out.add(SubscribedPartition(id = "huya:${match.gid}", name = match.name, platform = Platform.Huya))
      }
      "bilibili" -> {
        val href = (entry.cate2Href ?: extractIdFromKey("bilibili", entry.key)).orEmpty().trim()
        if (href.isEmpty()) {
          skipped += 1
          continue
        }
        if (bilibiliCategories == null) bilibiliCategories = runCatching { appState.repo.fetchBilibiliCategories() }.getOrNull()
        val categories = bilibiliCategories
        if (categories == null) {
          skipped += 1
          continue
        }
        val match = categories.asSequence().flatMap { it.cate2List.asSequence() }.firstOrNull { it.href == href }
        if (match == null) {
          skipped += 1
          continue
        }
        out.add(SubscribedPartition(id = "bilibili:${match.parentAreaId}:${match.areaId}", name = match.name, platform = Platform.Bilibili))
      }
      "douyin" -> {
        val href = (entry.cate2Href ?: extractIdFromKey("douyin", entry.key)).orEmpty().trim()
        if (href.isEmpty()) {
          skipped += 1
          continue
        }
        if (douyinCategories == null) douyinCategories = runCatching { appState.repo.fetchDouyinCategories() }.getOrNull()
        val categories = douyinCategories
        if (categories == null) {
          skipped += 1
          continue
        }
        val match = categories.asSequence().flatMap { it.cate2List.asSequence() }.firstOrNull { it.href == href }
        if (match == null) {
          skipped += 1
          continue
        }
        out.add(SubscribedPartition(id = "douyin:${match.partitionType}:${match.partition}", name = match.name, platform = Platform.Douyin))
      }
    }
  }

  return PartitionMappingResult(mapped = out, skipped = skipped)
}

@Serializable
private data class ExportedCustomCategoryEntry(
  val key: String,
  val platform: String,
  val cate2Name: String,
  val cate1Name: String? = null,
  val cate1Href: String? = null,
  val cate2Href: String? = null,
  val douyuId: String? = null,
)

@Serializable
private data class ExportedDesktopFollowedStreamer(
  val id: String,
  val platform: String,
  val nickname: String,
  val avatarUrl: String,
  val roomTitle: String? = null,
  val currentRoomId: String,
  val liveStatus: String,
)

suspend fun buildMobileLanSyncPayload(appState: AppState, appVersion: String? = null): LanSyncPayload {
  val followed = appState.followedStreamers.toList().map { s ->
    ExportedDesktopFollowedStreamer(
      id = s.roomId,
      platform = desktopPlatformFromMobile(s.platform),
      nickname = s.name,
      avatarUrl = s.avatarUrl ?: "",
      roomTitle = s.title.ifBlank { null },
      currentRoomId = s.roomId,
      liveStatus = if (s.isLive) "LIVE" else "OFFLINE",
    )
  }

  val keywords = appState.danmuBlockKeywords.toList()

  val entries = linkedMapOf<String, String>()
  entries["followedStreamers"] = lanSyncJson.encodeToString(ListSerializer(ExportedDesktopFollowedStreamer.serializer()), followed)
  entries["danmu_block_keywords"] = lanSyncJson.encodeToString(ListSerializer(String.serializer()), keywords)
  entries["dtv_mobile_subscribed_partitions_v1"] = lanSyncJson.encodeToString(
    ListSerializer(SubscribedPartition.serializer()),
    appState.subscribedPartitions.toList(),
  )

  val customCategories = exportMobilePartitionsAsDesktopCustomCategories(appState)
  if (customCategories.isNotEmpty()) {
    entries["dtv_custom_categories_v1"] = lanSyncJson.encodeToString(
      ListSerializer(ExportedCustomCategoryEntry.serializer()),
      customCategories,
    )
  }

  return LanSyncPayload(
    kind = LAN_SYNC_KIND,
    version = LAN_SYNC_VERSION,
    exportedAt = currentIsoTimestamp(),
    source = LanSyncSource(client = "mobile", appVersion = appVersion),
    entries = entries,
  )
}

private suspend fun exportMobilePartitionsAsDesktopCustomCategories(appState: AppState): List<ExportedCustomCategoryEntry> {
  val partitions = appState.subscribedPartitions.toList()
  if (partitions.isEmpty()) return emptyList()

  var douyuCategories: DouyuCategories? = null
  var huyaCategories: List<HuyaCate1>? = null
  var bilibiliCategories: List<BilibiliCate1>? = null
  var douyinCategories: List<DouyinCate1>? = null

  val out = ArrayList<ExportedCustomCategoryEntry>()

  for (p in partitions) {
    when (p.platform) {
      Platform.Douyu -> {
        val cate2Id = Regex("^douyu:c2:(.+)$").matchEntire(p.id)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (cate2Id.isEmpty()) continue
        if (douyuCategories == null) douyuCategories = runCatching { appState.repo.fetchDouyuCategories() }.getOrNull()
        val categories = douyuCategories ?: continue
        val match = categories.cate1List.asSequence()
          .flatMap { it.cate2List.asSequence() }
          .firstOrNull { it.id == cate2Id }
          ?: continue
        val shortName = match.shortName
        if (shortName.isBlank()) continue
        out.add(
          ExportedCustomCategoryEntry(
            key = "douyu:$shortName",
            platform = "douyu",
            cate2Name = match.name,
            douyuId = shortName,
          ),
        )
      }
      Platform.Huya -> {
        val gid = Regex("^huya:(.+)$").matchEntire(p.id)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (gid.isEmpty()) continue
        if (huyaCategories == null) huyaCategories = runCatching { appState.repo.fetchHuyaCategories() }.getOrNull()
        val categories = huyaCategories ?: continue
        val match = categories.asSequence().flatMap { it.cate2List.asSequence() }.firstOrNull { it.gid == gid } ?: continue
        val href = match.href ?: continue
        val cate1 = categories.firstOrNull { c1 -> c1.cate2List.any { it.gid == gid } }
        out.add(
          ExportedCustomCategoryEntry(
            key = "huya:$href",
            platform = "huya",
            cate2Name = match.name,
            cate1Name = cate1?.name,
            cate1Href = cate1?.href,
            cate2Href = href,
          ),
        )
      }
      Platform.Bilibili -> {
        val m = Regex("^bilibili:(\\d+):(\\d+)$").matchEntire(p.id) ?: continue
        val parentAreaId = m.groupValues[1].toIntOrNull() ?: continue
        val areaId = m.groupValues[2].toIntOrNull() ?: continue
        if (bilibiliCategories == null) bilibiliCategories = runCatching { appState.repo.fetchBilibiliCategories() }.getOrNull()
        val categories = bilibiliCategories ?: continue
        val match = categories.asSequence()
          .flatMap { it.cate2List.asSequence() }
          .firstOrNull { it.parentAreaId == parentAreaId && it.areaId == areaId }
          ?: continue
        val href = match.href ?: continue
        val cate1 = categories.firstOrNull { it.parentAreaId == parentAreaId }
        out.add(
          ExportedCustomCategoryEntry(
            key = "bilibili:$href",
            platform = "bilibili",
            cate2Name = match.name,
            cate1Name = cate1?.name,
            cate1Href = cate1?.href,
            cate2Href = href,
          ),
        )
      }
      Platform.Douyin -> {
        val m = Regex("^douyin:([^:]+):(.+)$").matchEntire(p.id) ?: continue
        val partitionType = m.groupValues[1]
        val partition = m.groupValues[2]
        if (douyinCategories == null) douyinCategories = runCatching { appState.repo.fetchDouyinCategories() }.getOrNull()
        val categories = douyinCategories ?: continue
        val match = categories.asSequence()
          .flatMap { it.cate2List.asSequence() }
          .firstOrNull { it.partitionType == partitionType && it.partition == partition }
          ?: continue
        val href = match.href ?: continue
        val cate1 = categories.firstOrNull { c1 -> c1.cate2List.any { it.partitionType == partitionType && it.partition == partition } }
        out.add(
          ExportedCustomCategoryEntry(
            key = "douyin:$href",
            platform = "douyin",
            cate2Name = match.name,
            cate1Name = cate1?.name,
            cate1Href = cate1?.href,
            cate2Href = href,
          ),
        )
      }
      else -> Unit
    }
  }

  // Dedupe by key and preserve order.
  val seen = HashSet<String>()
  val deduped = ArrayList<ExportedCustomCategoryEntry>()
  for (e in out) {
    if (!seen.add(e.key)) continue
    deduped.add(e)
  }
  return deduped
}

fun normalizeImportTarget(input: String, fallbackToken: String = LAN_SYNC_DEFAULT_TOKEN): Pair<String, String> {
  val raw = input.trim()
  require(raw.isNotEmpty()) { "请输入共享端 IP 或 URL。" }

  fun hasExplicitPort(value: String): Boolean {
    // scheme://host:port/... OR scheme://[ipv6]:port/...
    return Regex("^\\w+://\\[[^\\]]+\\]:(\\d+)(/|\\?|$)").containsMatchIn(value) ||
      Regex("^\\w+://[^/:?#]+:(\\d+)(/|\\?|$)").containsMatchIn(value)
  }

  val normalizedUrlText =
    if (!raw.contains("://") && (raw.contains("/") || raw.contains("?"))) {
      // Allow inputs like "192.168.1.8:38999/dtv-sync?token=dtv" (missing scheme).
      "http://$raw"
    } else {
      raw
    }

  if (normalizedUrlText.contains("://")) {
    val url = io.ktor.http.Url(normalizedUrlText)
    val token = url.parameters["token"]?.takeIf { it.isNotBlank() } ?: fallbackToken
    val explicitPort = hasExplicitPort(normalizedUrlText)
    val port = if (explicitPort) url.port else LAN_SYNC_FIXED_PORT
    val baseUrl = "${url.protocol.name}://${url.host}:$port"
    return baseUrl to token
  }

  val baseUrl = if (raw.contains(":")) "http://$raw" else "http://$raw:$LAN_SYNC_FIXED_PORT"
  return baseUrl to fallbackToken
}
