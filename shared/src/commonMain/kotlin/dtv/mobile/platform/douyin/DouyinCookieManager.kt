package dtv.mobile.platform.douyin

import dtv.mobile.util.runSuspendCatching
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface DouyinCookiePersistence {
  fun read(): String?
  fun readFetchedAt(): Long
  fun write(cookie: String, fetchedAtMs: Long)
}

internal class DouyinCookieManager(
  private val persistence: DouyinCookiePersistence,
  private val fallbackCookie: String,
  private val fetcher: suspend () -> String?,
  private val nowMs: () -> Long = System::currentTimeMillis,
  private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
) {
  companion object {
    const val DEFAULT_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
  }

  private val refreshMutex = Mutex()

  @Volatile
  private var cachedCookie: String? = persistence.read()?.takeIf(::hasTtwid)

  @Volatile
  private var fetchedAtMs: Long = 0L

  @Volatile
  private var refreshGeneration: Long = 0L

  init {
    fetchedAtMs = persistence.readFetchedAt()
  }

  suspend fun getCookie(forceRefresh: Boolean = false): String = refreshMutex.withLock {
    val generationAtEntry = refreshGeneration
    val now = nowMs()
    val cached = cachedCookie?.takeIf(::hasTtwid)
    if (!forceRefresh && cached != null && now - fetchedAtMs in 0 until cacheTtlMs) {
      return@withLock cached
    }
    // A concurrent caller may have refreshed while this caller waited for the mutex.
    if (forceRefresh && cached != null && refreshGeneration != generationAtEntry) {
      return@withLock cached
    }

    val fresh = runSuspendCatching { fetcher() }.getOrNull()?.takeIf(::hasTtwid)
    if (fresh != null) {
      cachedCookie = fresh
      fetchedAtMs = now
      refreshGeneration++
      persistence.write(fresh, now)
      return@withLock fresh
    }

    cached ?: fallbackCookie
  }

  fun updateFromSetCookie(setCookie: String?) {
    val incoming = setCookie?.substringBefore(';')?.trim().orEmpty()
    if (incoming.isBlank()) return
    val merged = mergeCookies(cachedCookie.orEmpty(), incoming)
    if (!hasTtwid(merged)) return
    val now = nowMs()
    cachedCookie = merged
    fetchedAtMs = now
    refreshGeneration++
    persistence.write(merged, now)
  }

  private fun mergeCookies(existing: String, incoming: String): String {
    val values = linkedMapOf<String, String>()
    fun addAll(header: String) {
      header.split(';').forEach { segment ->
        val index = segment.indexOf('=')
        if (index <= 0) return@forEach
        val key = segment.substring(0, index).trim()
        if (key.isNotBlank()) values[key] = segment.substring(index + 1).trim()
      }
    }
    addAll(existing)
    addAll(incoming)
    return values.entries.joinToString("; ") { (key, value) -> "$key=$value" }
  }

  private fun hasTtwid(cookie: String): Boolean =
    cookie.split(';').any {
      it.trim().startsWith("ttwid=") && it.substringAfter('=', "").isNotBlank()
    }
}
