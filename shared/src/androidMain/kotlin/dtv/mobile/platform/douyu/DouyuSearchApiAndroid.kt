package dtv.mobile.platform.douyu

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.util.normalizeHttpUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class DouyuSearchApiAndroid(
  private val client: HttpClient,
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

  private fun md5Hex(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return bytes.joinToString("") { b -> "%02x".format(b) }
  }

  private fun findList(data: JsonElement?): JsonArray? {
    if (data is JsonArray) return data
    val obj = data as? kotlinx.serialization.json.JsonObject ?: return null
    val candidates = listOf(
      "relateUser",
      "relate_user",
      "relate",
      "relateUserList",
      "relate_user_list",
    )
    candidates.forEach { key ->
      val el = obj[key]
      if (el is JsonArray) return el
    }
    return null
  }

  suspend fun searchAnchors(keyword: String): List<Streamer> {
    val trimmed = keyword.trim()
    if (trimmed.isEmpty()) return emptyList()

    val didSeed = "${System.nanoTime()}-${Random.nextInt()}"
    val did = md5Hex(didSeed)

    val text = client.get("https://www.douyu.com/japi/search/api/searchUser") {
      parameter("kw", trimmed)
      parameter("page", "1")
      parameter("pageSize", "20")
      parameter("filterType", "0")
      headers {
        append("Referer", "https://www.douyu.com/search/")
        append("Cookie", "dy_did=$did; acf_did=$did")
      }
    }.bodyAsText()

    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
    val list = findList(root["data"]) ?: return emptyList()

    fun lastPathSegment(url: String): String {
      val t = url.trim().trimEnd('/')
      return t.substringAfterLast('/', missingDelimiterValue = t)
    }

    return list.mapNotNull { el ->
      val itemObj = el.jsonObject

      val type = itemObj["type"].longValueOrNull()
      if (type != null && type != 1L) return@mapNotNull null

      val anchorObj = (itemObj["anchorInfo"] as? kotlinx.serialization.json.JsonObject) ?: itemObj

      val roomId =
        anchorObj["rid"].stringValueOrNull()
          ?: anchorObj["room_id"].stringValueOrNull()
          ?: anchorObj["roomId"].stringValueOrNull()
          ?: anchorObj["rid"].longValueOrNull()?.toString()
          ?: anchorObj["room_id"].longValueOrNull()?.toString()
          ?: anchorObj["roomId"].longValueOrNull()?.toString()
          ?: anchorObj["bkUrl"].stringValueOrNull()?.let(::lastPathSegment)
          ?: ""
      if (roomId.isBlank()) return@mapNotNull null

      val name =
        anchorObj["nickName"].stringValueOrNull()
          ?: anchorObj["nickname"].stringValueOrNull()
          ?: anchorObj["user_name"].stringValueOrNull()
          ?: anchorObj["userName"].stringValueOrNull()
          ?: ""
      if (name.isBlank()) return@mapNotNull null

      val title =
        anchorObj["roomName"].stringValueOrNull()
          ?: anchorObj["room_name"].stringValueOrNull()
          ?: anchorObj["description"].stringValueOrNull()
          ?: anchorObj["title"].stringValueOrNull()
          ?: "暂无标题"

      val avatar =
        anchorObj["avatar"].stringValueOrNull()
          ?: anchorObj["avatar_url"].stringValueOrNull()
          ?: anchorObj["avatarUrl"].stringValueOrNull()

      val isLive = anchorObj["isLive"].longValueOrNull() ?: anchorObj["is_live"].longValueOrNull()
      val isLoop = anchorObj["isLoop"].longValueOrNull() ?: anchorObj["is_loop"].longValueOrNull()
      val videoLoop = anchorObj["videoLoop"].longValueOrNull() ?: anchorObj["video_loop"].longValueOrNull()
      val liveStatus = when (isLive) {
        1L -> true
        2L -> false
        else -> (isLoop != 1L) && (videoLoop != 1L)
      }

      Streamer(
        platform = Platform.Douyu,
        roomId = roomId,
        name = name,
        title = title.ifBlank { "暂无标题" },
        viewerText = "",
        avatarUrl = normalizeHttpUrl(avatar),
        coverUrl = null,
        isLive = liveStatus,
      )
    }
  }
}
