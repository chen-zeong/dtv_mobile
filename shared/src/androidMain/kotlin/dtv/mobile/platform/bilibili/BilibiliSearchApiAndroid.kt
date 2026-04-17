package dtv.mobile.platform.bilibili

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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class BilibiliSearchApiAndroid(
  private val client: HttpClient,
  private val cookieStore: BilibiliCookieStoreAndroid,
) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val emTagPattern = Regex("(?i)</?em[^>]*>")

  private fun JsonElement?.stringValueOrNull(): String? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
  }

  private fun JsonElement?.longValueOrNull(): Long? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return p.longOrNull ?: p.content.toLongOrNull()
  }

  private fun stripEmTags(input: String): String {
    return input.replace(emTagPattern, "")
  }

  private fun normalizeImage(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val full = if (trimmed.startsWith("http")) trimmed else "https:$trimmed"
    return if (full.contains('@')) full else "$full@400w.jpg"
  }

  private fun parseCookiePairs(cookie: String): MutableList<Pair<String, String>> {
    if (cookie.isBlank()) return mutableListOf()
    return cookie.split(';')
      .mapNotNull { it.trim() }
      .mapNotNull { seg ->
        val idx = seg.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        val k = seg.substring(0, idx).trim()
        val v = seg.substring(idx + 1).trim()
        if (k.isBlank()) return@mapNotNull null
        k to v
      }
      .toMutableList()
  }

  private fun findCookie(pairs: List<Pair<String, String>>, key: String): String? {
    return pairs.firstOrNull { (k, _) -> k.equals(key, ignoreCase = true) }?.second
  }

  private fun upsertCookie(pairs: MutableList<Pair<String, String>>, key: String, value: String) {
    val idx = pairs.indexOfFirst { (k, _) -> k.equals(key, ignoreCase = true) }
    if (idx >= 0) pairs[idx] = key to value else pairs.add(key to value)
  }

  private fun buildCookieHeader(pairs: List<Pair<String, String>>): String {
    return pairs.joinToString("; ") { (k, v) -> "$k=$v" }
  }

  private suspend fun ensureBuvid(cookieHeader: String): String {
    val pairs = parseCookiePairs(cookieHeader)
    val has3 = findCookie(pairs, "buvid3") != null
    val has4 = findCookie(pairs, "buvid4") != null
    if (has3 && has4) return cookieHeader

    val text = client.get("https://api.bilibili.com/x/frontend/finger/spi") {
      headers {
        append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
        append("Referer", "https://live.bilibili.com/")
        if (cookieHeader.isNotBlank()) append("Cookie", cookieHeader)
      }
    }.bodyAsText()

    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return cookieHeader
    val data = root["data"]?.jsonObject
    val b3 = data?.get("b_3").stringValueOrNull()
    val b4 = data?.get("b_4").stringValueOrNull()
    if (!b3.isNullOrBlank()) upsertCookie(pairs, "buvid3", b3)
    if (!b4.isNullOrBlank()) upsertCookie(pairs, "buvid4", b4)
    return buildCookieHeader(pairs)
  }

  suspend fun searchRooms(keyword: String, page: Int = 1): List<Streamer> {
    val trimmed = keyword.trim()
    if (trimmed.isEmpty()) return emptyList()

    val initialCookie = cookieStore.getCookie().orEmpty()
    val cookieHeader = runCatching { ensureBuvid(initialCookie) }.getOrElse { initialCookie }
    if (cookieHeader.isNotBlank()) cookieStore.mergeFromCookieHeader(cookieHeader)

    val text = client.get("https://api.bilibili.com/x/web-interface/search/type") {
      parameter("context", "")
      parameter("search_type", "live")
      parameter("cover_type", "user_cover")
      parameter("order", "")
      parameter("keyword", trimmed)
      parameter("category_id", "")
      parameter("__refresh__", "")
      parameter("_extra", "")
      parameter("highlight", "0")
      parameter("single_column", "0")
      parameter("page", page.toString())
      headers {
        append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
        append("Referer", "https://live.bilibili.com/")
        if (cookieHeader.isNotBlank()) append("Cookie", cookieHeader)
      }
    }.bodyAsText()

    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val code = root["code"].longValueOrNull() ?: -1L
    if (code != 0L) return emptyList()

    val liveUsers = root["data"]?.jsonObject
      ?.get("result")?.jsonObject
      ?.get("live_user")?.jsonArray
      ?: return emptyList()

    return liveUsers.mapNotNull { el ->
      val obj = el.jsonObject
      val roomId = obj["roomid"].longValueOrNull()?.toString().orEmpty()
      if (roomId.isBlank()) return@mapNotNull null

      val title = stripEmTags(obj["title"].stringValueOrNull().orEmpty()).trim().ifBlank { "暂无标题" }
      val name = stripEmTags(obj["uname"].stringValueOrNull().orEmpty()).trim().ifBlank { "B站主播" }
      val cover = normalizeHttpUrl(normalizeImage(obj["cover"].stringValueOrNull()))
      val avatar = normalizeHttpUrl(normalizeImage(obj["uface"].stringValueOrNull()))

      val watching = obj["online"]?.let { online ->
        when (online) {
          is kotlinx.serialization.json.JsonPrimitive -> online.content
          else -> ""
        }
      }.orEmpty()

      val isLive = (obj["live_status"].longValueOrNull() ?: 0L) == 1L

      Streamer(
        platform = Platform.Bilibili,
        roomId = roomId,
        name = name,
        title = title,
        viewerText = watching,
        avatarUrl = avatar,
        coverUrl = cover,
        isLive = isLive,
      )
    }
  }
}

