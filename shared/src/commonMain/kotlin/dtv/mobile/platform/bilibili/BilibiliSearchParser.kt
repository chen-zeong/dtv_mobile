package dtv.mobile.platform.bilibili

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.platform.common.jsonLongOrNull
import dtv.mobile.platform.common.jsonObjects
import dtv.mobile.platform.common.jsonStringOrNull
import dtv.mobile.platform.common.objectAt
import dtv.mobile.util.normalizeHttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal object BilibiliSearchParser {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val emTag = Regex("(?i)</?em[^>]*>")

  fun parseResponse(text: String): List<Streamer> {
    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    if ((root["code"].jsonLongOrNull() ?: -1L) != 0L) return emptyList()
    val users = root.objectAt(listOf("data", "result"))?.get("live_user")
    return users.jsonObjects().mapNotNull { obj ->
      val roomId = obj["roomid"].jsonLongOrNull()?.toString()
        ?: obj["roomid"].jsonStringOrNull()?.trim()?.toLongOrNull()?.toString()
        ?: return@mapNotNull null
      Streamer(
        platform = Platform.Bilibili,
        roomId = roomId,
        name = stripEmTags(obj["uname"].jsonStringOrNull().orEmpty()).trim().ifBlank { "B站主播" },
        title = stripEmTags(obj["title"].jsonStringOrNull().orEmpty()).trim().ifBlank { "暂无标题" },
        viewerText = obj["online"].jsonStringOrNull().orEmpty(),
        avatarUrl = normalizeSearchImage(obj["uface"].jsonStringOrNull()),
        coverUrl = normalizeSearchImage(obj["cover"].jsonStringOrNull()),
        isLive = obj["live_status"].jsonLongOrNull() == 1L,
      )
    }
  }

  private fun stripEmTags(value: String): String = value.replace(emTag, "")

  private fun normalizeSearchImage(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    val full = if (value.startsWith("http")) value else "https:$value"
    return normalizeHttpUrl(if (full.contains('@')) full else "$full@400w.jpg")
  }
}
