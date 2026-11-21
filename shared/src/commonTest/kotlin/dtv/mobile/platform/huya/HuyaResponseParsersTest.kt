package dtv.mobile.platform.huya

import dtv.mobile.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HuyaResponseParsersTest {
  @Test
  fun mapsLiveListFields() {
    val result = HuyaResponseParsers.parseLiveListResponse(
      """
      {
        "vList": [
          {
            "lProfileRoom": 8888,
            "sNick": "虎牙主播",
            "sIntroduction": "测试标题",
            "sAvatar180": "https://example.com/a.jpg",
            "sScreenshot": "https://example.com/c.jpg",
            "lUserCount": 234567
          }
        ]
      }
      """.trimIndent(),
    )

    assertEquals(1, result.size)
    assertEquals(Platform.Huya, result.single().platform)
    assertEquals("8888", result.single().roomId)
    assertEquals("23.4万", result.single().viewerText)
  }

  @Test
  fun mapsSearchLiveStateAndDropsInvalidRooms() {
    val result = HuyaResponseParsers.parseSearchResponse(
      """
      {
        "response": {
          "1": {
            "docs": [
              {
                "room_id": 8888,
                "game_nick": "虎牙主播",
                "live_intro": "测试标题",
                "game_avatarUrl180": "https://example.com/a.jpg",
                "gameLiveOn": true
              },
              {"room_id": 0, "game_nick": "过滤项"}
            ]
          }
        }
      }
      """.trimIndent(),
    )

    assertEquals(1, result.size)
    assertTrue(result.single().isLive)
  }

  @Test
  fun handlesEmptyOrMalformedSearchData() {
    assertEquals(emptyList(), HuyaResponseParsers.parseSearchResponse("not-json"))
    assertEquals(emptyList(), HuyaResponseParsers.parseSearchResponse("""{"response":{}}"""))
  }
}
