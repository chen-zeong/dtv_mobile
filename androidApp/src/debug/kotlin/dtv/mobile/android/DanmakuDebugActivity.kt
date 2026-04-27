package dtv.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.android.AndroidDtvRepository
import dtv.mobile.util.AppLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DanmakuDebugActivity : ComponentActivity() {
  companion object {
    private const val TAG = "DTV-DanmakuTest"
    private const val EXTRA_PLATFORM = "platform"
    private const val EXTRA_ROOM = "room"
    private const val EXTRA_SECONDS = "seconds"
    private const val DEFAULT_SECONDS = 60
  }

  private var collectJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppLog.init(applicationContext)

    val platform = intent.getStringExtra(EXTRA_PLATFORM)?.trim()?.lowercase().orEmpty()
    val room = intent.getStringExtra(EXTRA_ROOM)?.trim().orEmpty()
    val seconds = intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS).coerceIn(10, 600)

    if (platform.isBlank() || room.isBlank()) {
      AppLog.e(TAG, "missing args: platform=$platform room=$room")
      finish()
      return
    }

    var stateText by mutableStateOf("starting...")
    var recentLines by mutableStateOf(listOf<String>())
    setContent {
      MaterialTheme {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(text = "Danmaku Debug", style = MaterialTheme.typography.titleMedium)
          Text(text = "platform=$platform")
          Text(text = "room=$room")
          Text(text = "seconds=$seconds")
          Text(text = stateText)
          Text(text = "recent messages:")
          LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(recentLines) { line ->
              Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }

    val repo = AndroidDtvRepository(applicationContext)
    val flow = when (platform) {
      "huya" -> repo.observeHuyaDanmaku(room)
      "douyin" -> repo.observeDouyinDanmaku(room)
      else -> null
    }
    if (flow == null) {
      AppLog.e(TAG, "unsupported platform=$platform; only huya/douyin")
      finish()
      return
    }

    AppLog.i(TAG, "start platform=$platform room=$room seconds=$seconds")
    collectJob = lifecycleScope.launch {
      var count = 0
      stateText = "collecting..."
      withTimeoutOrNull(seconds * 1000L) {
        flow.collect { msg ->
          count += 1
          val line = formatMessage(msg)
          recentLines = (recentLines + line).takeLast(40)
          stateText = "messages=$count"
          AppLog.i(TAG, "msg#$count $line")
        }
      }
      stateText = "done, messages=$count"
      AppLog.i(TAG, "done platform=$platform room=$room total=$count")
      finish()
    }
  }

  override fun onDestroy() {
    collectJob?.cancel()
    collectJob = null
    super.onDestroy()
  }

  private fun formatMessage(msg: DanmakuMessage): String {
    val user = msg.user.replace('\n', ' ').replace('\r', ' ').trim()
    val content = msg.content.replace('\n', ' ').replace('\r', ' ').trim().take(120)
    return "user=$user content=$content"
  }
}
