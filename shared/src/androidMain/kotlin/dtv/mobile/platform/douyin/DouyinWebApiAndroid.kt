package dtv.mobile.platform.douyin

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DouyinWebApiAndroid(
  private val client: HttpClient,
  appContext: Context,
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

  private val cookieManager = DouyinCookieManager(
    persistence = object : DouyinCookiePersistence {
      private val prefs = appContext.getSharedPreferences("dtv_douyin", Context.MODE_PRIVATE)

      override fun read(): String? = prefs.getString("cookie", null)

      override fun readFetchedAt(): Long = prefs.getLong("fetched_at", 0L)

      override fun write(cookie: String, fetchedAtMs: Long) {
        prefs.edit()
          .putString("cookie", cookie)
          .putLong("fetched_at", fetchedAtMs)
          .apply()
      }
    },
    fallbackCookie = DEFAULT_COOKIE,
    fetcher = {
      DouyinWebViewCookieAndroid(appContext).fetch(
        url = "https://live.douyin.com/",
        userAgent = DEFAULT_USER_AGENT,
      )
    },
  )

  suspend fun cookieHeader(forceRefresh: Boolean = false): String =
    cookieManager.getCookie(forceRefresh)

  fun updateCookieFromSetCookie(setCookie: String?) {
    cookieManager.updateFromSetCookie(setCookie)
  }

  typealias PartitionResult = DouyinPartitionResult
  typealias DouyinRoom = DouyinPartitionRoom

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

    val firstCookie = cookieHeader()
    val first = fetchPartitionResponse(url, firstCookie)
    updateCookieFromSetCookie(first.setCookie)
    if (!DouyinResponseParser.isRetryableFailure(first.status, first.text)) {
      return DouyinResponseParser.parsePartitionResponse(first.text, limit)
    }

    val retryCookie = cookieHeader(forceRefresh = true)
    val second = fetchPartitionResponse(url, retryCookie)
    updateCookieFromSetCookie(second.setCookie)
    return DouyinResponseParser.parsePartitionResponse(second.text, limit)
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

    suspend fun request(cookie: String): HttpText {
      val response = client.get(url) {
        headers {
          append("User-Agent", DEFAULT_USER_AGENT)
          append("Referer", "https://live.douyin.com/$webRid")
          append("Accept-Encoding", "identity")
          append("Cookie", cookie)
        }
      }
      return HttpText(
        status = response.status.value,
        text = response.bodyAsText(),
        msToken = response.headers["x-ms-token"]?.trim()?.takeIf { it.isNotBlank() },
        setCookie = response.headers["Set-Cookie"],
      )
    }

    val first = request(cookieHeader())
    var response = first
    updateCookieFromSetCookie(first.setCookie)
    var parsed = runCatching { DouyinResponseParser.parseRoomEnterResponse(response.text) }.getOrNull()
    if (DouyinResponseParser.isRetryableFailure(response.status, response.text, parsed)) {
      response = request(cookieHeader(forceRefresh = true))
      updateCookieFromSetCookie(response.setCookie)
      parsed = DouyinResponseParser.parseRoomEnterResponse(response.text)
    }
    val parsedRoom = parsed ?: DouyinResponseParser.parseRoomEnterResponse(response.text)

    return EnterRoomResult(
      title = parsedRoom.title,
      anchorName = parsedRoom.anchorName,
      avatarUrl = parsedRoom.avatarUrl,
      coverUrl = parsedRoom.coverUrl,
      viewerCountStr = parsedRoom.viewerCountStr,
      roomId = parsedRoom.roomId,
      msToken = response.msToken,
      hlsPullUrlMap = parsedRoom.streamUrls.hlsPullUrlMap,
      flvPullUrlMap = parsedRoom.streamUrls.flvPullUrlMap,
    )
  }

  private suspend fun fetchPartitionResponse(url: String, cookie: String): HttpText {
    val response = client.get(url) {
      headers {
        append("User-Agent", DEFAULT_USER_AGENT)
        append("Cookie", cookie)
      }
    }
    return HttpText(
      status = response.status.value,
      text = response.bodyAsText(),
      msToken = response.headers["x-ms-token"]?.trim()?.takeIf { it.isNotBlank() },
      setCookie = response.headers["Set-Cookie"],
    )
  }

  private data class HttpText(
    val status: Int,
    val text: String,
    val msToken: String? = null,
    val setCookie: String? = null,
  )
}
