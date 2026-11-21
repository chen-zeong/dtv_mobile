package dtv.mobile.ui.player

import dtv.mobile.repo.DanmakuMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuBufferTest {
  @Test
  fun findsNewMessagesAfterCappedBufferStopsGrowing() {
    val messages = (190L..209L).map { id ->
      DanmakuMessage(
        roomId = "room",
        user = "user",
        content = "message-$id",
        displayId = id,
      )
    }

    val newMessages = messages.messagesAfterDisplayId(lastDisplayId = 208L)

    assertEquals(listOf(209L), newMessages.map { it.displayId })
  }

  @Test
  fun displayIdsKeepIncreasingAcrossReconnects() {
    val generator = DanmakuDisplayIdGenerator()

    assertEquals(1L, generator.next())
    assertEquals(2L, generator.next())
    assertEquals(3L, generator.next())
  }
}
