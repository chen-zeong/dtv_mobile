package dtv.mobile.platform.bilibili

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.util.formatViewerCountWanIfNeeded
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal object BilibiliLiveListParser {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun isRiskControlResponse(text: String): Boolean {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return true
    val code = root["code"].longValueOrNull() ?: -1L
    return code == -352L || code == -412L
  }

  private fun JsonElement?.stringValueOrNull(): String? {
    val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content
  }

  private fun JsonElement?.longValueOrNull(): Long? {
    val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return primitive.longOrNull ?: primitive.content.toLongOrNull()
  }

  fun parseAreaResponse(text: String): List<JsonObject> {
    val root = json.parseToJsonElement(text).jsonObject
    val code = root["code"].longValueOrNull() ?: -1L
    if (code != 0L) error(root["message"].stringValueOrNull() ?: "B站列表接口错误: code=$code")

    return (root["data"] as? kotlinx.serialization.json.JsonArray)
      .orEmpty()
      .mapNotNull { it as? JsonObject }
  }

  fun mapRoom(room: JsonObject): Streamer? {
    val roomId = (
      room["roomid"].longValueOrNull()
        ?: room["room_id"].longValueOrNull()
        ?: room["roomId"].longValueOrNull()
      )
      ?.toString()
      .orEmpty()
    if (roomId.isBlank()) return null

    val name = room["uname"].stringValueOrNull()?.trim().orEmpty()
    val title = room["title"].stringValueOrNull()?.trim().orEmpty()
    val cover = normalizeHttpUrl(
      room["user_cover"].stringValueOrNull()
        ?: room["cover"].stringValueOrNull()
        ?: room["keyframe"].stringValueOrNull(),
    )
    val avatar = normalizeHttpUrl(room["face"].stringValueOrNull())

    val liveStatus = room["live_status"].longValueOrNull() ?: room["liveStatus"].longValueOrNull()
    val isLive = liveStatus?.let { it == 1L } ?: true
    if (!isLive) return null

    val watchedNum = room["watched_show"]?.jsonObject?.get("num")?.stringValueOrNull()?.trim()
      ?: room["watched_show"]?.jsonObject?.get("num")?.longValueOrNull()?.toString()
    val online = room["online"].longValueOrNull() ?: 0L
    val viewerText = when {
      !watchedNum.isNullOrBlank() -> watchedNum
      online > 0 -> formatViewerCountWanIfNeeded(online.toString())
      else -> ""
    }

    return Streamer(
      platform = Platform.Bilibili,
      roomId = roomId,
      name = name.ifBlank { "B站主播" },
      title = title.ifBlank { "直播中" },
      viewerText = viewerText,
      avatarUrl = avatar,
      coverUrl = cover,
      isLive = isLive,
    )
  }
}
