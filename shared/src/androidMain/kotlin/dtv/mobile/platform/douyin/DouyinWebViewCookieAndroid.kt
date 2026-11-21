package dtv.mobile.platform.douyin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class DouyinWebViewCookieAndroid(
  private val appContext: Context,
) {
  @SuppressLint("SetJavaScriptEnabled")
  suspend fun fetch(url: String, userAgent: String, timeoutMs: Long = 10_000L): String? =
    suspendCancellableCoroutine { cont ->
      val handler = Handler(Looper.getMainLooper())

      fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
      }

      runOnMain {
        if (!cont.isActive) return@runOnMain
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)

        val webView = WebView(appContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          runCatching { cm.setAcceptThirdPartyCookies(webView, true) }
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = userAgent

        var finished = false
        var pollRunnable: Runnable? = null

        fun cleanup() {
          runCatching { webView.stopLoading() }
          runCatching { webView.destroy() }
        }

        fun stopPolling() {
          pollRunnable?.let(handler::removeCallbacks)
          pollRunnable = null
        }

        fun finishWith(cookie: String?) {
          if (finished) return
          finished = true
          stopPolling()
          cleanup()
          if (cont.isActive) cont.resume(cookie)
        }

        fun schedulePoll(delayMs: Long) {
          if (finished) return
          stopPolling()
          val runnable = Runnable {
            pollRunnable = null
            if (finished) return@Runnable
            val cookie = runCatching { cm.getCookie(url) }.getOrNull().orEmpty()
            if (cookie.contains("ttwid=")) {
              finishWith(cookie)
            } else {
              schedulePoll(350L)
            }
          }
          pollRunnable = runnable
          handler.postDelayed(runnable, delayMs)
        }

        webView.webViewClient = object : WebViewClient() {
          override fun onPageFinished(view: WebView?, u: String?) {
            if (finished) return
            schedulePoll(650L)
          }

          @Deprecated("Deprecated in Java")
          override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) = Unit

          override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) = Unit
        }

        val timeoutRunnable = Runnable {
          val cookie = runCatching { cm.getCookie(url) }.getOrNull()
          finishWith(cookie?.takeIf { it.contains("ttwid=") })
        }
        handler.postDelayed(timeoutRunnable, timeoutMs)

        cont.invokeOnCancellation {
          handler.post {
            finished = true
            stopPolling()
            handler.removeCallbacks(timeoutRunnable)
            cleanup()
          }
        }

        runCatching { webView.loadUrl(url) }
          .onFailure { t ->
            handler.post {
              if (finished) return@post
              finished = true
              stopPolling()
              handler.removeCallbacks(timeoutRunnable)
              cleanup()
              if (cont.isActive) cont.resumeWithException(t)
            }
          }
      }
    }
}
