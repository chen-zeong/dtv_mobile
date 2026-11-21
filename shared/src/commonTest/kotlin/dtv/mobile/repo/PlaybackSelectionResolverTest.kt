package dtv.mobile.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSelectionResolverTest {
  private val options = PlaybackOptions(
    lines = listOf(
      PlaybackLine(
        id = "hls",
        label = "HLS",
        qualities = listOf(
          PlaybackQuality(id = "origin", label = "原画"),
          PlaybackQuality(id = "hd", label = "高清"),
        ),
      ),
      PlaybackLine(
        id = "flv",
        label = "FLV",
        qualities = listOf(
          PlaybackQuality(id = "origin", label = "原画"),
        ),
      ),
    ),
    defaultLineId = "hls",
    defaultQualityId = "origin",
  )

  @Test
  fun keepsOnlyAvailableSelections() {
    val result = PlaybackSelectionResolver.normalize(options, "hd", "hls")

    assertEquals("hd", result.qualityId)
    assertEquals("hls", result.lineId)
  }

  @Test
  fun dropsSelectionsNoLongerOffered() {
    val result = PlaybackSelectionResolver.normalize(options, "4k", "dash")

    assertNull(result.qualityId)
    assertNull(result.lineId)
  }

  @Test
  fun rejectsQualityThatBelongsToAnotherLine() {
    val result = PlaybackSelectionResolver.normalize(options, "hd", "flv")

    assertNull(result.qualityId)
    assertEquals("flv", result.lineId)
  }
}
