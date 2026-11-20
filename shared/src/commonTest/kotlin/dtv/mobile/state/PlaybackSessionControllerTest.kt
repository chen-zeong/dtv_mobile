package dtv.mobile.state

import dtv.mobile.model.Platform
import dtv.mobile.repo.DtvRepository
import dtv.mobile.repo.PlaybackLine
import dtv.mobile.repo.PlaybackOptions
import dtv.mobile.repo.PlaybackQuality
import dtv.mobile.repo.ResolvedPlayback
import dtv.mobile.repo.fake.FakeDtvRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlaybackSessionControllerTest {
  @Test
  fun normalizesStoredSelectionPerLine() = runBlocking {
    val repository = ControlledRepository()
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    val saved = mutableMapOf<String, Pair<String?, String?>>()
    val controller = PlaybackSessionController(
      repository = repository,
      scope = scope,
      preferenceProvider = { platform, roomId ->
        val value = saved["${platform.name}:$roomId"]
        PlaybackPreference(
          platform = platform,
          roomId = roomId,
          qualityId = value?.first,
          lineId = value?.second,
        )
      },
      preferenceSaver = { platform, roomId, qualityId, lineId ->
        saved["${platform.name}:$roomId"] = qualityId to lineId
      },
    )

    saved["Douyin:room"] = "HD1" to "flv"
    controller.open(Platform.Douyin, "room")
    val playing = awaitPhase(controller, PlaybackSessionPhase.Playing)

    assertEquals("flv", playing.selectedLineId)
    assertNull(playing.selectedQualityId)
    assertEquals("ORIGIN", playing.currentQualityId)
    assertEquals("flv", repository.lastLineId)
    assertNull(repository.lastQualityId)
    assertEquals(null to "flv", saved["Douyin:room"])
    scope.cancel()
  }

  @Test
  fun switchingLineKeepsQualityOnlyWhenSupported() = runBlocking {
    val repository = ControlledRepository()
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    val controller = controller(repository, scope)
    controller.open(Platform.Douyin, "room")
    awaitPhase(controller, PlaybackSessionPhase.Playing)
    controller.selectQuality("HD1")
    val withQuality = awaitPhase(controller, PlaybackSessionPhase.Playing)
    assertEquals("HD1", withQuality.selectedQualityId)

    controller.selectLine("flv")
    val switched = awaitPhase(controller, PlaybackSessionPhase.Playing)

    assertEquals("flv", switched.selectedLineId)
    assertNull(switched.selectedQualityId)
    assertEquals("ORIGIN", switched.currentQualityId)
    scope.cancel()
  }

  @Test
  fun failedSwitchKeepsCurrentPlayback() = runBlocking {
    val repository = ControlledRepository()
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    val controller = controller(repository, scope)
    controller.open(Platform.Douyin, "room")
    val initial = awaitPhase(controller, PlaybackSessionPhase.Playing)
    val originalUrl = initial.playback?.url

    repository.failResolve = true
    controller.selectQuality("HD1")
    val failed = awaitPhase(controller, PlaybackSessionPhase.Failed)

    assertEquals(originalUrl, failed.playback?.url)
    assertEquals(initial.currentLineId, failed.currentLineId)
    assertNotNull(failed.error)
    scope.cancel()
  }

  @Test
  fun lateResponseCannotOverwriteNewerRequest() = runBlocking {
    val repository = ControlledRepository()
    val scope = CoroutineScope(coroutineContext + SupervisorJob())
    val controller = controller(repository, scope)
    controller.open(Platform.Douyin, "room")
    awaitPhase(controller, PlaybackSessionPhase.Playing)

    repository.delayNextResolve()
    controller.selectLine("flv")
    repository.delayedResolveStarted.await()
    controller.retry()
    val newest = awaitPhase(controller, PlaybackSessionPhase.Playing)
    assertEquals("https://new.example/room", newest.playback?.url)

    repository.releaseDelayedResolve.complete(Unit)
    repeat(10) { yield() }
    assertEquals("https://new.example/room", controller.state.value.playback?.url)
    scope.cancel()
  }

  private suspend fun awaitPhase(
    controller: PlaybackSessionController,
    phase: PlaybackSessionPhase,
  ): PlaybackSessionState {
    return withTimeout(2_000L) {
      controller.state.first { it.phase == phase }
    }
  }

  private fun controller(
    repository: ControlledRepository,
    scope: CoroutineScope,
  ): PlaybackSessionController {
    return PlaybackSessionController(
      repository = repository,
      scope = scope,
      preferenceProvider = { platform, roomId -> PlaybackPreference(platform, roomId) },
      preferenceSaver = { _, _, _, _ -> },
    )
  }

  private class ControlledRepository : DtvRepository by FakeDtvRepository() {
    private val options = PlaybackOptions(
      lines = listOf(
        PlaybackLine(
          id = "hls",
          label = "HLS",
          qualities = listOf(
            PlaybackQuality("ORIGIN", "原画"),
            PlaybackQuality("HD1", "高清"),
          ),
        ),
        PlaybackLine(
          id = "flv",
          label = "FLV",
          qualities = listOf(
            PlaybackQuality("ORIGIN", "原画"),
          ),
        ),
      ),
      defaultLineId = "hls",
      defaultQualityId = "ORIGIN",
    )

    var failResolve = false
    var lastLineId: String? = null
    var lastQualityId: String? = null
    var delayedResolveStarted = CompletableDeferred<Unit>()
    var releaseDelayedResolve = CompletableDeferred<Unit>()
    private var delayNext = false
    private var resolveCount = 0

    override suspend fun fetchPlaybackOptions(platform: Platform, roomId: String): PlaybackOptions = options

    override suspend fun resolvePlayback(
      platform: Platform,
      roomId: String,
      qualityId: String?,
      lineId: String?,
    ): ResolvedPlayback {
      resolveCount += 1
      lastLineId = lineId
      lastQualityId = qualityId
      if (failResolve) error("switch failed")
      if (delayNext) {
        delayNext = false
        delayedResolveStarted.complete(Unit)
        withContext(NonCancellable) {
          releaseDelayedResolve.await()
        }
        return ResolvedPlayback(
          url = "https://stale.example/room",
          qualityId = qualityId ?: "ORIGIN",
          lineId = lineId ?: "hls",
        )
      }
      return ResolvedPlayback(
        url = if (resolveCount == 1) "https://initial.example/room" else "https://new.example/room",
        qualityId = qualityId ?: if (lineId == "flv") "ORIGIN" else "ORIGIN",
        lineId = lineId ?: "hls",
      )
    }

    fun delayNextResolve() {
      delayNext = true
    }
  }
}
