package dtv.mobile.platform.huya

import dtv.mobile.model.Streamer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText

class HuyaLiveListApiAndroid(
  private val client: HttpClient,
) {
  suspend fun fetchLiveList(gid: String, page: Int, pageSize: Int): List<Streamer> {
    val url = "https://live.huya.com/liveHttpUI/getLiveList?iGid=$gid&iPageNo=$page&iPageSize=$pageSize"
    val text = client.get(url) {
      headers { append("User-Agent", "Mozilla/5.0") }
    }.bodyAsText()

    return HuyaResponseParsers.parseLiveListResponse(text)
  }
}
