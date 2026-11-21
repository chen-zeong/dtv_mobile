package dtv.mobile.platform.douyin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DouyinResponseParserTest {
  @Test
  fun mapsPartitionRoomsAndPagination() {
    val result = DouyinResponseParser.parsePartitionResponse(
      """
      {
        "status_code": 0,
        "data": {
          "has_more": true,
          "data": [
            {
              "web_rid": "123456",
              "room": {
                "title": "测试直播",
                "owner": {
                  "nickname": "测试主播",
                  "avatar_thumb": {"url_list": ["https://example.com/avatar.jpg"]}
                },
                "cover": {"url_list": ["https://example.com/cover.jpg"]},
                "stats": {"user_count_str": "1.2万"}
              }
            }
          ]
        }
      }
      """.trimIndent(),
      limit = 10,
    )

    assertEquals(true, result.hasMore)
    assertEquals("123456", result.rooms.single().webRid)
    assertEquals("测试主播", result.rooms.single().ownerNickname)
    assertEquals("1.2万", result.rooms.single().userCountStr)
  }

  @Test
  fun propagatesPartitionRiskControlError() {
    val error = assertFailsWith<IllegalStateException> {
      DouyinResponseParser.parsePartitionResponse(
        """{"status_code":2154,"data":{"message":"risk control"}}""",
        limit = 10,
      )
    }
    assertEquals("risk control", error.message)
  }

  @Test
  fun mapsRoomEnterStreamUrls() {
    val result = DouyinResponseParser.parseRoomEnterResponse(
      """
      {
        "data": {
          "user": {
            "nickname": "主播",
            "avatar_thumb": {"url_list": ["https://example.com/a.jpg"]}
          },
          "data": [
            {
              "id_str": "987654",
              "title": "房间标题",
              "cover": {"url_list": ["https://example.com/c.jpg"]},
              "stats": {"user_count_str": "42"},
              "stream_url": {
                "hls_pull_url_map": {"ORIGIN": "https://example.com/live.m3u8"},
                "flv_pull_url": {"ORIGIN": "https://example.com/live.flv"}
              }
            }
          ]
        }
      }
      """.trimIndent(),
    )

    assertEquals("987654", result.roomId)
    assertEquals("房间标题", result.title)
    assertEquals("https://example.com/live.m3u8", result.streamUrls.hlsPullUrlMap["ORIGIN"])
    assertEquals("https://example.com/live.flv", result.streamUrls.flvPullUrlMap["ORIGIN"])
  }

  @Test
  fun identifiesRetryableFailuresOnlyForTransportOrEmptyStreamCases() {
    assertTrue(DouyinResponseParser.isRetryableFailure(200, ""))
    assertTrue(DouyinResponseParser.isRetryableFailure(200, "not-json"))
    assertTrue(DouyinResponseParser.isRetryableFailure(500, """{"status_code":0}"""))
    assertFalse(DouyinResponseParser.isRetryableFailure(200, """{"status_code":0}"""))

    val emptyStreams = DouyinResponseParser.parseRoomEnterResponse(
      """{"data":{"data":[{"id_str":"1","stream_url":{}}]}}""",
    )
    assertTrue(
      DouyinResponseParser.isRetryableFailure(
        200,
        """{"data":{"data":[{"id_str":"1","stream_url":{}}]}}""",
        emptyStreams,
      ),
    )
  }
}
