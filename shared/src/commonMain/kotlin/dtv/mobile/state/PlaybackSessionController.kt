package dtv.mobile.state

import dtv.mobile.model.Platform
import dtv.mobile.repo.DtvRepository
import dtv.mobile.repo.PlaybackOptions
import dtv.mobile.repo.PlaybackSelectionResolver
import dtv.mobile.repo.ResolvedPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaybackSessionPhase {
  Idle,
  Loading,
  Playing,
  Switching,
  Failed,
}
data class PlaybackSessionState(
  val phase: PlaybackSessionPhase = PlaybackSessionPhase.Idle,
  val options: PlaybackOptions = PlaybackOptions(),
  val selectedLineId: String? = null,
  val selectedQualityId: String? = null,
  val currentLineId: String? = null,
  val currentQualityId: String? = null,
  val playback: ResolvedPlayback? = null,
  val error: String? = null,
) {
  val isBusy: Boolean
    get() = phase == PlaybackSessionPhase.Loading || phase == PlaybackSessionPhase.Switching

  val isReady: Boolean
    get() = phase == PlaybackSessionPhase.Playing && playback != null
}

class PlaybackSessionController(
  private val repository: DtvRepository,
  private val scope: CoroutineScope,
  private val preferenceProvider: (Platform, String) -> PlaybackPreference,
  private val preferenceSaver: (Platform, String, String?, String?) -> Unit,
) {
  private val mutableState = MutableStateFlow(PlaybackSessionState())
  val state: StateFlow<PlaybackSessionState> = mutableState.asStateFlow()

  private var platform: Platform? = null
  private var roomId: String? = null
  private var requestGeneration = 0L
  private var activeJob: Job? = null

  fun open(platform: Platform, roomId: String) {
    val normalizedRoomId = roomId.trim()
    if (normalizedRoomId.isEmpty()) {
      close()
      mutableState.value = PlaybackSessionState(
        phase = PlaybackSessionPhase.Failed,
        error = "直播间编号为空",
      )
      return
    }
    this.platform = platform
    this.roomId = normalizedRoomId
    val preference = preferenceProvider(platform, normalizedRoomId)
    mutableState.value = PlaybackSessionState(
      phase = PlaybackSessionPhase.Loading,
      selectedLineId = preference.lineId,
      selectedQualityId = preference.qualityId,
    )
    startRequest(
      platform = platform,
      roomId = normalizedRoomId,
      selectedLineId = preference.lineId,
      selectedQualityId = preference.qualityId,
      refreshOptions = true,
    )
  }

  fun retry() {
    val currentPlatform = platform ?: return
    val currentRoomId = roomId ?: return
    val current = mutableState.value
    startRequest(
      platform = currentPlatform,
      roomId = currentRoomId,
      selectedLineId = current.selectedLineId,
      selectedQualityId = current.selectedQualityId,
      refreshOptions = true,
    )
  }

  fun selectLine(lineId: String?) {
    if (mutableState.value.isBusy) return
    val current = mutableState.value
    if (lineId != null && PlaybackSelectionResolver.line(current.options, lineId) == null) {
      failWithoutClearingPlayback("当前直播不提供所选线路")
      return
    }
    val targetLine = PlaybackSelectionResolver.effectiveLine(current.options, lineId) ?: run {
      failWithoutClearingPlayback("当前直播没有可用线路")
      return
    }
    val targetQuality = current.selectedQualityId
      ?.let { PlaybackSelectionResolver.quality(targetLine, it)?.id }
    val currentPlatform = platform ?: return
    val currentRoomId = roomId ?: return
    startRequest(
      platform = currentPlatform,
      roomId = currentRoomId,
      selectedLineId = lineId,
      selectedQualityId = targetQuality,
      refreshOptions = false,
    )
  }

  fun selectQuality(qualityId: String?) {
    if (mutableState.value.isBusy) return
    val current = mutableState.value
    val line = PlaybackSelectionResolver.effectiveLine(current.options, current.selectedLineId)
      ?: run {
        failWithoutClearingPlayback("当前直播没有可用线路")
        return
      }
    val canonicalQualityId = qualityId
      ?.let { PlaybackSelectionResolver.quality(line, it)?.id }
    if (qualityId != null && canonicalQualityId == null) {
      failWithoutClearingPlayback("当前线路不提供所选画质")
      return
    }
    val currentPlatform = platform ?: return
    val currentRoomId = roomId ?: return
    startRequest(
      platform = currentPlatform,
      roomId = currentRoomId,
      selectedLineId = current.selectedLineId,
      selectedQualityId = canonicalQualityId,
      refreshOptions = false,
    )
  }

  fun close() {
    requestGeneration += 1
    activeJob?.cancel()
    activeJob = null
  }

  fun clear() {
    close()
    platform = null
    roomId = null
    mutableState.value = PlaybackSessionState()
  }

  fun replacePlaybackUrl(url: String) {
    mutableState.update { current ->
      val playback = current.playback ?: return@update current
      current.copy(
        phase = PlaybackSessionPhase.Playing,
        playback = playback.copy(url = url),
        error = null,
      )
    }
  }

  fun reportPlayerFailure(message: String) {
    requestGeneration += 1
    activeJob?.cancel()
    mutableState.update {
      it.copy(
        phase = PlaybackSessionPhase.Failed,
        playback = null,
        error = message,
      )
    }
  }

  private fun startRequest(
    platform: Platform,
    roomId: String,
    selectedLineId: String?,
    selectedQualityId: String?,
    refreshOptions: Boolean,
  ) {
    requestGeneration += 1
    val generation = requestGeneration
    activeJob?.cancel()
    val before = mutableState.value
    mutableState.value = before.copy(
      phase = if (before.playback == null) PlaybackSessionPhase.Loading else PlaybackSessionPhase.Switching,
      selectedLineId = selectedLineId,
      selectedQualityId = selectedQualityId,
      error = null,
    )

    activeJob = scope.launch {
      try {
        val options = if (refreshOptions || before.options.lines.isEmpty()) {
          repository.fetchPlaybackOptions(platform, roomId)
        } else {
          before.options
        }
        if (generation != requestGeneration) return@launch

        val normalized = PlaybackSelectionResolver.normalize(
          options = options,
          preferredQualityId = selectedQualityId,
          preferredLineId = selectedLineId,
        )
        val resolved = repository.resolvePlayback(
          platform = platform,
          roomId = roomId,
          qualityId = normalized.qualityId,
          lineId = normalized.lineId,
        )
        if (generation != requestGeneration) return@launch

        val effectiveLine = PlaybackSelectionResolver.effectiveLine(options, normalized.lineId)
        mutableState.value = PlaybackSessionState(
          phase = PlaybackSessionPhase.Playing,
          options = options,
          selectedLineId = normalized.lineId,
          selectedQualityId = normalized.qualityId,
          currentLineId = resolved.lineId ?: effectiveLine?.id,
          currentQualityId = resolved.qualityId,
          playback = resolved,
        )
        preferenceSaver(
          platform,
          roomId,
          normalized.qualityId,
          normalized.lineId,
        )
      } catch (t: CancellationException) {
        throw t
      } catch (t: Throwable) {
        if (generation != requestGeneration) return@launch
        mutableState.value = before.copy(
          phase = PlaybackSessionPhase.Failed,
          error = t.message ?: "播放地址获取失败",
        )
      }
    }
  }

  private fun failWithoutClearingPlayback(message: String) {
    mutableState.update {
      it.copy(
        phase = PlaybackSessionPhase.Failed,
        error = message,
      )
    }
  }
}
