package dtv.mobile.platform.douyu

import dtv.mobile.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DouyuResponseParsersTest {
  @Test
  fun mapsSearchAnchorsAndFiltersNonAnchorItems() {
    val result = DouyuResponseParsers.parseSearchResponse(
      """
      {
        "data": {
          "relateUser": [
            {
              "type": 1,
              "anchorInfo": {
                "rid": "9999",
                "nickName": "斗鱼主播",
                "roomName": "测试标题",
                "avatar": "//example.com/a.jpg",
                "isLive": 1
              }
            },
            {"type": 2, "anchorInfo": {"rid": "bad", "nickName": "过滤项"}}
          ]
        }
      }
      """.trimIndent(),
    )

    assertEquals(1, result.size)
    assertEquals(Platform.Douyu, result.single().platform)
    assertEquals("9999", result.single().roomId)
    assertEquals("https://example.com/a.jpg", result.single().avatarUrl)
    assertTrue(result.single().isLive)
  }

  @Test
  fun mapsMixListAndFormatsViewerCount() {
    val result = DouyuResponseParsers.parseMixListResponse(
      """
      {
        "code": 0,
        "data": {
          "rl": [
            {
              "rid": 9999,
              "nn": "斗鱼主播",
              "rn": "测试标题",
              "av": "https://example.com/a.jpg",
              "rs16": "https://example.com/c.jpg",
              "ol": 123456,
              "type": 1
            }
          ]
        }
      }
      """.trimIndent(),
    )

    assertEquals("9999", result.single().roomId)
    assertEquals("12.3万", result.single().viewerText)
    assertTrue(result.single().isLive)
  }

  @Test
  fun returnsEmptyForInvalidSearchJson() {
    assertEquals(emptyList(), DouyuResponseParsers.parseSearchResponse("not-json"))
  }

  @Test
  fun treatsLoopingMixListRoomsAsOffline() {
    val result = DouyuResponseParsers.parseMixListResponse(
      """
      {
        "code": 0,
        "data": {
          "rl": [
            {
              "rid": 9999,
              "nn": "轮播主播",
              "rn": "录像轮播",
              "type": 1,
              "videoLoop": 1
            }
          ]
        }
      }
      """.trimIndent(),
    )

    assertFalse(result.single().isLive)
  }
}
