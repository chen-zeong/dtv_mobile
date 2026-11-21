package dtv.mobile.platform.douyin

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

class DouyinCookieManagerTest {
  @Test
  fun concurrentRequestsShareOneRefresh() = runBlocking {
    val calls = AtomicInteger()
    val store = DouyinCookieManager(
      persistence = MemoryPersistence(),
      fallbackCookie = "ttwid=fallback",
      fetcher = {
        calls.incrementAndGet()
        "ttwid=fresh"
      },
    )

    val values = (1..8).map { async { store.getCookie() } }.awaitAll()

    assertEquals(1, calls.get())
    assertEquals(List(8) { "ttwid=fresh" }, values)
  }

  @Test
  fun failedRefreshUsesCachedCookieAndDoesNotExtendItsAge() = runBlocking {
    var now = 1_000L
    var fetcherFails = false
    val store = DouyinCookieManager(
      persistence = MemoryPersistence(),
      fallbackCookie = "ttwid=fallback",
      fetcher = { if (fetcherFails) error("no webview") else "ttwid=cached" },
      nowMs = { now },
    )

    assertEquals("ttwid=cached", store.getCookie())
    assertEquals("ttwid=cached", store.getCookie())
    fetcherFails = true
    now += 6 * 60 * 60 * 1000L + 1

    assertEquals("ttwid=cached", store.getCookie())
    assertEquals("ttwid=cached", store.getCookie())
  }

  @Test
  fun setCookieIsMergedWithoutDroppingExistingKeys() = runBlocking {
    val store = DouyinCookieManager(
      persistence = MemoryPersistence(),
      fallbackCookie = "ttwid=fallback",
      fetcher = { "ttwid=base; sid=old" },
    )

    assertEquals("ttwid=base; sid=old", store.getCookie())
    store.updateFromSetCookie("sid=new; Path=/; HttpOnly")
    assertEquals("ttwid=base; sid=new", store.getCookie())
  }

  private class MemoryPersistence : DouyinCookiePersistence {
    private var cookie: String? = null
    private var fetchedAt: Long = 0L

    override fun read(): String? = cookie

    override fun readFetchedAt(): Long = fetchedAt

    override fun write(cookie: String, fetchedAtMs: Long) {
      this.cookie = cookie
      this.fetchedAt = fetchedAtMs
    }
  }
}
