package dtv.mobile.repo

import dtv.mobile.model.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToLong
import kotlin.random.Random

enum class DanmakuPhase {
  Connecting,
  Authenticating,
  Ready,
  Reconnecting,
  Degraded,
}
enum class DanmakuFailure {
  Network,
  Http,
  Authentication,
  Protocol,
  RoomUnavailable,
  RateLimited,
  Unknown,
}

data class DanmakuEvent(
  val platform: Platform,
  val roomId: String,
  val sessionId: Long,
  val sequence: Long,
  val occurredAtEpochMs: Long,
  val phase: DanmakuPhase,
  val attempt: Int = 0,
  val retryAfterMs: Long? = null,
  val failure: DanmakuFailure? = null,
  val message: DanmakuMessage? = null,
)

class DanmakuConnectionException(
  val failure: DanmakuFailure,
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

enum class DanmakuTerminationKind {
  Closed,
  Failed,
}

data class DanmakuTermination(
  val kind: DanmakuTerminationKind,
  val failure: DanmakuFailure? = null,
  val detail: String? = null,
)

interface DanmakuProtocolConnection {
  suspend fun awaitTermination(): DanmakuTermination

  fun close()
}

interface DanmakuProtocolSink {
  fun onAuthenticating()

  fun onInbound()

  fun onProtocolError() = Unit

  fun onMessage(message: DanmakuMessage)
}

interface DanmakuProtocolConnector {
  val watchdogTimeoutMs: Long

  suspend fun connect(
    roomId: String,
    sink: DanmakuProtocolSink,
  ): DanmakuProtocolConnection
}

data class DanmakuSupervisorConfig(
  val startupTimeoutMs: Long = 15_000L,
  val stableSessionMs: Long = 60_000L,
  val degradeAfterFailures: Int = 3,
  val initialBackoffMs: Long = 1_000L,
  val maxBackoffMs: Long = 30_000L,
  val backoffJitterRatio: Double = 0.2,
  val watchdogPollMs: Long = 3_000L,
)

class DanmakuConnectionSupervisor(
  private val config: DanmakuSupervisorConfig = DanmakuSupervisorConfig(),
  private val now: () -> Long = { System.currentTimeMillis() },
  private val sleeper: suspend (Long) -> Unit = { delay(it) },
  private val random: Random = Random.Default,
) {
  fun observe(
    platform: Platform,
    roomId: String,
    connector: DanmakuProtocolConnector,
  ): Flow<DanmakuEvent> = callbackFlow {
    val signals = Channel<Signal>(
      capacity = 512,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sessionId = now() * 1_000L + random.nextLong(0L, 1_000L)
    var sequence = 0L

    fun enqueue(
      phase: DanmakuPhase,
      attempt: Int = 0,
      retryAfterMs: Long? = null,
      failure: DanmakuFailure? = null,
      message: DanmakuMessage? = null,
    ): Boolean {
      return signals.trySend(
        Signal.Event(
          phase = phase,
          attempt = attempt,
          retryAfterMs = retryAfterMs,
          failure = failure,
          message = message,
        ),
      ).isSuccess
    }

    val emitter = launch {
      for (signal in signals) {
        when (signal) {
          is Signal.Event -> {
            sequence += 1L
            send(
              DanmakuEvent(
                platform = platform,
                roomId = roomId,
                sessionId = sessionId,
                sequence = sequence,
                occurredAtEpochMs = now(),
                phase = signal.phase,
                attempt = signal.attempt,
                retryAfterMs = signal.retryAfterMs,
                failure = signal.failure,
                message = signal.message,
              ),
            )
          }
        }
      }
    }

    val manager = launch {
      var attempt = 0
      var consecutiveStartupFailures = 0
      var degradedReported = false
      var hasConnected = false

      while (isActive) {
        attempt += 1
        enqueue(
          phase = if (!hasConnected && attempt == 1) DanmakuPhase.Connecting else DanmakuPhase.Reconnecting,
          attempt = attempt,
        )

        val lastInboundAt = MutableStateFlow(0L)
        val protocolErrorCount = MutableStateFlow(0)
        val protocolErrorReported = MutableStateFlow(false)
        val sink = object : DanmakuProtocolSink {
          override fun onAuthenticating() {
            enqueue(DanmakuPhase.Authenticating, attempt = attempt)
          }

          override fun onInbound() {
            lastInboundAt.value = now()
          }

          override fun onProtocolError() {
            protocolErrorCount.update { it + 1 }
            val count = protocolErrorCount.value
            if (count >= 5 && protocolErrorReported.compareAndSet(expect = false, update = true)) {
              enqueue(
                phase = DanmakuPhase.Degraded,
                attempt = attempt,
                failure = DanmakuFailure.Protocol,
              )
            }
          }

          override fun onMessage(message: DanmakuMessage) {
            onInbound()
            enqueue(
              phase = DanmakuPhase.Ready,
              attempt = attempt,
              message = message,
            )
          }
        }

        var connection: DanmakuProtocolConnection? = null
        var readyAt = 0L
        val watchdogFailure = MutableStateFlow<DanmakuFailure?>(null)
        var watchdogJob: Job? = null
        var termination: DanmakuTermination? = null

        try {
          val activeConnection = withTimeout(config.startupTimeoutMs) {
            connector.connect(roomId = roomId, sink = sink)
          }
          connection = activeConnection
          readyAt = now()
          hasConnected = true
          if (lastInboundAt.value <= 0L) lastInboundAt.value = readyAt
          enqueue(DanmakuPhase.Ready, attempt = attempt)

          watchdogJob = launch {
            while (isActive) {
              sleeper(config.watchdogPollMs)
              val last = lastInboundAt.value
              if (last <= 0L) continue
              if (now() - last >= connector.watchdogTimeoutMs) {
                watchdogFailure.value = DanmakuFailure.Protocol
                activeConnection.close()
                break
              }
            }
          }

          termination = connection.awaitTermination()
        } catch (t: TimeoutCancellationException) {
          connection?.close()
          termination = DanmakuTermination(
            kind = DanmakuTerminationKind.Failed,
            failure = DanmakuFailure.Network,
            detail = "startup timeout",
          )
        } catch (t: DanmakuConnectionException) {
          connection?.close()
          termination = DanmakuTermination(
            kind = DanmakuTerminationKind.Failed,
            failure = t.failure,
            detail = t.message,
          )
        } catch (t: CancellationException) {
          connection?.close()
          throw t
        } catch (t: Throwable) {
          connection?.close()
          termination = DanmakuTermination(
            kind = DanmakuTerminationKind.Failed,
            failure = DanmakuFailure.Unknown,
            detail = t.message,
          )
        } finally {
          watchdogJob?.cancel()
          connection?.close()
        }

        val completedTermination = requireNotNull(termination)
        val failure = watchdogFailure.value ?: completedTermination.failure
        val wasReady = readyAt > 0L
        val stable = wasReady && now() - readyAt >= config.stableSessionMs

        if (!wasReady) {
          consecutiveStartupFailures += 1
        } else {
          consecutiveStartupFailures = 0
          degradedReported = false
        }

        if (!degradedReported && consecutiveStartupFailures >= config.degradeAfterFailures) {
          degradedReported = true
          enqueue(
            phase = DanmakuPhase.Degraded,
            attempt = attempt,
            failure = failure ?: DanmakuFailure.Unknown,
          )
        }

        if (stable) {
          attempt = 0
        }

        val retryDelayMs = jitteredBackoff(attempt)
        enqueue(
          phase = DanmakuPhase.Reconnecting,
          attempt = attempt,
          retryAfterMs = retryDelayMs,
          failure = failure,
        )
        sleeper(retryDelayMs)
      }
    }

    manager.invokeOnCompletion { cause ->
      if (cause != null && cause !is CancellationException) {
        enqueue(
          phase = DanmakuPhase.Degraded,
          failure = DanmakuFailure.Unknown,
        )
      }
      signals.close()
    }

    awaitClose {
      manager.cancel()
      signals.close()
      emitter.cancel()
    }
  }.buffer(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private fun jitteredBackoff(attempt: Int): Long {
    val exponent = attempt.coerceIn(1, 5) - 1
    val base = (config.initialBackoffMs shl exponent).coerceAtMost(config.maxBackoffMs)
    val jitter = (base * config.backoffJitterRatio).roundToLong().coerceAtLeast(1L)
    return (base + random.nextLong(-jitter, jitter + 1L)).coerceIn(1L, config.maxBackoffMs)
  }

  private sealed interface Signal {
    data class Event(
      val phase: DanmakuPhase,
      val attempt: Int,
      val retryAfterMs: Long?,
      val failure: DanmakuFailure?,
      val message: DanmakuMessage?,
    ) : Signal
  }
}
