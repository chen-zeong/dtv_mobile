package dtv.mobile.platform.douyu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DouyuDanmakuCodecAndroidTest {
  @Test
  fun decodesMultiplePacketsFromOneWebSocketMessage() {
    val first = DouyuDanmakuCodecAndroid.encodeClientMessage(
      "type@=chatmsg/nn@=用户A/txt@=第一条/level@=12/col@=1/",
    )
    val second = DouyuDanmakuCodecAndroid.encodeClientMessage(
      "type@=chatmsg/nn@=用户B/txt@=第二条/level@=20/col@=2/",
    )

    val decoded = DouyuDanmakuCodecAndroid.decodeChatMessages(first + second, roomId = "123")

    assertFalse(decoded.malformed)
    assertEquals(listOf("第一条", "第二条"), decoded.messages.map { it.content })
    assertEquals(listOf("用户A", "用户B"), decoded.messages.map { it.user })
  }

  @Test
  fun decodesEscapedDelimiters() {
    val frame = DouyuDanmakuCodecAndroid.encodeClientMessage(
      "type@=chatmsg/nn@=用户A/txt@=A@SB@AC/",
    )

    val decoded = DouyuDanmakuCodecAndroid.decodeChatMessages(frame, roomId = "123")

    assertFalse(decoded.malformed)
    assertEquals("A/B@C", decoded.messages.single().content)
  }
}
