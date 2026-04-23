package dtv.mobile.platform.douyu

import android.content.Context
import dtv.mobile.repo.DouyuPlayInfo
import dtv.mobile.repo.DouyuPlayVariant
import dtv.mobile.util.decodeHtmlEntities
import dtv.mobile.util.jsonElementToInt
import dtv.mobile.util.jsonElementToString
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class DouyuStreamUrlResolverAndroid(
  private val appContext: Context,
  private val client: HttpClient,
) {
  private val signer = DouyuJsSignerAndroid(appContext)
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  suspend fun resolve(
    roomId: String,
    quality: String? = null,
    cdn: String? = null,
  ): String = withContext(Dispatchers.IO) {
    val (realRoomId, isLive) = fetchRoomDetail(roomId)
    if (!isLive) error("主播未开播")

    val signData = buildSignParams(realRoomId)
    val playInfo = getPlayQualities(realRoomId, signData)

    val selectedRate = resolveRate(quality, playInfo.variants) ?: playInfo.variants.maxOfOrNull { it.rate } ?: 0
    val selectedCdn = selectCdn(cdn, playInfo.cdns)

    getPlayUrl(realRoomId, signData, selectedRate, selectedCdn)
  }

  suspend fun fetchPlayInfo(roomId: String): DouyuPlayInfo = withContext(Dispatchers.IO) {
    val (realRoomId, isLive) = fetchRoomDetail(roomId)
    if (!isLive) error("主播未开播")
    val signData = buildSignParams(realRoomId)
    val playInfo = getPlayQualities(realRoomId, signData)
    DouyuPlayInfo(
      cdns = playInfo.cdns,
      variants = playInfo.variants.map { DouyuPlayVariant(name = it.name, rate = it.rate, bit = it.bit) },
    )
  }

  suspend fun isLive(roomId: String): Boolean = withContext(Dispatchers.IO) {
    fetchRoomDetail(roomId).second
  }

  private suspend fun fetchRoomDetail(roomId: String): Pair<String, Boolean> {
    val url = "https://www.douyu.com/betard/$roomId"
    val httpResp = client.get(url) { header(HttpHeaders.Referrer, "https://www.douyu.com/$roomId") }
    val text = httpResp.bodyAsText().trim()
    val resp = json.decodeFromString(DouyuBetardResponse.serializer(), text)
    val room = resp.room ?: error("Missing room data")
    val realRoomId = jsonElementToString(room.roomId)?.trim().orEmpty()
    if (realRoomId.isEmpty()) error("Invalid room_id")
    val showStatus = jsonElementToInt(room.showStatus) ?: 0
    return realRoomId to (showStatus == 1)
  }

  private suspend fun buildSignParams(roomId: String): String {
    val script = getHomeH5Enc(roomId)
    val ts = System.currentTimeMillis() / 1000
    return signer.signParams(
      homeH5EncScript = script,
      roomId = roomId,
      did = DEFAULT_DOUYU_DID,
      tsSeconds = ts,
    )
  }

  private suspend fun getHomeH5Enc(roomId: String): String {
    val url = "https://www.douyu.com/swf_api/homeH5Enc?rids=$roomId"
    val httpResp = client.get(url) { header(HttpHeaders.Referrer, "https://www.douyu.com/$roomId") }
    val text = httpResp.bodyAsText().trim()
    val resp = json.decodeFromString(DouyuHomeH5EncResponse.serializer(), text)
    if (resp.error != 0) error("homeH5Enc error: ${resp.error}")
    val key = "room$roomId"
    return resp.data?.get(key) ?: error("Missing homeH5Enc data")
  }

  private suspend fun getPlayQualities(roomId: String, signData: String): InternalPlayInfo {
    val payload = "$signData&cdn=&rate=-1&ver=Douyu_223061205&iar=1&ive=1&hevc=0&fa=0"
    val url = "https://www.douyu.com/lapi/live/getH5Play/$roomId"
    val httpResp = client.post(url) {
      contentType(ContentType.Application.FormUrlEncoded)
      setBody(payload)
    }
    val text = httpResp.bodyAsText().trim()
    val resp = json.decodeFromString(DouyuH5PlayResponse.serializer(), text)

    if (resp.error != 0) error("getH5Play error ${resp.error}: ${resp.msg ?: "failed"}")
    val data = resp.data ?: error("No data field in response")

    val cdns = data.cdnsWithName.orEmpty().mapNotNull { it.cdn?.trim() }.filter { it.isNotEmpty() }
      .sortedWith(compareBy({ it.startsWith("scdn") }, { it }))
    val variants = data.multirates.orEmpty().mapNotNull { raw ->
      val name = raw.name?.trim().orEmpty()
      val rate = raw.rate
      if (name.isEmpty() || rate == null) return@mapNotNull null
      DouyuRateVariant(name = name, rate = rate, bit = raw.bit)
    }
    return InternalPlayInfo(
      variants = variants,
      cdns = if (cdns.isNotEmpty()) cdns else listOf(DEFAULT_DOUYU_CDN),
    )
  }

  private suspend fun getPlayUrl(roomId: String, signData: String, rate: Int, cdn: String): String {
    val payload = "$signData&cdn=$cdn&rate=$rate&ver=Douyu_223061205&iar=1&ive=1&hevc=0&fa=0"
    val url = "https://www.douyu.com/lapi/live/getH5Play/$roomId"
    val httpResp = client.post(url) {
      contentType(ContentType.Application.FormUrlEncoded)
      setBody(payload)
    }
    val text = httpResp.bodyAsText().trim()
    val resp = json.decodeFromString(DouyuH5PlayResponse.serializer(), text)

    if (resp.error != 0) error("getH5Play error ${resp.error}: ${resp.msg ?: "failed"}")
    val data = resp.data ?: error("No data field in response")
    val rtmpUrl = data.rtmpUrl ?: error("No rtmp_url field")
    val rtmpLive = data.rtmpLive?.let(::decodeHtmlEntities) ?: error("No rtmp_live field")
    // Some Douyu CDN redirects use underscores in HTTPS hostnames which breaks Android's SNI/IDN handling.
    // For mobile playback, prefer cleartext HTTP to avoid TLS SNI host validation failures.
    val normalizedBase = if (rtmpUrl.startsWith("https://")) {
      "http://" + rtmpUrl.removePrefix("https://")
    } else {
      rtmpUrl
    }
    return "$normalizedBase/$rtmpLive"
  }

  private fun selectCdn(requested: String?, available: List<String>): String {
    val target = requested?.trim()?.lowercase().orEmpty()
    if (target.isNotEmpty()) {
      available.firstOrNull { it.lowercase() == target }?.let { return it }
    }
    return available.firstOrNull() ?: DEFAULT_DOUYU_CDN
  }

  private fun resolveRate(quality: String?, variants: List<DouyuRateVariant>): Int? {
    if (variants.isEmpty()) return null
    val q = quality?.trim().orEmpty()
    if (q.isEmpty()) return null
    val lower = q.lowercase()

    val canonical = when {
      q.contains('原') || lower == "origin" -> "原画"
      q.contains('高') || lower == "high" -> "高清"
      q.contains('标') || lower == "standard" -> "标清"
      else -> q
    }

    fun findByKeyword(vararg keywords: String): Int? {
      for (k in keywords) {
        variants.firstOrNull { it.name.contains(k) }?.let { return it.rate }
      }
      return null
    }

    return when (canonical) {
      "原画" -> {
        variants.firstOrNull { it.rate == 0 }?.rate
          ?: findByKeyword("原画", "蓝光8M", "蓝光")
          ?: variants.minOfOrNull { it.rate }
      }
      "高清" -> {
        variants.firstOrNull { it.rate == 4 }?.rate
          ?: findByKeyword("蓝光", "蓝光4M")
          ?: findByKeyword("超清")
          ?: findByKeyword("高清")
          ?: variants.filter { it.rate != 0 }.maxByOrNull { it.bit ?: 0 }?.rate
          ?: variants.filter { it.rate != 0 }.maxOfOrNull { it.rate }
      }
      "标清" -> {
        variants.firstOrNull { it.rate == 3 }?.rate
          ?: findByKeyword("超清")
          ?: findByKeyword("流畅")
          ?: findByKeyword("标清")
          ?: findByKeyword("普清")
          ?: variants.filter { it.rate != 0 }.minByOrNull { it.bit ?: Int.MAX_VALUE }?.rate
          ?: variants.filter { it.rate != 0 }.minOfOrNull { it.rate }
      }
      else -> findByKeyword(canonical)
    }
  }

  private data class DouyuRateVariant(
    val name: String,
    val rate: Int,
    val bit: Int?,
  )

  private data class InternalPlayInfo(
    val variants: List<DouyuRateVariant>,
    val cdns: List<String>,
  )

  private companion object {
    const val DEFAULT_DOUYU_CDN = "ws-h5"
    const val DEFAULT_DOUYU_DID = "10000000000000000000000000001501"
  }
}
