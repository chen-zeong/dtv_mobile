package dtv.mobile.platform.bilibili

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BilibiliSearchParserTest {
  @Test
  fun mapsLiveSearchResultsAndRemovesHighlightTags() {
    val result = BilibiliSearchParser.parseResponse(
      """
      {
        "code": 0,
        "data": {
          "result": {
            "live_user": [
              {
                "roomid": 123,
                "uname": "<em class=\"keyword\">主播</em>",
                "title": "<em>测试</em>直播",
                "online": "1.2万",
                "cover": "//i0.hdslb.com/cover.jpg",
                "uface": "//i1.hdslb.com/avatar.jpg",
                "live_status": 1
              }
            ]
          }
        }
      }
      """.trimIndent(),
    )

    assertEquals("123", result.single().roomId)
    assertEquals("主播", result.single().name)
    assertEquals("测试直播", result.single().title)
    assertEquals("https://i0.hdslb.com/cover.jpg@400w.jpg", result.single().coverUrl)
    assertTrue(result.single().isLive)
  }

  @Test
  fun returnsEmptyForRiskControlResponse() {
    assertEquals(emptyList(), BilibiliSearchParser.parseResponse("""{"code":-412}"""))
    assertTrue(BilibiliLiveListParser.isRiskControlResponse("""{"code":-412}"""))
    assertTrue(BilibiliLiveListParser.isRiskControlResponse("""{"code":-352}"""))
  }
}
