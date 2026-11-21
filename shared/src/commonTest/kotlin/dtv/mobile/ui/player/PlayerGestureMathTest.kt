package dtv.mobile.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerGestureMathTest {
  @Test
  fun upwardDragForSixtyPercentOfHeightCoversFullRange() {
    assertEquals(
      1f,
      systemValueAfterVerticalDrag(
        startValue = 0f,
        accumulatedY = -600f,
        viewportHeightPx = 1000f,
      ),
    )
  }

  @Test
  fun downwardDragAndBoundsAreClamped() {
    assertEquals(
      0f,
      systemValueAfterVerticalDrag(
        startValue = 0.5f,
        accumulatedY = 2000f,
        viewportHeightPx = 1000f,
      ),
    )
  }
}
