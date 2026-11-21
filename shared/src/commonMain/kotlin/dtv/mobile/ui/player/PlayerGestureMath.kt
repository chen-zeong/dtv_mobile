package dtv.mobile.ui.player

internal fun systemValueAfterVerticalDrag(
  startValue: Float,
  accumulatedY: Float,
  viewportHeightPx: Float,
): Float {
  val span = (viewportHeightPx * 0.60f).coerceAtLeast(1f)
  return (startValue - accumulatedY / span).coerceIn(0f, 1f)
}
