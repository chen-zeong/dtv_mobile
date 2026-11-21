package dtv.mobile.platform.douyu

import dtv.mobile.model.Streamer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.security.MessageDigest
import kotlin.random.Random

class DouyuSearchApiAndroid(
  private val client: HttpClient,
) {
  private fun md5Hex(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return bytes.joinToString("") { b -> "%02x".format(b) }
  }

  suspend fun searchAnchors(keyword: String): List<Streamer> {
    val trimmed = keyword.trim()
    if (trimmed.isEmpty()) return emptyList()

    val didSeed = "${System.nanoTime()}-${Random.nextInt()}"
    val did = md5Hex(didSeed)

    val text = client.get("https://www.douyu.com/japi/search/api/searchUser") {
      parameter("kw", trimmed)
      parameter("page", "1")
      parameter("pageSize", "20")
      parameter("filterType", "0")
      headers {
        append("Referer", "https://www.douyu.com/search/")
        append("Cookie", "dy_did=$did; acf_did=$did")
      }
    }.bodyAsText()

    return DouyuResponseParsers.parseSearchResponse(text)
  }
}
