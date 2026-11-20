package dtv.mobile.platform.douyu

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.platform.common.jsonLongOrNull
import dtv.mobile.platform.common.jsonObjects
import dtv.mobile.platform.common.jsonStringOrNull
import dtv.mobile.platform.common.objectAt
import dtv.mobile.util.formatViewerCountWanIfNeeded
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal object DouyuResponseParsers {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parseSearchResponse(text: String): List<Streamer> {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val list = findSearchList(root["data"]) ?: return emptyList()

    return list.mapNotNull { item ->
      val itemObj = item as? JsonObject ?: return@mapNotNull null
      val type = itemObj["type"].jsonLongOrNull()
      if (type != null && type != 1L) return@mapNotNull null

      val anchor = itemObj.objectAt(listOf("anchorInfo")) ?: itemObj
      val roomId = anchor["rid"].jsonStringOrNull()
        ?: anchor["room_id"].jsonStringOrNull()
        ?: anchor["roomId"].jsonStringOrNull()
        ?: anchor["rid"].jsonLongOrNull()?.toString()
        ?: anchor["room_id"].jsonLongOrNull()?.toString()
        ?: anchor["roomId"].jsonLongOrNull()?.toString()
        ?: anchor["bkUrl"].jsonStringOrNull()?.trimEnd('/')?.substringAfterLast('/')
        ?: ""
      if (roomId.isBlank()) return@mapNotNull null

      val name = anchor["nickName"].jsonStringOrNull()
        ?: anchor["nickname"].jsonStringOrNull()
        ?: anchor["user_name"].jsonStringOrNull()
        ?: anchor["userName"].jsonStringOrNull()
        ?: ""
      if (name.isBlank()) return@mapNotNull null

      val title = anchor["roomName"].jsonStringOrNull()
        ?: anchor["room_name"].jsonStringOrNull()
        ?: anchor["description"].jsonStringOrNull()
        ?: anchor["title"].jsonStringOrNull()
        ?: "暂无标题"
      val avatar = anchor["avatar"].jsonStringOrNull()
        ?: anchor["avatar_url"].jsonStringOrNull()
        ?: anchor["avatarUrl"].jsonStringOrNull()
      val isLive = anchor["isLive"].jsonLongOrNull() ?: anchor["is_live"].jsonLongOrNull()
      val isLoop = anchor["isLoop"].jsonLongOrNull() ?: anchor["is_loop"].jsonLongOrNull()
      val videoLoop = anchor["videoLoop"].jsonLongOrNull() ?: anchor["video_loop"].jsonLongOrNull()

      Streamer(
        platform = Platform.Douyu,
        roomId = roomId,
        name = name,
        title = title.ifBlank { "暂无标题" },
        viewerText = "",
        avatarUrl = normalizeHttpUrl(avatar),
        coverUrl = null,
        isLive = resolveDouyuLiveStatus(isLive, videoLoop, isLoop) ?: true,
      )
    }
  }

  private fun findSearchList(data: kotlinx.serialization.json.JsonElement?): JsonArray? {
    if (data is JsonArray) return data
    val obj = data as? JsonObject ?: return null
    return listOf("relateUser", "relate_user", "relate", "relateUserList", "relate_user_list")
      .firstNotNullOfOrNull { key -> obj[key] as? JsonArray }
  }

  fun parseMixListResponse(text: String): List<Streamer> {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    if ((root["code"].jsonLongOrNull() ?: -1L) != 0L) return emptyList()
    return root.objectAt(listOf("data"))?.get("rl").jsonObjects().mapNotNull { room ->
      val roomId = room["rid"].jsonLongOrNull()?.toString()?.takeIf { it.isNotBlank() }
        ?: return@mapNotNull null
      val type = room["type"].jsonLongOrNull()
      val showStatus = room["show_status"].jsonLongOrNull()
      val videoLoop = room["videoLoop"].jsonLongOrNull() ?: room["video_loop"].jsonLongOrNull()
      val isLoop = room["isLoop"].jsonLongOrNull() ?: room["is_loop"].jsonLongOrNull()
      val typeStatus = when (type) {
        1L -> 1L
        2L -> 2L
        else -> null
      }
      Streamer(
        platform = Platform.Douyu,
        roomId = roomId,
        name = room["nn"].jsonStringOrNull().orEmpty(),
        title = room["rn"].jsonStringOrNull().orEmpty(),
        viewerText = formatViewerCountWanIfNeeded(room["ol"].jsonLongOrNull()?.toString().orEmpty()),
        avatarUrl = normalizeHttpUrl(room["av"].jsonStringOrNull()),
        coverUrl = normalizeHttpUrl(room["rs16"].jsonStringOrNull()),
        isLive = resolveDouyuLiveStatus(showStatus ?: typeStatus, videoLoop, isLoop) ?: true,
      )
    }
  }
}
