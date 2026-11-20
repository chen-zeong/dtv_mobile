package dtv.mobile.platform.bilibili

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BilibiliLiveListParserTest {
  @Test
  fun mapsAreaRoomFields() {
    val rooms = BilibiliLiveListParser.parseAreaResponse(
      """
      {
        "code": 0,
        "message": "success",
        "data": [
          {
            "roomid": 22174141,
            "title": "晚七点最伟大的抱团",
            "uname": "赵俊日",
            "online": 398326,
            "user_cover": "https://i0.hdslb.com/bfs/live/cover.jpg",
            "face": "https://i1.hdslb.com/bfs/face/avatar.jpg",
            "parent_id": 2,
            "area_id": 86
          }
        ]
      }
      """.trimIndent(),
    )

    val streamer = rooms.single().let(BilibiliLiveListParser::mapRoom)

    assertEquals("22174141", streamer?.roomId)
    assertEquals("赵俊日", streamer?.name)
    assertEquals("晚七点最伟大的抱团", streamer?.title)
    assertEquals("39.8万", streamer?.viewerText)
    assertEquals("https://i0.hdslb.com/bfs/live/cover.jpg", streamer?.coverUrl)
    assertEquals("https://i1.hdslb.com/bfs/face/avatar.jpg", streamer?.avatarUrl)
    assertEquals(true, streamer?.isLive)
  }

  @Test
  fun propagatesRiskControlError() {
    val error = assertFailsWith<IllegalStateException> {
      BilibiliLiveListParser.parseAreaResponse("""{"code":-352,"message":"risk control"}""")
    }

    assertEquals("risk control", error.message)
  }

  @Test
  fun returnsEmptyListWhenAreaDataIsMissing() {
    val rooms = BilibiliLiveListParser.parseAreaResponse("""{"code":0,"data":null}""")

    assertEquals(emptyList(), rooms)
  }

  @Test
  fun skipsRoomsWithoutRoomId() {
    val streamer = BilibiliLiveListParser.mapRoom(
      kotlinx.serialization.json.buildJsonObject {
        put("title", kotlinx.serialization.json.JsonPrimitive("直播中"))
      },
    )

    assertNull(streamer)
  }
}
