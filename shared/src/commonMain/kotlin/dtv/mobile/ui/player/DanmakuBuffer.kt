package dtv.mobile.ui.player

import dtv.mobile.repo.DanmakuMessage

internal class DanmakuDisplayIdGenerator {
  private var lastId = 0L

  fun next(): Long = ++lastId
}

internal fun List<DanmakuMessage>.messagesAfterDisplayId(lastDisplayId: Long): List<DanmakuMessage> {
  if (lastDisplayId <= 0L) return filter { it.displayId > 0L }
  return filter { it.displayId > lastDisplayId }
}
