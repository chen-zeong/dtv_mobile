package dtv.mobile.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dtv.mobile.model.Platform
import dtv.mobile.repo.DanmakuPhase
import dtv.mobile.repo.android.AndroidDtvRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DanmakuLiveSoakTest {
  @Test
  fun fourPlatformsRemainUsableForConfiguredDuration() = runBlocking {
    val args = InstrumentationRegistry.getArguments()
    val minutes = args.getString("danmakuMinutes")?.toLongOrNull()?.coerceIn(1L, 180L) ?: 30L
    val requestedPlatforms = args.getString("danmakuPlatforms")
      ?.split(',')
      ?.mapNotNull { value -> Platform.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } }
      ?.toSet()
      ?: setOf(Platform.Douyu, Platform.Bilibili, Platform.Huya, Platform.Douyin)

    val configured = requestedPlatforms.mapNotNull { platform ->
      args.getString("danmakuRoom${platform.name}")?.trim()?.takeIf { it.isNotEmpty() }?.let { platform to it }
    }
    assumeTrue(
      "Provide danmakuRoom<Platform> instrumentation arguments for a live soak run.",
      configured.isNotEmpty(),
    )

    val repository = AndroidDtvRepository(
      InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
    )

    for ((platform, roomId) in configured) {
      val summary = collectFor(
        repository = repository,
        platform = platform,
        roomId = roomId,
        durationMs = TimeUnit.MINUTES.toMillis(minutes),
      )
      println(summary.toLine())
      assertTrue("$platform never reached Ready", summary.reachedReady)
      assertTrue("$platform received no chat messages during soak", summary.messages > 0)
    }
  }

  private suspend fun collectFor(
    repository: AndroidDtvRepository,
    platform: Platform,
    roomId: String,
    durationMs: Long,
  ): SoakSummary {
    var ready = false
    var messages = 0
    var reconnects = 0
    var degraded = 0
    var lastPhase: DanmakuPhase? = null
    try {
      withTimeout(durationMs) {
        repository.observeDanmaku(platform, roomId).collect { event ->
          ready = ready || event.phase == DanmakuPhase.Ready
          messages += if (event.message != null) 1 else 0
          reconnects += if (event.phase == DanmakuPhase.Reconnecting) 1 else 0
          degraded += if (event.phase == DanmakuPhase.Degraded) 1 else 0
          lastPhase = event.phase
        }
      }
    } catch (_: TimeoutCancellationException) {
      // Expected end of the bounded soak window.
    }
    return SoakSummary(
      platform = platform,
      roomId = roomId,
      durationMs = durationMs,
      reachedReady = ready,
      messages = messages,
      reconnects = reconnects,
      degraded = degraded,
      lastPhase = lastPhase,
    )
  }

  private data class SoakSummary(
    val platform: Platform,
    val roomId: String,
    val durationMs: Long,
    val reachedReady: Boolean,
    val messages: Int,
    val reconnects: Int,
    val degraded: Int,
    val lastPhase: DanmakuPhase?,
  ) {
    fun toLine(): String {
      return "DANMAKU_SOAK platform=$platform room=$roomId durationMs=$durationMs " +
        "ready=$reachedReady messages=$messages reconnects=$reconnects degraded=$degraded lastPhase=$lastPhase"
    }
  }
}
