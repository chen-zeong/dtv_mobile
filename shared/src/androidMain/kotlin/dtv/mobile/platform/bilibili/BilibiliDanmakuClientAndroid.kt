package dtv.mobile.platform.bilibili

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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.security.MessageDigest
import kotlin.math.min

class BilibiliDanmakuClientAndroid(
  private val httpClient: HttpClient,
  private val cookieProvider: () -> String?,
  private val okHttp: OkHttpClient = OkHttpClient(),
) : DanmakuProtocolConnector {
  override val watchdogTimeoutMs: Long = 90_000L
  companion object {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // https://github.com/SocialSisterYi/bilibili-API-collect (WBI signature)
    // kept aligned with DTV-heroui src-tauri/src/platforms/bilibili/auth.rs
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
      46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
      27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
      37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
      22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
    )

    private data class NavInfo(
      val imgKey: String,
      val subKey: String,
      val mid: Long?,
    )

    @Volatile private var cachedNavInfo: NavInfo? = null
    @Volatile private var cachedWbiAtMs: Long = 0L
  }

  private data class DanmuInfo(
    val roomId: Int,
    val uid: Long,
    val token: String,
    val endpoints: List<Endpoint>,
  )

  private data class Endpoint(
    val host: String,
    val wssPort: Int,
  )

  private fun takeFilename(url: String): String? {
    val slash = url.lastIndexOf('/')
    if (slash < 0) return null
    val tail = url.substring(slash + 1)
    val dot = tail.lastIndexOf('.')
    if (dot <= 0) return null
    return tail.substring(0, dot)
  }

  private fun md5Hex(s: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(s.encodeToByteArray())
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) sb.append(String.format("%02x", b))
    return sb.toString()
  }

  private fun urlEncodedWbiComponent(input: String): String {
    val out = StringBuilder(input.length + 16)
    for (ch in input) {
      when {
        ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~' -> out.append(ch)
        ch == '!' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' -> Unit // skip
        else -> {
          val bytes = ch.toString().encodeToByteArray()
          for (b in bytes) out.append(String.format("%%%02X", b.toInt() and 0xFF))
        }
      }
    }
    return out.toString()
  }

  private fun getMixinKey(orig: String): String {
    val bytes = orig.encodeToByteArray()
    val take = min(32, MIXIN_KEY_ENC_TAB.size)
    val sb = StringBuilder(32)
    for (i in 0 until take) {
      val idx = MIXIN_KEY_ENC_TAB[i]
      val b = bytes.getOrNull(idx) ?: 0
      sb.append((b.toInt() and 0xFF).toChar())
    }
    return sb.toString()
  }

  private suspend fun getNavInfo(): NavInfo {
    val now = System.currentTimeMillis()
    val cached = cachedNavInfo
    if (cached != null && now - cachedWbiAtMs < 6 * 60 * 60 * 1000L) return cached

    val url = "https://api.bilibili.com/x/web-interface/nav"
    val cookie = cookieProvider()
    val text = httpClient.get(url) {
      headers {
        append("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:138.0) Gecko/20100101 Firefox/138.0")
        append("Referer", "https://www.bilibili.com/")
        if (!cookie.isNullOrBlank()) append("Cookie", cookie)
      }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val data = root["data"]?.jsonObject ?: error("B站 nav data 为空")
    val mid = data["mid"]?.jsonPrimitive?.content?.toLongOrNull()
    val wbi = data["wbi_img"]?.jsonObject ?: error("B站 nav wbi_img 为空")
    val imgUrl = wbi["img_url"]?.jsonPrimitive?.content?.trim().orEmpty()
    val subUrl = wbi["sub_url"]?.jsonPrimitive?.content?.trim().orEmpty()
    val imgKey = takeFilename(imgUrl) ?: error("B站 img_key 解析失败")
    val subKey = takeFilename(subUrl) ?: error("B站 sub_key 解析失败")
    val nav = NavInfo(imgKey = imgKey, subKey = subKey, mid = mid)

    cachedNavInfo = nav
    cachedWbiAtMs = now
    return nav
  }

  private suspend fun signedWbiQuery(params: List<Pair<String, String>>): String {
    val (imgKey, subKey, _) = getNavInfo()
    val mixinKey = getMixinKey(imgKey + subKey)
    val wts = (System.currentTimeMillis() / 1000L).toString()

    val all = (params + ("wts" to wts)).sortedBy { it.first }
    val query = all.joinToString("&") { (k, v) ->
      "${urlEncodedWbiComponent(k)}=${urlEncodedWbiComponent(v)}"
    }
    val wRid = md5Hex(query + mixinKey)
    return "$query&w_rid=$wRid"
  }

  private suspend fun fetchDanmuInfo(roomId: String): DanmuInfo {
    val query = signedWbiQuery(
      listOf(
        "id" to roomId,
        "type" to "0",
        "web_location" to "444.8",
      ),
    )
    val url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?$query"
    val cookie = cookieProvider()
    val text = httpClient.get(url) {
      headers {
        append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        append("Referer", "https://live.bilibili.com/")
        if (!cookie.isNullOrBlank()) append("Cookie", cookie)
      }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val code = root["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
    if (code != 0) {
      error("B站弹幕参数获取失败(code=$code)")
    }

    val data = root["data"]?.jsonObject ?: error("B站弹幕参数为空")
    val token = data["token"]?.jsonPrimitive?.content?.trim().orEmpty()
    val realRoomId = data["roomid"]?.jsonPrimitive?.content?.toIntOrNull() ?: roomId.toIntOrNull() ?: 0

    val hostList = data["host_list"]?.jsonArray ?: JsonArray(emptyList())
    val endpoints =
      hostList.asSequence()
        .mapNotNull { it.jsonObject }
        .mapNotNull { obj ->
          val host = obj["host"]?.jsonPrimitive?.content?.trim().orEmpty()
          val port = obj["wss_port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
          if (host.isBlank() || port <= 0) null else Endpoint(host = host, wssPort = port)
        }
        .distinctBy { "${it.host}:${it.wssPort}" }
        .sortedWith(compareBy<Endpoint>({ if (it.wssPort == 443) 0 else 1 }, { it.host }))
        .toList()
    if (token.isBlank()) error("B站弹幕 token 为空")

    val resolvedEndpoints = endpoints.ifEmpty { listOf(Endpoint(host = "broadcastlv.chat.bilibili.com", wssPort = 443)) }
    val uid = runSuspendCatching { getNavInfo().mid }.getOrNull() ?: 0L
    return DanmuInfo(roomId = realRoomId, uid = uid, token = token, endpoints = resolvedEndpoints)
  }

  private fun buildPacket(op: Int, body: ByteArray = ByteArray(0), ver: Int = 1, seq: Int = 1): ByteArray {
    val packetLen = 16 + body.size
    val buf = ByteBuffer.allocate(packetLen).order(ByteOrder.BIG_ENDIAN)
    buf.putInt(packetLen)
    buf.putShort(16)
    buf.putShort(ver.toShort())
    buf.putInt(op)
    buf.putInt(seq)
    buf.put(body)
    return buf.array()
  }

  private data class Packet(val ver: Int, val op: Int, val body: ByteArray)

  private fun parsePackets(bytes: ByteArray): List<Packet> {
    val out = ArrayList<Packet>()
    var offset = 0
    while (offset + 16 <= bytes.size) {
      val len = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
      if (len <= 0 || offset + len > bytes.size) break
      val headerLen = ByteBuffer.wrap(bytes, offset + 4, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
      val ver = ByteBuffer.wrap(bytes, offset + 6, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
      val op = ByteBuffer.wrap(bytes, offset + 8, 4).order(ByteOrder.BIG_ENDIAN).int
      val bodyStart = offset + headerLen
      val bodyEnd = offset + len
      val body = if (bodyStart in 0..bodyEnd && bodyEnd <= bytes.size) bytes.copyOfRange(bodyStart, bodyEnd) else ByteArray(0)
      out.add(Packet(ver = ver, op = op, body = body))
      offset += len
    }
    return out
  }

  private fun inflateZlib(data: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    while (!inflater.finished() && !inflater.needsInput()) {
      val n = inflater.inflate(buf)
      if (n <= 0) break
      out.write(buf, 0, n)
    }
    inflater.end()
    return out.toByteArray()
  }

  private fun decodeChatMessageOrNull(packetBody: ByteArray): DanmakuMessage? {
    val root = runCatching { json.parseToJsonElement(packetBody.decodeToString()).jsonObject }.getOrNull() ?: return null
    val cmd = root["cmd"]?.jsonPrimitive?.content?.orEmpty() ?: return null
    if (!cmd.startsWith("DANMU_MSG")) return null

    val info = root["info"] as? JsonArray ?: return null
    val content = info.getOrNull(1)?.jsonPrimitive?.content?.trim().orEmpty()
    val user = (info.getOrNull(2) as? JsonArray)?.getOrNull(1)?.jsonPrimitive?.content?.trim().orEmpty()
    if (content.isBlank()) return null
    return DanmakuMessage(roomId = "", user = user.ifBlank { "匿名" }, content = content)
  }

  override suspend fun connect(
    roomId: String,
    sink: DanmakuProtocolSink,
  ): DanmakuProtocolConnection = withContext(Dispatchers.IO) {
    val info = try {
      fetchDanmuInfo(roomId)
    } catch (t: CancellationException) {
      throw t
    } catch (t: Throwable) {
      throw DanmakuConnectionException(
        failure = DanmakuFailure.Authentication,
        message = t.message ?: "获取 B站弹幕参数失败",
        cause = t,
      )
    }

    val authJson =
      """{"uid":${info.uid.coerceAtLeast(0L)},"roomid":${info.roomId},"protover":1,"platform":"web","type":2,"key":"${info.token}"}""".encodeToByteArray()
    val authPacket = buildPacket(op = 7, body = authJson, ver = 1, seq = 1)
    val heartbeatPacket = buildPacket(op = 2, body = ByteArray(0), ver = 1, seq = 1)
    var lastFailure: DanmakuFailure = DanmakuFailure.Protocol

    for (endpoint in info.endpoints) {
      val result = runCatching {
        connectEndpoint(
          roomId = roomId,
          wsUrl = if (endpoint.wssPort == 443) {
            "wss://${endpoint.host}/sub"
          } else {
            "wss://${endpoint.host}:${endpoint.wssPort}/sub"
          },
          authPacket = authPacket,
          heartbeatPacket = heartbeatPacket,
          sink = sink,
        )
      }
      result.getOrNull()?.let { return@withContext it }
      val error = result.exceptionOrNull()
      if (error is CancellationException) throw error
      lastFailure = (error as? DanmakuConnectionException)?.failure ?: DanmakuFailure.Protocol
    }

    throw DanmakuConnectionException(
      failure = lastFailure,
      message = "B站弹幕 WebSocket 握手失败",
    )
  }

  private suspend fun connectEndpoint(
    roomId: String,
    wsUrl: String,
    authPacket: ByteArray,
    heartbeatPacket: ByteArray,
    sink: DanmakuProtocolSink,
  ): DanmakuProtocolConnection = withContext(Dispatchers.IO) {
    val ready = CompletableDeferred<DanmakuProtocolConnection>()
    val termination = CompletableDeferred<DanmakuTermination>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var heartbeatJob: kotlinx.coroutines.Job? = null

    fun failBeforeReady(result: DanmakuTermination) {
      termination.complete(result)
      if (!ready.isCompleted) {
        ready.completeExceptionally(
          DanmakuConnectionException(
            failure = result.failure ?: DanmakuFailure.Network,
            message = result.detail ?: "B站 WebSocket 在认证前关闭",
          ),
        )
      }
    }

    val request = Request.Builder()
      .url(wsUrl)
      .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
      .addHeader("Referer", "https://live.bilibili.com/")
      .addHeader("Origin", "https://live.bilibili.com")
      .build()

    val listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        sink.onAuthenticating()
        webSocket.send(ByteString.of(*authPacket))
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        sink.onInbound()
        for (packet in parsePackets(bytes.toByteArray())) {
          when (packet.op) {
            8 -> {
              sink.onInbound()
              heartbeatJob?.cancel()
              heartbeatJob = scope.launch {
                while (isActive) {
                  delay(30_000L)
                  if (!webSocket.send(ByteString.of(*heartbeatPacket))) break
                  sink.onInbound()
                }
              }
              ready.complete(
                DeferredDanmakuConnection(
                  termination = termination,
                  closeAction = {
                    heartbeatJob.cancel()
                    webSocket.close(1000, "close")
                    scope.cancel()
                  },
                ),
              )
            }

            5 -> {
              if (packet.ver == 2) {
                val inflated = runCatching { inflateZlib(packet.body) }
                  .onFailure { sink.onProtocolError() }
                  .getOrNull()
                  ?: continue
                for (inner in parsePackets(inflated)) {
                  if (inner.op != 5) continue
                  val message = decodeChatMessageOrNull(inner.body) ?: continue
                  sink.onMessage(message.copy(roomId = roomId))
                }
              } else {
                val message = decodeChatMessageOrNull(packet.body) ?: continue
                sink.onMessage(message.copy(roomId = roomId))
              }
            }
          }
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
      val socket = okHttp.newWebSocket(request, listener)
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
}
