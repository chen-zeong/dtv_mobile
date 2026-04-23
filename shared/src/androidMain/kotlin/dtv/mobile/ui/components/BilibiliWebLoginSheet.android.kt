package dtv.mobile.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val BILIBILI_LOGIN_URL = "https://passport.bilibili.com/login"
private const val BILIBILI_COOKIE_PROBE_URL = "https://www.bilibili.com"
private const val BILIBILI_COOKIE_PROBE_URL_2 = "https://passport.bilibili.com"

private fun hasRequiredBilibiliCookies(cookieHeader: String?): Boolean {
  val raw = cookieHeader?.lowercase().orEmpty()
  return raw.contains("sessdata=") && raw.contains("bili_jct=")
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun BilibiliWebLoginSheet(
  onDismissRequest: () -> Unit,
  onCookieCaptured: (cookieHeader: String) -> Unit,
) {
  val context = LocalContext.current
  val cookieManager = remember {
    CookieManager.getInstance().apply {
      setAcceptCookie(true)
    }
  }

  val webView = remember(context) {
    WebView(context).apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.userAgentString = settings.userAgentString + " DTV-Mobile"
    }
  }

  var status by remember { mutableStateOf("请在网页登录 B站 账号") }
  var lastCookie by remember { mutableStateOf<String?>(null) }

  fun probeAndMaybeFinish() {
    val merged = listOfNotNull(
      cookieManager.getCookie(BILIBILI_COOKIE_PROBE_URL)?.trim()?.takeIf { it.isNotBlank() },
      cookieManager.getCookie(BILIBILI_COOKIE_PROBE_URL_2)?.trim()?.takeIf { it.isNotBlank() },
    ).distinct().joinToString("; ").trim().ifBlank { null }

    if (merged != null && merged != lastCookie) {
      lastCookie = merged
    }

    if (hasRequiredBilibiliCookies(merged)) {
      status = "已获取登录 Cookie"
      onCookieCaptured(merged!!)
      onDismissRequest()
    }
  }

  DisposableEffect(Unit) {
    webView.webViewClient = object : WebViewClient() {
      override fun onPageFinished(view: WebView?, url: String?) {
        probeAndMaybeFinish()
      }
    }

    runCatching { cookieManager.setAcceptThirdPartyCookies(webView, true) }
    webView.loadUrl(BILIBILI_LOGIN_URL)

    onDispose {
      runCatching { webView.stopLoading() }
      runCatching { webView.destroy() }
    }
  }

  ModalBottomSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("B站网页登录", style = MaterialTheme.typography.titleMedium)
      Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))

      AndroidView(
        factory = { webView },
        modifier = Modifier
          .fillMaxWidth()
          .height(520.dp),
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onDismissRequest) { Text("关闭") }
        TextButton(onClick = { probeAndMaybeFinish() }) { Text("完成") }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
