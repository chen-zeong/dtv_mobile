package dtv.mobile.platform.bilibili

import dtv.mobile.util.AppLog
import dtv.mobile.repo.DanmakuMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Inflater
import java.security.MessageDigest
import kotlin.math.min

class BilibiliDanmakuClientAndroid(
  private val httpClient: HttpClient,
  private val cookieProvider: () -> String?,
  private val okHttp: OkHttpClient = OkHttpClient(),
) {
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

    @Volatile private var cachedWbiKeys: Pair<String, String>? = null
    @Volatile private var cachedWbiAtMs: Long = 0L
  }

  private data class DanmuInfo(
    val roomId: Int,
    val token: String,
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

  private suspend fun getWbiKeys(): Pair<String, String> {
    val now = System.currentTimeMillis()
    val cached = cachedWbiKeys
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
    val wbi = data["wbi_img"]?.jsonObject ?: error("B站 nav wbi_img 为空")
    val imgUrl = wbi["img_url"]?.jsonPrimitive?.content?.trim().orEmpty()
    val subUrl = wbi["sub_url"]?.jsonPrimitive?.content?.trim().orEmpty()
    val imgKey = takeFilename(imgUrl) ?: error("B站 img_key 解析失败")
    val subKey = takeFilename(subUrl) ?: error("B站 sub_key 解析失败")
    val pair = imgKey to subKey

    cachedWbiKeys = pair
    cachedWbiAtMs = now
    return pair
  }

  private suspend fun signedWbiQuery(params: List<Pair<String, String>>): String {
    val (imgKey, subKey) = getWbiKeys()
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
      val msg = root["message"]?.jsonPrimitive?.content
      AppLog.w("DTV-Bilibili", "getDanmuInfo failed code=$code msg=$msg roomId=$roomId")
      error("B站弹幕参数获取失败(code=$code)")
    }

    val data = root["data"]?.jsonObject ?: error("B站弹幕参数为空")
    val token = data["token"]?.jsonPrimitive?.content?.trim().orEmpty()
    val realRoomId = data["roomid"]?.jsonPrimitive?.content?.toIntOrNull() ?: roomId.toIntOrNull() ?: 0

    val hostList = data["host_list"]?.jsonArray ?: JsonArray(emptyList())
    val preferred = hostList
      .asSequence()
      .mapNotNull { it.jsonObject }
      .firstOrNull { it["wss_port"]?.jsonPrimitive?.content?.toIntOrNull() == 443 }
      ?: hostList.firstOrNull()?.jsonObject
    val host = preferred?.get("host")?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "broadcastlv.chat.bilibili.com" }
    val wssPort = preferred?.get("wss_port")?.jsonPrimitive?.content?.toIntOrNull() ?: 443
    if (token.isBlank()) error("B站弹幕 token 为空")

    return DanmuInfo(roomId = realRoomId, token = token, host = host, wssPort = wssPort)
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

  fun observe(roomId: String): Flow<DanmakuMessage> = callbackFlow {
    val socketRef = AtomicReference<WebSocket?>(null)

    val job = launch(Dispatchers.IO) {
      val info = runCatching { fetchDanmuInfo(roomId) }
        .onFailure { AppLog.e("DTV-Bilibili", "fetch danmu info failed roomId=$roomId", it) }
        .getOrNull()
        ?: return@launch

      val wsUrl = if (info.wssPort == 443) "wss://${info.host}/sub" else "wss://${info.host}:${info.wssPort}/sub"
      AppLog.i("DTV-Bilibili", "danmaku ws url=$wsUrl roomId=$roomId(real=${info.roomId})")
      val authJson =
        // protover=1 requests plain JSON (avoid zlib/brotli differences across regions).
        """{"uid":0,"roomid":${info.roomId},"protover":1,"platform":"web","type":2,"key":"${info.token}"}""".encodeToByteArray()
      val authPacket = buildPacket(op = 7, body = authJson, ver = 1, seq = 1)
      val heartbeatPacket = buildPacket(op = 2, body = ByteArray(0), ver = 1, seq = 1)

      val req = Request.Builder()
        .url(wsUrl)
        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        .addHeader("Referer", "https://live.bilibili.com/")
        .build()

      val listener = object : WebSocketListener() {
        var msgCount = 0

        override fun onOpen(webSocket: WebSocket, response: Response) {
          socketRef.set(webSocket)
          AppLog.i("DTV-Bilibili", "danmaku ws opened roomId=$roomId")
          webSocket.send(ByteString.of(*authPacket))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
          val raw = bytes.toByteArray()
          for (p in parsePackets(raw)) {
            when (p.op) {
              8 -> {
                AppLog.i("DTV-Bilibili", "danmaku auth ok roomId=$roomId ver=${p.ver} bodyLen=${p.body.size}")
              }
              5 -> {
                msgCount++
                if (msgCount == 1 || msgCount % 50 == 0) {
                  AppLog.i("DTV-Bilibili", "danmaku messages received roomId=$roomId count=$msgCount ver=${p.ver} bodyLen=${p.body.size}")
                }
                if (p.ver == 2) {
                  val inflated = runCatching { inflateZlib(p.body) }.getOrNull() ?: continue
                  for (inner in parsePackets(inflated)) {
                    if (inner.op != 5) continue
                    val msg = decodeChatMessageOrNull(inner.body) ?: continue
                    trySend(msg.copy(roomId = roomId))
                  }
                } else {
                  val msg = decodeChatMessageOrNull(p.body) ?: continue
                  trySend(msg.copy(roomId = roomId))
                }
              }
            }
          }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          AppLog.e("DTV-Bilibili", "bilibili danmaku ws failure roomId=$roomId", t)
          // Do not propagate to the UI collector: danmaku failures should not crash the app.
          close()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          close()
        }
      }

      val socket = okHttp.newWebSocket(req, listener)
      socketRef.set(socket)

      while (true) {
        delay(30_000)
        val s = socketRef.get() ?: break
        val ok = s.send(ByteString.of(*heartbeatPacket))
        if (!ok) break
      }
    }

    awaitClose {
      runCatching { socketRef.getAndSet(null)?.close(1000, "close") }
      job.cancel()
    }
  }
}
