package dtv.mobile.platform.bilibili

import dtv.mobile.model.Streamer
import dtv.mobile.util.runSuspendCatching
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

class BilibiliLiveListApiAndroid(
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

  private suspend fun fetchAreaList(parentAreaId: Int, areaId: Int, page: Int, pageSize: Int): List<JsonObject> {
    val text = client.get("https://api.live.bilibili.com/room/v1/Area/getRoomList") {
      headers {
        append("User-Agent", "Mozilla/5.0")
        append("Referer", "https://live.bilibili.com/")
      }
      parameter("platform", "web")
      parameter("parent_area_id", parentAreaId)
      parameter("area_id", areaId)
      parameter("sort_type", "online")
      parameter("page", page)
      parameter("page_size", pageSize)
    }.bodyAsText()

    return BilibiliLiveListParser.parseAreaResponse(text)
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
    val rooms: List<JsonObject> = runSuspendCatching {
      // /second/getList is currently rejected with -352 unless it carries a browser session signature.
      // The legacy area endpoint still provides the same filtered rooms without that anti-bot dependency.
      if (parentAreaId == 0 && areaId == 0) {
        fetchHotIndexList(page = page, pageSize = pageSize)
      } else {
        fetchAreaList(
          parentAreaId = parentAreaId,
          areaId = areaId,
          page = page,
          pageSize = pageSize,
        )
      }
    }.getOrElse { emptyList() }

    return rooms.mapNotNull(BilibiliLiveListParser::mapRoom)
  }
}
