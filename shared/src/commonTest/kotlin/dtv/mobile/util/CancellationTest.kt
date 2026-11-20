package dtv.mobile.util

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CancellationTest {
  @Test
  fun rethrowsCancellationException() {
    val cancellation = CancellationException("cancelled")

    val thrown = assertFailsWith<CancellationException> {
      runSuspendCatching<Unit> { throw cancellation }
    }

    assertSame(cancellation, thrown)
  }

  @Test
  fun capturesRegularFailures() {
    val result = runSuspendCatching<Unit> { error("network failed") }

    assertTrue(result.isFailure)
  }
}
