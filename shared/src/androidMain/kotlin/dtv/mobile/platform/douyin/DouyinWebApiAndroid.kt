package dtv.mobile.platform.douyin

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

class DouyinWebApiAndroid(
  private val client: HttpClient,
) {
  companion object {
    // Use the tested cookie from the desktop project to improve API success.
    internal const val DEFAULT_COOKIE =
      "ttwid=1%7C2iDIYVmjzMcpZ20fcaFde0VghXAA3NaNXE_SLR68IyE%7C1761045455%7Cab35197d5cfb21df6cbb2fa7ef1c9262206b062c315b9d04da746d0b37dfbc7d"

    // Align UA with the working sample.
    const val DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 Core/1.116.567.400 QQBrowser/19.7.6764.400"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  }

  data class PartitionResult(
    val rooms: List<DouyinRoom>,
    val hasMore: Boolean,
  )

  data class DouyinRoom(
    val webRid: String,
    val title: String,
    val ownerNickname: String,
    val coverUrl: String?,
    val avatarUrl: String?,
    val userCountStr: String,
  )

  data class EnterRoomResult(
    val title: String?,
    val anchorName: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val viewerCountStr: String?,
    val roomId: String?,
    val msToken: String?,
    val hlsPullUrlMap: Map<String, String>,
    val flvPullUrlMap: Map<String, String>,
  )

  private fun buildQuery(params: List<Pair<String, String>>): String {
    return params.joinToString("&") { (k, v) ->
      "${k.encodeURLQueryComponent()}=${v.encodeURLQueryComponent()}"
    }
  }

  private fun JsonElement?.stringValueOrNull(): String? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
  }

  private fun JsonObject.string(path: List<String>): String? {
    var cur: JsonElement = this
    for (p in path) {
      cur = (cur as? JsonObject)?.get(p) ?: return null
    }
    return cur.stringValueOrNull()
  }

  private fun JsonObject.obj(path: List<String>): JsonObject? {
    var cur: JsonElement = this
    for (p in path) {
      cur = (cur as? JsonObject)?.get(p) ?: return null
    }
    return cur as? JsonObject
  }

  private fun JsonObject.arr(path: List<String>): JsonArray? {
    var cur: JsonElement = this
    for (p in path) {
      cur = (cur as? JsonObject)?.get(p) ?: return null
    }
    return cur as? JsonArray
  }

  suspend fun fetchPartitionRooms(
    partition: String,
    partitionType: String,
    offset: Int,
    limit: Int,
    msToken: String,
  ): PartitionResult {
    val params = listOf(
      "aid" to "6383",
      "app_name" to "douyin_web",
      "live_id" to "1",
      "device_platform" to "web",
      "language" to "zh-CN",
      "enter_from" to "web_homepage_hot",
      "cookie_enabled" to "true",
      "screen_width" to "1920",
      "screen_height" to "1080",
      "browser_language" to "zh-CN",
      "browser_platform" to "Win32",
      "browser_name" to "Chrome",
      "browser_version" to "116.0.0.0",
      "count" to limit.toString(),
      "offset" to offset.toString(),
      "partition" to partition,
      "partition_type" to partitionType,
      "req_from" to "2",
      "msToken" to msToken,
    )
    val query = buildQuery(params)
    val sign = withContext(Dispatchers.Default) { generateABogus(query, DEFAULT_USER_AGENT) }
    val url = "https://live.douyin.com/webcast/web/partition/detail/room/v2/?$query&a_bogus=${sign.encodeURLQueryComponent()}"

    val text = client.get(url) {
      headers {
        append("User-Agent", DEFAULT_USER_AGENT)
        append("Cookie", DEFAULT_COOKIE)
      }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val status = root["status_code"]?.jsonPrimitive?.intOrNull ?: -1
    if (status != 0) {
      val msg = root.obj(listOf("data"))?.string(listOf("message"))
      throw IllegalStateException(msg ?: "抖音分区列表接口错误: status_code=$status")
    }

    val wrapper = root["data"]?.jsonObject ?: return PartitionResult(emptyList(), hasMore = false)
    val dataArr = wrapper["data"]?.jsonArray ?: return PartitionResult(emptyList(), hasMore = false)
    val rooms = dataArr.mapNotNull { entry ->
      val obj = entry as? JsonObject ?: return@mapNotNull null
      val webRid = obj["web_rid"].stringValueOrNull()?.trim().orEmpty()
      val room = (obj["room"] as? JsonObject) ?: return@mapNotNull null
      val title = room["title"].stringValueOrNull()?.trim().orEmpty()
      val owner = room.obj(listOf("owner"))
      val ownerNickname = owner?.string(listOf("nickname"))?.trim().orEmpty()
      val coverUrl = room.arr(listOf("cover", "url_list"))?.firstOrNull().stringValueOrNull()
      val avatarUrl = owner?.arr(listOf("avatar_thumb", "url_list"))?.firstOrNull().stringValueOrNull()
      val stats = room.obj(listOf("stats"))
      val userCountStr = (stats?.string(listOf("user_count_str")) ?: stats?.string(listOf("total_user_str"))).orEmpty()

      if (webRid.isBlank()) return@mapNotNull null
      DouyinRoom(
        webRid = webRid,
        title = title.ifBlank { "直播中" },
        ownerNickname = ownerNickname.ifBlank { "抖音主播" },
        coverUrl = coverUrl,
        avatarUrl = avatarUrl,
        userCountStr = userCountStr.ifBlank { "直播中" },
      )
    }

    val apiHasMore = wrapper["has_more"]?.jsonPrimitive?.booleanOrNull
    val hasMore = apiHasMore ?: (rooms.size == limit)
    return PartitionResult(rooms = rooms, hasMore = hasMore)
  }

  suspend fun fetchRoomEnter(webRid: String): EnterRoomResult {
    val params = listOf(
      "aid" to "6383",
      "app_name" to "douyin_web",
      "live_id" to "1",
      "device_platform" to "web",
      "language" to "zh-CN",
      "browser_language" to "zh-CN",
      "browser_platform" to "Win32",
      "browser_name" to "Chrome",
      "browser_version" to "116.0.0.0",
      "web_rid" to webRid,
      "msToken" to "",
    )
    val query = buildQuery(params)
    val sign = withContext(Dispatchers.Default) { generateABogus(query, DEFAULT_USER_AGENT) }
    val url = "https://live.douyin.com/webcast/room/web/enter/?$query&a_bogus=${sign.encodeURLQueryComponent()}"

    val response = client.get(url) {
      headers {
        append("User-Agent", DEFAULT_USER_AGENT)
        append("Referer", "https://live.douyin.com/$webRid")
        append("Accept-Encoding", "identity")
        append("Cookie", DEFAULT_COOKIE)
      }
    }
    val text = response.bodyAsText()
    val msToken = response.headers["x-ms-token"]?.trim()?.takeIf { it.isNotBlank() }

    val root = json.parseToJsonElement(text).jsonObject
    val room = root.arr(listOf("data", "data"))?.firstOrNull()?.jsonObject
    val user = root.obj(listOf("data", "user"))
    val anchorName = user?.string(listOf("nickname"))
    val avatarUrl = user?.obj(listOf("avatar_thumb"))?.arr(listOf("url_list"))?.firstOrNull().stringValueOrNull()
    val title = room?.string(listOf("title"))
    val coverUrl = room?.obj(listOf("cover"))?.arr(listOf("url_list"))?.firstOrNull().stringValueOrNull()
    val stats = room?.obj(listOf("stats"))
    val viewerCountStr = (stats?.string(listOf("user_count_str")) ?: stats?.string(listOf("total_user_str")))
    val roomId = room?.get("id_str").stringValueOrNull()
      ?: room?.get("id").stringValueOrNull()

    val streamUrl = room?.obj(listOf("stream_url"))
    val hlsMap = streamUrl?.obj(listOf("hls_pull_url_map"))?.mapValues { it.value.stringValueOrNull().orEmpty() }.orEmpty()
    val flvMap = streamUrl?.obj(listOf("flv_pull_url"))?.mapValues { it.value.stringValueOrNull().orEmpty() }.orEmpty()

    return EnterRoomResult(
      title = title,
      anchorName = anchorName,
      avatarUrl = avatarUrl,
      coverUrl = coverUrl,
      viewerCountStr = viewerCountStr,
      roomId = roomId,
      msToken = msToken,
      hlsPullUrlMap = hlsMap.filterValues { it.isNotBlank() },
      flvPullUrlMap = flvMap.filterValues { it.isNotBlank() },
    )
  }
}
