package dtv.mobile.repo

import dtv.mobile.model.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DanmakuConnectionSupervisorTest {
  @Test
  fun reportsDegradedAfterThreeStartupFailures() = runBlocking {
    val now = FakeClock()
    val connector = object : DanmakuProtocolConnector {
      override val watchdogTimeoutMs: Long = 90_000L
      var attempts = 0

      override suspend fun connect(
        roomId: String,
        sink: DanmakuProtocolSink,
      ): DanmakuProtocolConnection {
        attempts += 1
        throw DanmakuConnectionException(DanmakuFailure.Authentication, "expired")
      }
    }
    val supervisor = DanmakuConnectionSupervisor(
      config = testConfig(),
      now = now::current,
      sleeper = { yield() },
      random = Random(1),
    )

    val events = supervisor.observe(Platform.Bilibili, "room", connector)
      .take(6)
      .toList()

    assertEquals(DanmakuPhase.Connecting, events.first().phase)
    assertTrue(events.any { it.phase == DanmakuPhase.Degraded })
    assertTrue(connector.attempts >= 3)
    assertEquals(DanmakuFailure.Authentication, events.first { it.phase == DanmakuPhase.Degraded }.failure)
  }

  @Test
  fun resetsBackoffAfterStableSession() = runBlocking {
    val clock = FakeClock()
    val connector = object : DanmakuProtocolConnector {
      override val watchdogTimeoutMs: Long = 90_000L
      var attempts = 0

      override suspend fun connect(
        roomId: String,
        sink: DanmakuProtocolSink,
      ): DanmakuProtocolConnection {
        attempts += 1
        sink.onAuthenticating()
        return object : DanmakuProtocolConnection {
          private val closed = CompletableDeferred<DanmakuTermination>()

          override suspend fun awaitTermination(): DanmakuTermination {
            clock.advance(61_000L)
            return DanmakuTermination(DanmakuTerminationKind.Closed)
          }

          override fun close() {
            closed.complete(DanmakuTermination(DanmakuTerminationKind.Closed))
          }
        }
      }
    }
    val supervisor = DanmakuConnectionSupervisor(
      config = testConfig(),
      now = clock::current,
      sleeper = { yield() },
      random = Random(1),
    )

    val events = supervisor.observe(Platform.Douyu, "room", connector)
      .take(5)
      .toList()

    val phases = events.map { it.phase }
    assertTrue(phases.contains(DanmakuPhase.Authenticating))
    assertTrue(phases.contains(DanmakuPhase.Ready))
    val reconnectEvents = events.filter { it.phase == DanmakuPhase.Reconnecting }
    assertTrue(reconnectEvents.isNotEmpty())
    assertEquals(1, reconnectEvents.last().attempt)
  }

  @Test
  fun emitsReadyAndChatEventsInSequence() = runBlocking {
    val clock = FakeClock()
    val connector = object : DanmakuProtocolConnector {
      override val watchdogTimeoutMs: Long = 90_000L

      override suspend fun connect(
        roomId: String,
        sink: DanmakuProtocolSink,
      ): DanmakuProtocolConnection {
        sink.onAuthenticating()
        val termination = CompletableDeferred<DanmakuTermination>()
        var emitted = false
        return object : DanmakuProtocolConnection {
          override suspend fun awaitTermination(): DanmakuTermination {
            if (!emitted) {
              emitted = true
              sink.onMessage(DanmakuMessage(roomId = roomId, user = "u", content = "hello"))
            }
            return termination.await()
          }

          override fun close() {
            termination.complete(DanmakuTermination(DanmakuTerminationKind.Closed))
          }
        }
      }
    }
    val supervisor = DanmakuConnectionSupervisor(
      config = testConfig(),
      now = clock::current,
      sleeper = { yield() },
      random = Random(1),
    )

    val events = supervisor.observe(Platform.Huya, "room", connector)
      .take(4)
      .toList()

    assertEquals(
      listOf(
        DanmakuPhase.Connecting,
        DanmakuPhase.Authenticating,
        DanmakuPhase.Ready,
        DanmakuPhase.Ready,
      ),
      events.map { it.phase },
    )
    assertEquals("hello", events.last().message?.content)
  }

  @Test
  fun reconnectsWhenInboundTrafficGoesSilent() = runBlocking {
    val clock = FakeClock()
    val connector = object : DanmakuProtocolConnector {
      override val watchdogTimeoutMs: Long = 90_000L

      override suspend fun connect(
        roomId: String,
        sink: DanmakuProtocolSink,
      ): DanmakuProtocolConnection {
        sink.onAuthenticating()
        return NeverEndingConnection()
      }
    }
    val supervisor = DanmakuConnectionSupervisor(
      config = testConfig(),
      now = clock::current,
      sleeper = {
        clock.advance(3_000L)
        yield()
      },
      random = Random(1),
    )

    val events = supervisor.observe(Platform.Douyin, "room", connector)
      .take(4)
      .toList()

    val reconnect = events.last()
    assertEquals(DanmakuPhase.Reconnecting, reconnect.phase)
    assertEquals(DanmakuFailure.Protocol, reconnect.failure)
  }

  @Test
  fun reportsDegradedAfterRepeatedProtocolErrors() = runBlocking {
    val clock = FakeClock()
    val connector = object : DanmakuProtocolConnector {
      override val watchdogTimeoutMs: Long = 90_000L

      override suspend fun connect(
        roomId: String,
        sink: DanmakuProtocolSink,
      ): DanmakuProtocolConnection {
        sink.onAuthenticating()
        return object : DanmakuProtocolConnection {
          private val termination = CompletableDeferred<DanmakuTermination>()

          override suspend fun awaitTermination(): DanmakuTermination {
            repeat(5) { sink.onProtocolError() }
            return termination.await()
          }

          override fun close() {
            termination.complete(DanmakuTermination(DanmakuTerminationKind.Closed))
          }
        }
      }
    }
    val supervisor = DanmakuConnectionSupervisor(
      config = testConfig(),
      now = clock::current,
      sleeper = { yield() },
      random = Random(1),
    )

    val events = supervisor.observe(Platform.Bilibili, "room", connector)
      .take(4)
      .toList()

    val degraded = events.last()
    assertEquals(DanmakuPhase.Degraded, degraded.phase)
    assertEquals(DanmakuFailure.Protocol, degraded.failure)
  }

  private fun testConfig() = DanmakuSupervisorConfig(
    startupTimeoutMs = 15_000L,
    stableSessionMs = 60_000L,
    degradeAfterFailures = 3,
    initialBackoffMs = 1_000L,
    maxBackoffMs = 30_000L,
    backoffJitterRatio = 0.0,
    watchdogPollMs = 3_000L,
  )

  private class FakeClock {
    private var value = 1_000_000L

    fun current(): Long = value

    fun advance(ms: Long) {
      value += ms
    }
  }

  private class NeverEndingConnection : DanmakuProtocolConnection {
    private val termination = CompletableDeferred<DanmakuTermination>()

    override suspend fun awaitTermination(): DanmakuTermination = termination.await()

    override fun close() {
      termination.complete(DanmakuTermination(DanmakuTerminationKind.Closed))
    }
  }
}
