package dtv.mobile.util

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but never converts coroutine cancellation into a normal failure.
 *
 * Network and UI code often treats failures as empty data. Swallowing cancellation in those
 * paths lets a cancelled request publish stale results into the next screen state.
 */
internal inline fun <T> runSuspendCatching(block: () -> T): Result<T> {
  return try {
    Result.success(block())
  } catch (t: CancellationException) {
    throw t
  } catch (t: Throwable) {
    Result.failure(t)
  }
}
