package dtv.mobile.platform.bilibili

import dtv.mobile.model.Streamer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import dtv.mobile.util.runSuspendCatching
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class BilibiliSearchApiAndroid(
  private val client: HttpClient,
  private val cookieStore: BilibiliCookieStoreAndroid,
) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private fun JsonElement?.stringValueOrNull(): String? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
  }

  private fun JsonElement?.longValueOrNull(): Long? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return p.longOrNull ?: p.content.toLongOrNull()
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
    val cookieHeader = runSuspendCatching { ensureBuvid(initialCookie) }.getOrElse { initialCookie }
    if (cookieHeader.isNotBlank()) cookieStore.mergeFromCookieHeader(cookieHeader)

    suspend fun request(): String = client.get("https://api.bilibili.com/x/web-interface/search/type") {
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

    val first = request()
    val text = if (BilibiliLiveListParser.isRiskControlResponse(first)) request() else first
    return BilibiliSearchParser.parseResponse(text)
  }
}

