package dtv.mobile.platform.bilibili

import android.content.Context

class BilibiliCookieStoreAndroid(
  appContext: Context,
) {
  private val prefs = appContext.getSharedPreferences("dtv_bilibili", Context.MODE_PRIVATE)

  fun getCookie(): String? = prefs.getString("cookie", null)?.takeIf { it.isNotBlank() }

  fun mergeFromCookieHeader(cookieHeader: String) {
    if (cookieHeader.isBlank()) return
    val existing = parseCookieHeader(getCookie().orEmpty())
    val updated = existing.toMutableMap()
    parseCookieHeader(cookieHeader).forEach { (k, v) ->
      if (k.isNotBlank() && v.isNotBlank()) updated[k] = v
    }
    val cookie = updated.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    if (cookie.isNotBlank()) prefs.edit().putString("cookie", cookie).apply()
  }

  fun clear() {
    prefs.edit().remove("cookie").apply()
  }

  fun mergeFromSetCookieHeaders(setCookie: List<String>) {
    if (setCookie.isEmpty()) return
    val existing = parseCookieHeader(getCookie().orEmpty())
    val updated = existing.toMutableMap()
    setCookie.forEach { header ->
      val pair = header.substringBefore(';').trim()
      val idx = pair.indexOf('=')
      if (idx <= 0) return@forEach
      val k = pair.substring(0, idx).trim()
      val v = pair.substring(idx + 1).trim()
      if (k.isNotEmpty() && v.isNotEmpty()) updated[k] = v
    }
    val cookie = updated.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    if (cookie.isNotBlank()) prefs.edit().putString("cookie", cookie).apply()
  }

  private fun parseCookieHeader(header: String): Map<String, String> {
    if (header.isBlank()) return emptyMap()
    return header.split(';')
      .mapNotNull { it.trim() }
      .mapNotNull { kv ->
        val idx = kv.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        kv.substring(0, idx).trim() to kv.substring(idx + 1).trim()
      }.toMap()
  }
}
