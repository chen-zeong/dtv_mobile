package dtv.mobile.platform.danmaku

import dtv.mobile.repo.DanmakuFailure
import dtv.mobile.repo.DanmakuProtocolConnection
import dtv.mobile.repo.DanmakuTermination
import kotlinx.coroutines.CompletableDeferred
import okhttp3.Response

internal class DeferredDanmakuConnection(
  private val termination: CompletableDeferred<DanmakuTermination>,
  private val closeAction: () -> Unit,
) : DanmakuProtocolConnection {
  override suspend fun awaitTermination(): DanmakuTermination = termination.await()

  override fun close() {
    runCatching(closeAction)
    if (!termination.isCompleted) {
      termination.complete(DanmakuTermination(dtv.mobile.repo.DanmakuTerminationKind.Closed))
    }
  }
}
internal fun danmakuFailureForHttp(code: Int?): DanmakuFailure {
  return when {
    code == null -> DanmakuFailure.Network
    code == 401 || code == 403 -> DanmakuFailure.Authentication
    code == 404 || code == 410 -> DanmakuFailure.RoomUnavailable
    code == 429 -> DanmakuFailure.RateLimited
    code >= 500 -> DanmakuFailure.Http
    else -> DanmakuFailure.Http
  }
}

internal fun okHttpTermination(
  response: Response?,
  message: String?,
): DanmakuTermination {
  return DanmakuTermination(
    kind = dtv.mobile.repo.DanmakuTerminationKind.Failed,
    failure = danmakuFailureForHttp(response?.code),
    detail = message,
  )
}
