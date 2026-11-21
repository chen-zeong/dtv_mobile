package dtv.mobile.platform.douyin

import android.content.Context
import dtv.mobile.platform.danmaku.DeferredDanmakuConnection
import dtv.mobile.platform.danmaku.okHttpTermination
import dtv.mobile.repo.DanmakuConnectionException
import dtv.mobile.repo.DanmakuFailure
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.DanmakuProtocolConnection
import dtv.mobile.repo.DanmakuProtocolConnector
import dtv.mobile.repo.DanmakuProtocolSink
import dtv.mobile.repo.DanmakuTermination
import dtv.mobile.repo.DanmakuTerminationKind
import dtv.mobile.util.runSuspendCatching
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * 抖音弹幕（Android, 纯 Kotlin/OkHttp）：
 * - GET `https://live.douyin.com/<webRid>` 解析 `user_unique_id` / `room id_str` / Cookie
 * - 拼接 WebSocket 参数 + WebMsSDK `signature`
 * - OkHttp WebSocket 收包：解 PushFrame / gzip / ack / chat message
 *
 * 参考 `kotlin-danmaku-android` 的实现结构重写；并移除对 Rust/JNI 的桥接依赖。
 */
class DouyinDanmakuClientAndroid(
  appContext: Context,
  private val webApi: DouyinWebApiAndroid,
  private val http: OkHttpClient = defaultHttpClient(),
  private val wsClient: OkHttpClient = defaultWsClient(),
) : DanmakuProtocolConnector {
  override val watchdogTimeoutMs: Long = 90_000L
  data class PrimedContext(
    val roomId: String,
    val msToken: String?,
  )

  private data class RoomInit(
    val roomId: String,
    val userUniqueId: String,
    val cookieHeader: String,
  )

  companion object {
    private const val TAG = "DTV-Douyin"

    private const val USER_AGENT = DouyinWebApiAndroid.DEFAULT_USER_AGENT
    private const val REFERER_BASE = "https://live.douyin.com"

    private val WS_BASES = listOf(
      "wss://webcast5-ws-web-hl.douyin.com/webcast/im/push/v2/?",
      "wss://webcast3-ws-web-lq.douyin.com/webcast/im/push/v2/?",
      "wss://webcast5-ws-web-lf.douyin.com/webcast/im/push/v2/?",
    )

    // 与 kotlin-danmaku-android 保持一致的参数组合（移动端已验证可用）。
    private const val VERSION_CODE = "180800"
    private const val WEBCAST_SDK_VERSION = "1.3.0"
    private const val UPDATE_VERSION_CODE = "1.3.0"

    private const val HEARTBEAT_MS: Long = 10_000
    private val RANDOM = SecureRandom()

    private fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun defaultWsClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
  }

  private val webMsdkSigner = DouyinWebMsdkSignatureAndroid(appContext)
  private val primed = ConcurrentHashMap<String, PrimedContext>()

  fun prime(webRid: String, roomId: String, msToken: String?) {
    val rid = webRid.trim()
    val rId = roomId.trim()
    if (rid.isBlank() || rId.isBlank()) return
    primed[rid] = PrimedContext(roomId = rId, msToken = msToken)
  }

  override suspend fun connect(
    roomId: String,
    sink: DanmakuProtocolSink,
  ): DanmakuProtocolConnection = withContext(Dispatchers.IO) {
    val webRid = roomId.trim()
    if (webRid.isBlank()) {
      throw DanmakuConnectionException(DanmakuFailure.RoomUnavailable, "抖音房间号为空")
    }

    val init = try {
      resolveRoomInit(
        webRid = webRid,
        primedRoomId = primed[webRid]?.roomId,
      )
    } catch (t: CancellationException) {
      throw t
    } catch (t: Throwable) {
      throw DanmakuConnectionException(
        failure = DanmakuFailure.Network,
        message = t.message ?: "抖音房间初始化失败",
        cause = t,
      )
    }

    var lastFailure = DanmakuFailure.Protocol
    for (base in WS_BASES.shuffled(RANDOM)) {
      val wsUrl = runSuspendCatching { buildWsUrl(base = base, init = init) }.getOrNull().orEmpty()
      if (wsUrl.isBlank()) continue
      val result = runSuspendCatching {
        connectEndpoint(
          webRid = webRid,
          wsUrl = wsUrl,
          cookieHeader = init.cookieHeader,
          sink = sink,
        )
      }
      result.getOrNull()?.let { return@withContext it }
      val error = result.exceptionOrNull()
      if (error is CancellationException) throw error
      lastFailure = (error as? DanmakuConnectionException)?.failure ?: DanmakuFailure.Protocol
    }

    throw DanmakuConnectionException(lastFailure, "抖音弹幕 WebSocket 握手失败")
  }

  private suspend fun connectEndpoint(
    webRid: String,
    wsUrl: String,
    cookieHeader: String,
    sink: DanmakuProtocolSink,
  ): DanmakuProtocolConnection = withContext(Dispatchers.IO) {
    val ready = CompletableDeferred<DanmakuProtocolConnection>()
    val termination = CompletableDeferred<DanmakuTermination>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val heartbeat = DouyinProtoLite.encodePushFrame(
      payloadType = "hb",
      logId = 0L,
      payload = ByteArray(0),
    )
    var heartbeatJob: kotlinx.coroutines.Job? = null

    fun failBeforeReady(result: DanmakuTermination) {
      termination.complete(result)
      if (!ready.isCompleted) {
        ready.completeExceptionally(
          DanmakuConnectionException(
            failure = result.failure ?: DanmakuFailure.Network,
            message = result.detail ?: "抖音 WebSocket 在就绪前关闭",
          ),
        )
      }
    }

    val request = Request.Builder()
      .url(wsUrl)
      .addHeader("accept", "application/json, text/plain, */*")
      .addHeader("accept-language", "zh-CN,zh;q=0.9,en;q=0.8")
      .addHeader("cache-control", "no-cache")
      .addHeader("pragma", "no-cache")
      .addHeader("User-Agent", USER_AGENT)
      .addHeader("Cookie", cookieHeader)
      .addHeader("Origin", REFERER_BASE)
      .addHeader("Referer", "$REFERER_BASE/$webRid")
      .build()

    val listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        sink.onAuthenticating()
        sink.onInbound()
        heartbeatJob = scope.launch {
          while (isActive) {
            if (!webSocket.send(heartbeat.toByteString())) break
            sink.onInbound()
            delay(HEARTBEAT_MS)
          }
        }
        ready.complete(
          DeferredDanmakuConnection(
            termination = termination,
            closeAction = {
              heartbeatJob.cancel()
              webSocket.cancel()
              scope.cancel()
            },
          ),
        )
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        sink.onInbound()
        val push = DouyinProtoLite.decodePushFrame(bytes.toByteArray())
          ?: run {
            sink.onProtocolError()
            return
          }
        if (push.payloadType != "msg" || push.payload.isEmpty()) return

        val decompressed = gunzipOrNull(push.payload) ?: return
        val response = DouyinProtoLite.decodeResponse(decompressed)
          ?: run {
            sink.onProtocolError()
            return
          }

        if (response.needAck) {
          val ack = DouyinProtoLite.encodePushFrame(
            payloadType = "ack",
            logId = push.logId,
            payload = response.internalExt.toByteArray(Charsets.UTF_8),
          )
          webSocket.send(ack.toByteString())
        }

        for (message in response.messages) {
          if (message.method != "WebcastChatMessage") continue
          val chat = DouyinProtoLite.decodeChatMessage(message.payload)
            ?: run {
              sink.onProtocolError()
              continue
            }
          sink.onMessage(
            DanmakuMessage(
              roomId = webRid,
              user = chat.nick,
              content = chat.content,
              userLevel = chat.userLevel,
              fansClubLevel = chat.fansClubLevel,
            ),
          )
        }
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        heartbeatJob?.cancel()
        failBeforeReady(DanmakuTermination(DanmakuTerminationKind.Closed, detail = reason))
        scope.cancel()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        heartbeatJob?.cancel()
        failBeforeReady(okHttpTermination(response, t.message))
        scope.cancel()
      }
    }

    try {
      val socket = wsClient.newWebSocket(request, listener)
      try {
        ready.await()
      } catch (t: Throwable) {
        heartbeatJob?.cancel()
        socket.cancel()
        scope.cancel()
        throw t
      }
    } catch (t: Throwable) {
      scope.cancel()
      throw t
    }
  }

  private suspend fun resolveRoomInit(webRid: String, primedRoomId: String?): RoomInit {
    val request = Request.Builder()
      .url("$REFERER_BASE/$webRid")
      .header("User-Agent", USER_AGENT)
      .header("Referer", REFERER_BASE)
      .header("Cookie", webApi.cookieHeader())
      .build()

    http.newCall(request).execute().use { resp ->
      // Align with kotlin-danmaku-android: only take the first Set-Cookie pair.
      val cookieHeader = resp.headers("Set-Cookie")
        .firstOrNull()
      webApi.updateCookieFromSetCookie(cookieHeader)
      val effectiveCookie = cookieHeader?.substringBefore(';')?.trim().orEmpty()
        .ifBlank { webApi.cookieHeader() }

      val html = resp.body?.string().orEmpty()
      val rawUserUniqueId = extractUserUniqueId(html)
      val userUniqueId = rawUserUniqueId.ifBlank { randomDigits(12) }

      val roomIdFromHtml = extractRoomId(html)
      val roomIdFromApi = runSuspendCatching { webApi.fetchRoomEnter(webRid).roomId?.trim().orEmpty() }.getOrDefault("")
      val roomId = primedRoomId?.trim().orEmpty().ifBlank { roomIdFromHtml }.ifBlank { roomIdFromApi }
      if (roomId.isBlank()) error("Cannot resolve roomId for webRid=$webRid")

      return RoomInit(roomId = roomId, userUniqueId = userUniqueId, cookieHeader = effectiveCookie)
    }
  }

  private suspend fun buildWsUrl(base: String, init: RoomInit): String {
    val ts = System.currentTimeMillis()
    val urlPrefix =
      base +
        listOf(
          "app_name=douyin_web",
          "version_code=$VERSION_CODE",
          "webcast_sdk_version=$WEBCAST_SDK_VERSION",
          "update_version_code=$UPDATE_VERSION_CODE",
          "compress=gzip",
          "cursor=${encode("h-1_t-${ts}_r-1_d-1_u-1")}",
          "host=${encode("https://live.douyin.com")}",
          "aid=6383",
          "live_id=1",
          "did_rule=3",
          "debug=false",
          "maxCacheMessageNumber=20",
          "endpoint=live_pc",
          "support_wrds=1",
          "im_path=${encode("/webcast/im/fetch/")}",
          "user_unique_id=${init.userUniqueId}",
          "device_platform=web",
          "cookie_enabled=true",
          "screen_width=1920",
          "screen_height=1080",
          "browser_language=zh-CN",
          "browser_platform=Win32",
          "browser_name=Mozilla",
          "browser_version=${encode(USER_AGENT.removePrefix("Mozilla/"))}",
          "browser_online=true",
          "tz_name=Asia/Shanghai",
          "identity=audience",
          "room_id=${init.roomId}",
          "heartbeatDuration=0",
        ).joinToString("&")

    val sig = runSuspendCatching {
      webMsdkSigner.signature(
        roomId = init.roomId,
        userUniqueId = init.userUniqueId,
        webcastSdkVersion = WEBCAST_SDK_VERSION,
        userAgent = USER_AGENT,
      )
    }.getOrNull().orEmpty()

    if (sig.isBlank()) return ""
    return "$urlPrefix&signature=${encode(sig)}"
  }

  private fun extractUserUniqueId(html: String): String {
    val patterns = listOf(
      // Matches kotlin-danmaku-android demo parsing (escaped JSON in HTML).
      Regex("""\\\"user_unique_id\\\":\\\"(\d+)\\\""""),
      // Unescaped JSON in HTML.
      Regex(""""user_unique_id"\s*:\s*"(\d+)""""),
    )
    for (re in patterns) {
      val m = re.find(html) ?: continue
      val id = m.groupValues.getOrNull(1).orEmpty()
      if (!id.isNullOrBlank()) return id.trim()
    }
    return ""
  }

  private fun extractRoomId(html: String): String {
    val patterns = listOf(
      // Escaped JSON in HTML.
      Regex("""\\\"roomInfo\\\":\{\\\"room\\\":\{\\\"id_str\\\":\\\"(\d+)\\\""""),
      // Unescaped JSON in HTML.
      Regex(""""id_str"\s*:\s*"(\d+)""""),
    )
    for (re in patterns) {
      val m = re.find(html) ?: continue
      val id = m.groupValues.getOrNull(1).orEmpty()
      if (!id.isNullOrBlank()) return id.trim()
    }
    return ""
  }

  private fun gunzipOrNull(data: ByteArray): ByteArray? {
    if (data.isEmpty()) return null
    return runCatching { GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() } }.getOrNull()
  }

  private fun randomDigits(n: Int): String {
    val sb = StringBuilder(n)
    repeat(n) { sb.append(('0'.code + RANDOM.nextInt(10)).toChar()) }
    return sb.toString()
  }

  private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

  // cookie merge removed: follow kotlin-danmaku-android behavior.
}
