package dtv.mobile.platform.douyin

import dtv.mobile.platform.common.arrayAt
import dtv.mobile.platform.common.jsonBooleanOrNull
import dtv.mobile.platform.common.jsonLongOrNull
import dtv.mobile.platform.common.jsonObjects
import dtv.mobile.platform.common.jsonStringOrNull
import dtv.mobile.platform.common.objectAt
import dtv.mobile.platform.common.stringAt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DouyinPartitionRoom(
  val webRid: String,
  val title: String,
  val ownerNickname: String,
  val coverUrl: String?,
  val avatarUrl: String?,
  val userCountStr: String,
)

data class DouyinPartitionResult(
  val rooms: List<DouyinPartitionRoom>,
  val hasMore: Boolean,
)

data class DouyinRoomStreamUrls(
  val hlsPullUrlMap: Map<String, String>,
  val flvPullUrlMap: Map<String, String>,
)

data class DouyinRoomEnterResult(
  val title: String?,
  val anchorName: String?,
  val avatarUrl: String?,
  val coverUrl: String?,
  val viewerCountStr: String?,
  val roomId: String?,
  val streamUrls: DouyinRoomStreamUrls,
)

internal object DouyinResponseParser {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parsePartitionResponse(text: String, limit: Int): DouyinPartitionResult {
    val root = json.parseToJsonElement(text).jsonObject
    val status = root["status_code"]?.jsonPrimitive?.intOrNull ?: -1
    if (status != 0) {
      val message = root.objectAt(listOf("data"))?.stringAt(listOf("message"))
      error(message ?: "抖音分区列表接口错误: status_code=$status")
    }

    val wrapper = root["data"] as? JsonObject
      ?: return DouyinPartitionResult(emptyList(), hasMore = false)
    val entries = wrapper["data"].jsonObjects()
    val rooms = entries.mapNotNull { entry ->
      val webRid = entry["web_rid"].jsonStringOrNull()?.trim().orEmpty()
      val room = entry["room"] as? JsonObject ?: return@mapNotNull null
      val title = room["title"].jsonStringOrNull()?.trim().orEmpty()
      val owner = room.objectAt(listOf("owner"))
      val ownerNickname = owner?.stringAt(listOf("nickname"))?.trim().orEmpty()
      val coverUrl = room.arrayAt(listOf("cover", "url_list"))?.firstOrNull().jsonStringOrNull()
      val avatarUrl = owner?.arrayAt(listOf("avatar_thumb", "url_list"))?.firstOrNull().jsonStringOrNull()
      val stats = room.objectAt(listOf("stats"))
      val userCount = stats?.stringAt(listOf("user_count_str"))
        ?: stats?.stringAt(listOf("total_user_str"))

      if (webRid.isBlank()) return@mapNotNull null
      DouyinPartitionRoom(
        webRid = webRid,
        title = title.ifBlank { "直播中" },
        ownerNickname = ownerNickname.ifBlank { "抖音主播" },
        coverUrl = coverUrl,
        avatarUrl = avatarUrl,
        userCountStr = userCount?.trim().orEmpty().ifBlank { "直播中" },
      )
    }

    val hasMore = wrapper["has_more"].jsonBooleanOrNull() ?: (rooms.size == limit)
    return DouyinPartitionResult(rooms = rooms, hasMore = hasMore)
  }

  fun parseRoomEnterResponse(text: String): DouyinRoomEnterResult {
    val root = json.parseToJsonElement(text).jsonObject
    val room = root.arrayAt(listOf("data", "data"))?.firstOrNull() as? JsonObject
    val user = root.objectAt(listOf("data", "user"))
    val streamUrl = room?.objectAt(listOf("stream_url"))
    val hls = streamUrl?.objectAt(listOf("hls_pull_url_map"))
      ?.mapValues { (_, value) -> value.jsonStringOrNull().orEmpty() }
      .orEmpty()
      .filterValues { it.isNotBlank() }
    val flv = streamUrl?.objectAt(listOf("flv_pull_url"))
      ?.mapValues { (_, value) -> value.jsonStringOrNull().orEmpty() }
      .orEmpty()
      .filterValues { it.isNotBlank() }

    return DouyinRoomEnterResult(
      title = room?.stringAt(listOf("title")),
      anchorName = user?.stringAt(listOf("nickname")),
      avatarUrl = user?.arrayAt(listOf("avatar_thumb", "url_list"))?.firstOrNull().jsonStringOrNull(),
      coverUrl = room?.arrayAt(listOf("cover", "url_list"))?.firstOrNull().jsonStringOrNull(),
      viewerCountStr = room?.objectAt(listOf("stats"))?.let { stats ->
        stats.stringAt(listOf("user_count_str")) ?: stats.stringAt(listOf("total_user_str"))
      },
      roomId = room?.get("id_str").jsonStringOrNull() ?: room?.get("id").jsonStringOrNull(),
      streamUrls = DouyinRoomStreamUrls(
        hlsPullUrlMap = hls,
        flvPullUrlMap = flv,
      ),
    )
  }

  fun isRetryableFailure(
    httpStatus: Int,
    text: String,
    roomResult: DouyinRoomEnterResult? = null,
  ): Boolean {
    if (httpStatus !in 200..299) return true
    if (text.isBlank()) return true
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return true
    val status = root["status_code"]?.jsonPrimitive?.intOrNull
    if (status != null && status != 0) return true
    return roomResult != null &&
      roomResult.streamUrls.hlsPullUrlMap.isEmpty() &&
      roomResult.streamUrls.flvPullUrlMap.isEmpty()
  }
}
