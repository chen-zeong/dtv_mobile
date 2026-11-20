package dtv.mobile.platform.huya

import dtv.mobile.model.Streamer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText

class HuyaSearchApiAndroid(
  private val client: HttpClient,
) {
  suspend fun searchAnchors(keyword: String, page: Int = 1): List<Streamer> {
    val trimmed = keyword.trim()
    if (trimmed.isEmpty()) return emptyList()

    val start = ((page - 1).coerceAtLeast(0) * 20).toString()
    val text = client.get("https://search.cdn.huya.com/") {
      parameter("m", "Search")
      parameter("do", "getSearchContent")
      parameter("q", trimmed)
      parameter("uid", "0")
      parameter("v", "1")
      parameter("typ", "-5")
      parameter("livestate", "0")
      parameter("rows", "20")
      parameter("start", start)
      headers {
        append("User-Agent", "Mozilla/5.0")
        append("Referer", "https://www.huya.com/search/")
        append("Origin", "https://www.huya.com")
        append("Accept", "*/*")
      }
    }.bodyAsText()

    return HuyaResponseParsers.parseSearchResponse(text)
  }
}
