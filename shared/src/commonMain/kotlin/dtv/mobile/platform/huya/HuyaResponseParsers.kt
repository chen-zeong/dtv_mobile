package dtv.mobile.platform.huya

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.platform.common.jsonBooleanOrNull
import dtv.mobile.platform.common.jsonLongOrNull
import dtv.mobile.platform.common.jsonObjects
import dtv.mobile.platform.common.jsonStringOrNull
import dtv.mobile.platform.common.objectAt
import dtv.mobile.platform.common.stringAt
import dtv.mobile.util.formatViewerCountWanIfNeeded
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal object HuyaResponseParsers {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parseLiveListResponse(text: String): List<Streamer> {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val rooms = root["vList"] ?: root.objectAt(listOf("data"))?.get("vList")
    return rooms.jsonObjects().mapNotNull { room ->
      val roomId = room["lProfileRoom"].jsonLongOrNull()?.toString()?.takeIf { it.isNotBlank() }
        ?: return@mapNotNull null
      Streamer(
        platform = Platform.Huya,
        roomId = roomId,
        name = room["sNick"].jsonStringOrNull().orEmpty().ifBlank { "虎牙主播" },
        title = room["sIntroduction"].jsonStringOrNull().orEmpty().ifBlank { "直播中" },
        viewerText = formatViewerCountWanIfNeeded(room["lUserCount"].jsonLongOrNull()?.toString().orEmpty()),
        avatarUrl = normalizeHttpUrl(room["sAvatar180"].jsonStringOrNull()),
        coverUrl = normalizeHttpUrl(room["sScreenshot"].jsonStringOrNull()),
        isLive = true,
      )
    }
  }

  fun parseSearchResponse(text: String): List<Streamer> {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val docs = root.objectAt(listOf("response", "1"))?.get("docs")
    return docs.jsonObjects().mapNotNull { room ->
      val roomId = room["room_id"].jsonLongOrNull()?.toString()
        ?.takeIf { it.isNotBlank() && it != "0" }
        ?: return@mapNotNull null
      Streamer(
        platform = Platform.Huya,
        roomId = roomId,
        name = room["game_nick"].jsonStringOrNull().orEmpty().ifBlank { "虎牙主播" },
        title = room["live_intro"].jsonStringOrNull().orEmpty().ifBlank { "暂无标题" },
        viewerText = "",
        avatarUrl = normalizeHttpUrl(room["game_avatarUrl180"].jsonStringOrNull()),
        coverUrl = null,
        isLive = room["gameLiveOn"].jsonBooleanOrNull() ?: false,
      )
    }
  }
}
