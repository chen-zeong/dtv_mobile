package dtv.mobile.platform.douyu

import dtv.mobile.platform.danmaku.DeferredDanmakuConnection
import dtv.mobile.platform.danmaku.okHttpTermination
import dtv.mobile.repo.DanmakuConnectionException
import dtv.mobile.repo.DanmakuFailure
import dtv.mobile.repo.DanmakuProtocolConnection
import dtv.mobile.repo.DanmakuProtocolConnector
import dtv.mobile.repo.DanmakuProtocolSink
import dtv.mobile.repo.DanmakuTermination
import dtv.mobile.repo.DanmakuTerminationKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

class DouyuDanmakuClientAndroid(
  private val okHttp: OkHttpClient = OkHttpClient(),
) : DanmakuProtocolConnector {
  override val watchdogTimeoutMs: Long = 135_000L

  override suspend fun connect(
    roomId: String,
    sink: DanmakuProtocolSink,
  ): DanmakuProtocolConnection = withContext(Dispatchers.IO) {
    val ready = CompletableDeferred<DanmakuProtocolConnection>()
    val termination = CompletableDeferred<DanmakuTermination>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var socket: WebSocket? = null
    var heartbeatJob: kotlinx.coroutines.Job? = null

    fun terminate(result: DanmakuTermination) {
      termination.complete(result)
      if (!ready.isCompleted) {
        ready.completeExceptionally(
          DanmakuConnectionException(
            failure = result.failure ?: DanmakuFailure.Network,
            message = result.detail ?: "WebSocket closed before it became ready",
          ),
        )
      }
    }

    val request = Request.Builder()
      .url("wss://danmuproxy.douyu.com:8506/")
      .addHeader("Sec-WebSocket-Protocol", "binary")
      .build()

    val listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        sink.onAuthenticating()
        webSocket.send(DouyuDanmakuCodecAndroid.encodeClientMessage("type@=loginreq/roomid@=$roomId/").toByteString())
        webSocket.send(DouyuDanmakuCodecAndroid.encodeClientMessage("type@=joingroup/rid@=$roomId/gid@=1/").toByteString())
        sink.onInbound()
        heartbeatJob = scope.launch {
          while (isActive) {
            delay(45_000L)
            if (!webSocket.send(DouyuDanmakuCodecAndroid.encodeClientMessage("type@=mrkl/").toByteString())) break
            sink.onInbound()
          }
        }
        ready.complete(
          DeferredDanmakuConnection(
            termination = termination,
            closeAction = {
              heartbeatJob.cancel()
              webSocket.cancel()
              scope.cancel()
            },
          ),
        )
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        sink.onInbound()
        val decoded = DouyuDanmakuCodecAndroid.decodeChatMessages(bytes.toByteArray(), roomId)
        if (decoded.malformed) sink.onProtocolError()
        decoded.messages.forEach(sink::onMessage)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(code, reason)
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        heartbeatJob?.cancel()
        terminate(DanmakuTermination(DanmakuTerminationKind.Closed, detail = reason))
        scope.cancel()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        heartbeatJob?.cancel()
        terminate(okHttpTermination(response, t.message))
        scope.cancel()
      }
    }

    try {
      socket = okHttp.newWebSocket(request, listener)
      ready.await()
    } catch (t: Throwable) {
      heartbeatJob?.cancel()
      socket?.cancel()
      scope.cancel()
      throw t
    }
  }

}
