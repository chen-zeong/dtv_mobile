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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest

class BilibiliLiveListApiAndroid(
  private val client: HttpClient,
) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private var cachedWWebid: String? = null
  private var cachedWWebidAtMs: Long = 0L

  private val accessIdRegex = Regex("\"access_id\"\\s*:\\s*\"([^\"]+)\"")

  private fun md5Hex(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return bytes.joinToString("") { b -> "%02x".format(b) }
  }

  private fun JsonElement?.stringValueOrNull(): String? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
  }

  private fun JsonElement?.longValueOrNull(): Long? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return p.longOrNull ?: p.content.toLongOrNull()
  }

  private suspend fun ensureWWebid(): String {
    val now = System.currentTimeMillis()
    val cached = cachedWWebid
    if (!cached.isNullOrBlank() && (now - cachedWWebidAtMs) < 30 * 60 * 1000L) {
      return cached
    }

    val html = client.get("https://live.bilibili.com/lol") {
      headers {
        append(
          "User-Agent",
          "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        )
        append("Referer", "https://www.bilibili.com/")
      }
    }.bodyAsText()

    val match = accessIdRegex.find(html) ?: error("B站 access_id 获取失败")
    val wWebid = match.groupValues.getOrNull(1)?.trim().orEmpty()
    if (wWebid.isBlank()) error("B站 access_id 为空")
    cachedWWebid = wWebid
    cachedWWebidAtMs = now
    return wWebid
  }

  private suspend fun fetchSecondList(parentAreaId: Int, areaId: Int, page: Int): List<JsonObject> {
    val wWebid = ensureWWebid()
    val wts = (System.currentTimeMillis() / 1000L).toString()
    val secret = "ea1db124af3c7062474693fa704f4ff8"

    val pairs = listOf(
      "area_id" to areaId.toString(),
      "page" to page.toString(),
      "parent_area_id" to parentAreaId.toString(),
      "platform" to "web",
      "sort_type" to "",
      "vajra_business_key" to "",
      "w_webid" to wWebid,
      "web_location" to "444.253",
      "wts" to wts,
    )

    val signString = pairs.joinToString("&") { (k, v) -> "$k=$v" } + secret
    val wRid = md5Hex(signString)

    val text = client.get("https://api.live.bilibili.com/xlive/web-interface/v1/second/getList") {
      headers {
        append(
          "User-Agent",
          "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        )
        append("Referer", "https://www.bilibili.com/")
        append("Cookie", "buvid3=i;")
      }
      pairs.forEach { (k, v) -> parameter(k, v) }
      parameter("w_rid", wRid)
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val code = root["code"].longValueOrNull() ?: -1L
    if (code != 0L) error(root["message"].stringValueOrNull() ?: "B站列表接口错误: code=$code")

    val list = root["data"]?.jsonObject?.get("list")?.jsonArray ?: return emptyList()
    return list.mapNotNull { it as? JsonObject }
  }

  private suspend fun fetchHotIndexList(page: Int, pageSize: Int): List<JsonObject> {
    val url =
      "https://api.live.bilibili.com/xlive/web-interface/v1/index/getList?platform=web&parent_area_id=0&area_id=0&page=$page&page_size=$pageSize"
    val text = client.get(url) {
      headers {
        append("User-Agent", "Mozilla/5.0")
        append("Referer", "https://live.bilibili.com/")
      }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val code = root["code"]?.jsonPrimitive?.longOrNull ?: -1
    if (code != 0L) return emptyList()
    val data = root["data"]?.jsonObject ?: return emptyList()

    fun extractFromModules(arr: JsonArray?): List<JsonObject> {
      if (arr == null) return emptyList()
      return arr.flatMap { mod ->
        val obj = mod.jsonObject
        val list = obj["list"]?.jsonArray ?: return@flatMap emptyList()
        list.mapNotNull { it as? JsonObject }
      }
    }

    return buildList {
      addAll(extractFromModules(data["room_list"] as? JsonArray))
    }
  }

  suspend fun fetchLiveList(
    parentAreaId: Int,
    areaId: Int,
    page: Int,
    pageSize: Int,
  ): List<Streamer> {
    val rooms: List<JsonObject> = runCatching {
      // Align with desktop: use /second/getList for real (parent, area) filtering.
      // Hot tab (0,0) is not supported by /second/getList; fallback to /index/getList.
      if (parentAreaId == 0 && areaId == 0) {
        fetchHotIndexList(page = page, pageSize = pageSize)
      } else {
        fetchSecondList(parentAreaId = parentAreaId, areaId = areaId, page = page)
      }
    }.getOrElse { emptyList() }

    return rooms.mapNotNull { r ->
      val roomId =
        (r["roomid"].longValueOrNull() ?: r["room_id"].longValueOrNull() ?: r["roomId"].longValueOrNull())
          ?.toString()
          .orEmpty()
      if (roomId.isBlank()) return@mapNotNull null
      val name = r["uname"].stringValueOrNull()?.trim().orEmpty()
      val title = r["title"].stringValueOrNull()?.trim().orEmpty()
      val cover = normalizeHttpUrl(
        r["user_cover"].stringValueOrNull()
          ?: r["cover"].stringValueOrNull()
          ?: r["keyframe"].stringValueOrNull(),
      )
      val avatar = normalizeHttpUrl(r["face"].stringValueOrNull())

      val liveStatus = r["live_status"].longValueOrNull() ?: r["liveStatus"].longValueOrNull()
      val isLive = liveStatus?.let { it == 1L } ?: true
      if (!isLive) return@mapNotNull null

      val watchedNum = r["watched_show"]?.jsonObject?.get("num")?.stringValueOrNull()?.trim()
        ?: r["watched_show"]?.jsonObject?.get("num")?.longValueOrNull()?.toString()
      val online = r["online"].longValueOrNull() ?: 0L
      val viewerStr = when {
        !watchedNum.isNullOrBlank() -> watchedNum
        online >= 10_000 -> String.format("%.1f万", online / 10_000.0)
        online > 0 -> online.toString()
        else -> ""
      }

      Streamer(
        platform = Platform.Bilibili,
        roomId = roomId,
        name = name.ifBlank { "B站主播" },
        title = title.ifBlank { "直播中" },
        viewerText = viewerStr,
        avatarUrl = avatar,
        coverUrl = cover,
        isLive = isLive,
      )
    }
  }
}
