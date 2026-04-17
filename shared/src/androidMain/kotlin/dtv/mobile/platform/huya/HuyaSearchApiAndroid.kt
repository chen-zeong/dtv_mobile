package dtv.mobile.platform.huya

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.util.normalizeHttpUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class HuyaSearchApiAndroid(
  private val client: HttpClient,
) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private fun JsonElement?.stringValueOrNull(): String? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
  }

  suspend fun searchAnchors(keyword: String, page: Int = 1): List<Streamer> {
    val trimmed = keyword.trim()
    if (trimmed.isEmpty()) return emptyList()

    val start = ((page - 1).coerceAtLeast(0) * 20).toString()
    val text = client.get("https://search.cdn.huya.com/") {
      parameter("m", "Search")
      parameter("do", "getSearchContent")
      parameter("q", trimmed)
      parameter("uid", "0")
      parameter("v", "1")
      parameter("typ", "-5")
      parameter("livestate", "0")
      parameter("rows", "20")
      parameter("start", start)
      headers {
        append("User-Agent", "Mozilla/5.0")
        append("Referer", "https://www.huya.com/search/")
        append("Origin", "https://www.huya.com")
        append("Accept", "*/*")
      }
    }.bodyAsText()

    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val docs = root["response"]?.jsonObject?.get("1")?.jsonObject?.get("docs")?.jsonArray ?: return emptyList()

    return docs.mapNotNull { el ->
      val obj = el.jsonObject
      val roomId = (obj["room_id"]?.jsonPrimitive?.longOrNull ?: obj["room_id"].stringValueOrNull()?.toLongOrNull())?.toString().orEmpty()
      if (roomId.isBlank() || roomId == "0") return@mapNotNull null
      val avatar = normalizeHttpUrl(obj["game_avatarUrl180"].stringValueOrNull())
      val name = obj["game_nick"].stringValueOrNull().orEmpty()
      val title = obj["live_intro"].stringValueOrNull().orEmpty()
      val live = obj["gameLiveOn"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        ?: obj["gameLiveOn"]?.jsonPrimitive?.booleanOrNull
        ?: false

      Streamer(
        platform = Platform.Huya,
        roomId = roomId,
        name = name.ifBlank { "虎牙主播" },
        title = title.ifBlank { "暂无标题" },
        viewerText = "",
        avatarUrl = avatar,
        coverUrl = null,
        isLive = live,
      )
    }
  }
}
