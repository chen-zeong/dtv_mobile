package dtv.mobile.platform.huya

import dtv.mobile.platform.danmaku.DeferredDanmakuConnection
import dtv.mobile.repo.DanmakuConnectionException
import dtv.mobile.repo.DanmakuFailure
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.DanmakuProtocolConnection
import dtv.mobile.repo.DanmakuProtocolConnector
import dtv.mobile.repo.DanmakuProtocolSink
import dtv.mobile.repo.DanmakuTermination
import dtv.mobile.repo.DanmakuTerminationKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

class HuyaDanmakuClientAndroid(
  private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .proxy(Proxy.NO_PROXY)
    .build(),
) : DanmakuProtocolConnector {
  private val wsClient: HttpClient = HttpClient(CIO) {
    install(WebSockets)
  }

  override val watchdogTimeoutMs: Long = 90_000L

  companion object {
    // Copied from DTV-heroui desktop backend (src-tauri/src/platforms/huya/danmaku.rs).
    // Do not modify unless upstream changes.
    private val HEARTBEAT: ByteArray = byteArrayOf(
      0x00, 0x03, 0x1d, 0x00, 0x00, 0x69, 0x00, 0x00, 0x00, 0x69, 0x10, 0x03, 0x2c, 0x3c, 0x4c, 0x56,
      0x08, 0x6f, 0x6e, 0x6c, 0x69, 0x6e, 0x65, 0x75, 0x69, 0x66, 0x0f, 0x4f, 0x6e, 0x55, 0x73, 0x65,
      0x72, 0x48, 0x65, 0x61, 0x72, 0x74, 0x42, 0x65, 0x61, 0x74, 0x7d, 0x00, 0x00, 0x3c, 0x08, 0x00,
      0x01, 0x06, 0x04, 0x74, 0x52, 0x65, 0x71, 0x1d, 0x00, 0x00, 0x2f, 0x0a, 0x0a, 0x0c, 0x16, 0x00,
      0x26, 0x00, 0x36, 0x07, 0x61, 0x64, 0x72, 0x5f, 0x77, 0x61, 0x70, 0x46, 0x00, 0x0b, 0x12, 0x03,
      0xae.toByte(), 0xf0.toByte(), 0x0f, 0x22, 0x03, 0xae.toByte(), 0xf0.toByte(), 0x0f, 0x3c, 0x42,
      0x6d, 0x52, 0x02, 0x60, 0x5c, 0x60, 0x01, 0x7c, 0x82.toByte(), 0x00, 0x0b, 0xb0.toByte(), 0x1f,
      0x9c.toByte(), 0xac.toByte(), 0x0b, 0x8c.toByte(), 0x98.toByte(), 0x0c, 0xa8.toByte(), 0x0c,
    )

    private const val UA =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  }

  override suspend fun connect(
    roomId: String,
    sink: DanmakuProtocolSink,
  ): DanmakuProtocolConnection = withContext(Dispatchers.IO) {
    val wsInfo = try {
      HuyaDtvHerouiAlgo.fetchWsInfo(http, roomId)
    } catch (t: CancellationException) {
      throw t
    } catch (t: Throwable) {
      throw DanmakuConnectionException(
        failure = DanmakuFailure.Network,
        message = t.message ?: "获取虎牙弹幕参数失败",
        cause = t,
      )
    }

    val ready = CompletableDeferred<DanmakuProtocolConnection>()
    val termination = CompletableDeferred<DanmakuTermination>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionJob = scope.launch {
      var heartbeatJob: Job? = null
      try {
        wsClient.webSocket(
          urlString = "wss://cdnws.api.huya.com",
          request = {
            header("Origin", "https://www.huya.com")
            header("Referer", "https://www.huya.com/")
            header("User-Agent", UA)
          },
        ) {
          sink.onAuthenticating()
          send(Frame.Binary(fin = true, data = wsInfo.registerPayload))
          sink.onInbound()

          heartbeatJob = launch {
            while (isActive) {
              delay(20_000L)
              send(Frame.Binary(fin = true, data = HEARTBEAT))
              sink.onInbound()
            }
          }

          ready.complete(
            DeferredDanmakuConnection(
              termination = termination,
              closeAction = {
                heartbeatJob.cancel()
                scope.cancel()
              },
            ),
          )

          for (frame in incoming) {
            if (frame !is Frame.Binary) continue
            sink.onInbound()
            val chat = runCatching { HuyaDtvHerouiAlgo.decodeChat(frame.data) }
              .onFailure { sink.onProtocolError() }
              .getOrNull()
              ?: continue
            sink.onMessage(
              DanmakuMessage(
                roomId = roomId,
                user = chat.user,
                content = chat.content,
              ),
            )
          }
        }
        termination.complete(DanmakuTermination(DanmakuTerminationKind.Closed))
      } catch (t: CancellationException) {
        termination.complete(DanmakuTermination(DanmakuTerminationKind.Closed))
        throw t
      } catch (t: Throwable) {
        val result = DanmakuTermination(
          kind = DanmakuTerminationKind.Failed,
          failure = DanmakuFailure.Network,
          detail = t.message,
        )
        termination.complete(result)
        if (!ready.isCompleted) {
          ready.completeExceptionally(
            DanmakuConnectionException(
              failure = DanmakuFailure.Network,
              message = t.message ?: "虎牙 WebSocket 握手失败",
              cause = t,
            ),
          )
        }
      } finally {
        heartbeatJob?.cancel()
      }
    }

    try {
      ready.await()
    } catch (t: Throwable) {
      sessionJob.cancel()
      scope.cancel()
      throw t
    }
  }
}
