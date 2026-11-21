package dtv.mobile.platform.bilibili

import dtv.mobile.repo.PlaybackLine
import dtv.mobile.repo.PlaybackOptions
import dtv.mobile.repo.PlaybackQuality
import dtv.mobile.repo.PlaybackSelectionResolver
import dtv.mobile.repo.ResolvedPlayback
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
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

class BilibiliStreamUrlResolverAndroid(
  private val client: HttpClient,
  private val cookieProvider: () -> String?,
) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private fun JsonElement?.stringValueOrNull(): String? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
  }

  private fun JsonElement?.longValueOrNull(): Long? {
    return (this as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
  }

  private data class Candidate(
    val baseUrl: String,
    val host: String,
    val extra: String,
    val lineId: String,
    val lineLabel: String,
    val currentQualityId: String?,
    val qualities: List<PlaybackQuality>,
  )

  private data class PlayData(
    val options: PlaybackOptions,
    val candidates: List<Candidate>,
    val actualQualityId: String?,
  )

  private suspend fun fetchPlayData(roomId: String, qn: Int?): PlayData {
    val quality = qn ?: 10000
    val url =
      "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id=$roomId&protocol=0,1&format=0,1,2&codec=0,1&qn=$quality&platform=web"
    val text = client.get(url) {
      headers {
        append("User-Agent", "Mozilla/5.0")
        append("Referer", "https://live.bilibili.com/")
        val cookie = cookieProvider()?.trim().orEmpty()
        if (cookie.isNotEmpty()) append("Cookie", cookie)
      }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val code = root["code"].longValueOrNull() ?: -1L
    if (code != 0L) error("B站播放地址获取失败: code=$code")

    val playurl = root["data"]?.jsonObject
      ?.get("playurl_info")?.jsonObject
      ?.get("playurl")?.jsonObject
    val streams =
      playurl?.get("stream")?.jsonArray
        ?: error("B站播放数据为空")
    val qualityLabels = playurl.get("g_qn_desc")?.jsonArray.orEmpty().mapNotNull { item ->
      val obj = item.jsonObject
      val id = obj["qn"].longValueOrNull()?.toString() ?: return@mapNotNull null
      val label = obj["desc"].stringValueOrNull()?.trim().orEmpty()
      if (label.isBlank()) null else id to label
    }.toMap()

    fun candidatesOfStream(stream: JsonObject): List<Candidate> {
      val formats = stream["format"]?.jsonArray ?: return emptyList()
      return formats.flatMap { f ->
        val formatObj = f.jsonObject
        val formatName = formatObj["format_name"].stringValueOrNull().orEmpty()
        val codecs = formatObj["codec"]?.jsonArray ?: return@flatMap emptyList()
        codecs.mapNotNull { c ->
          val codecObj = c.jsonObject
          val codecName = codecObj["codec_name"].stringValueOrNull().orEmpty()
          val baseUrl = codecObj["base_url"].stringValueOrNull()?.trim().orEmpty()
          if (baseUrl.isBlank()) return@mapNotNull null
          val urlInfo0 = codecObj["url_info"]?.jsonArray?.firstOrNull()?.jsonObject
          val host = urlInfo0?.get("host").stringValueOrNull()?.trim().orEmpty()
          val extra = urlInfo0?.get("extra").stringValueOrNull()?.trim().orEmpty()
          if (host.isBlank()) return@mapNotNull null
          val acceptedQualityIds = codecObj["accept_qn"]?.jsonArray.orEmpty()
            .mapNotNull { it.longValueOrNull()?.toString() }
            .distinct()
          val acceptedQualities = acceptedQualityIds.mapNotNull { id ->
            qualityLabels[id]?.let { label -> PlaybackQuality(id = id, label = label) }
          }
          val currentQualityId = codecObj["current_qn"].longValueOrNull()?.toString()
          val protocol = if (baseUrl.contains(".m3u8", ignoreCase = true) || formatName.contains("ts", true)) "HLS" else "FLV"
          val hostLabel = friendlyHost(host)
          Candidate(
            baseUrl = baseUrl,
            host = host,
            extra = extra,
            lineId = "$protocol|$formatName|$codecName|$host",
            lineLabel = "$protocol · $hostLabel",
            currentQualityId = currentQualityId,
            qualities = acceptedQualities,
          )
        }
      }
    }

    val all = streams.flatMap { candidatesOfStream(it.jsonObject) }
    if (all.isEmpty()) error("B站未解析到播放地址")
    val lines = all.map {
      PlaybackLine(id = it.lineId, label = it.lineLabel, qualities = it.qualities)
    }.distinctBy { it.id }
    val defaultCandidate = all.minByOrNull { score(it) }
    return PlayData(
      options = PlaybackOptions(
        lines = lines,
        defaultLineId = defaultCandidate?.lineId,
        defaultQualityId = defaultCandidate?.currentQualityId,
      ),
      candidates = all,
      actualQualityId = defaultCandidate?.currentQualityId ?: qn?.toString(),
    )
  }

  suspend fun fetchOptions(roomId: String): PlaybackOptions = fetchPlayData(roomId, null).options

  suspend fun resolveSelected(roomId: String, qualityId: String?, lineId: String?): ResolvedPlayback {
    val data = fetchPlayData(roomId, qualityId?.toIntOrNull())
    val selected = if (lineId != null) {
      data.candidates.firstOrNull { it.lineId.equals(lineId, ignoreCase = true) }
        ?: error("当前直播不提供所选线路")
    } else {
      data.candidates
        .asSequence()
        .filter { candidate ->
          qualityId == null || candidate.qualities.any { it.id.equals(qualityId, ignoreCase = true) }
        }
        .minByOrNull { score(it) }
        ?: error(if (qualityId == null) "B站未找到可用播放地址" else "当前直播不提供所选画质")
    }
    if (qualityId != null) {
      PlaybackSelectionResolver.exactQualityOrThrow(
        line = PlaybackLine(
          id = selected.lineId,
          label = selected.lineLabel,
          qualities = selected.qualities,
        ),
        qualityId = qualityId,
      )
      val actualQualityId = selected.currentQualityId
      if (actualQualityId != null && !actualQualityId.equals(qualityId, ignoreCase = true)) {
        error("平台实际返回画质 $actualQualityId，与所选画质 $qualityId 不一致")
      }
    }
    return ResolvedPlayback(
      url = buildUrl(selected),
      qualityId = selected.currentQualityId ?: qualityId ?: data.actualQualityId,
      lineId = selected.lineId,
    )
  }

  suspend fun resolve(roomId: String, qn: Int? = null): String = resolveSelected(roomId, qn?.toString(), null).url

  private fun score(c: Candidate): Int {
    val b = c.baseUrl.lowercase()
    return when {
      b.contains("index.m3u8") -> 0
      b.contains(".m3u8") -> 1
      b.contains(".flv") -> 2
      else -> 9
    }
  }

  private fun buildUrl(best: Candidate): String {
    val extraPart = best.extra.trimStart('?', '&')
    val base = best.baseUrl
    val joiner = when {
      extraPart.isEmpty() -> ""
      base.endsWith("?") || base.endsWith("&") -> ""
      base.contains("?") -> "&"
      else -> "?"
    }
    return buildString {
      append(best.host.trimEnd('/'))
      append(base)
      if (extraPart.isNotEmpty()) {
        append(joiner)
        append(extraPart)
      }
    }
  }

  private fun friendlyHost(host: String): String {
    val normalized = host.lowercase()
      .removePrefix("https://")
      .removePrefix("http://")
      .substringBefore('/')
    return when {
      "hdslb.com" in normalized -> "B站"
      "biliapi" in normalized -> "B站"
      "szbdyd" in normalized -> "百度"
      "mcdn" in normalized -> "移动"
      else -> normalized
    }
  }
}
