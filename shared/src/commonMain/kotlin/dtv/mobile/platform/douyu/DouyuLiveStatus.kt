package dtv.mobile.platform.douyu

/**
 * Resolves whether a Douyu room is actually live.
 *
 * `show_status == 1` can still represent a looping recorded video. A loop is not treated as a
 * live broadcast by either the followed list or the player.
 */
internal fun resolveDouyuLiveStatus(
  showStatus: Long?,
  videoLoop: Long?,
  isLoop: Long?,
): Boolean? {
  if (videoLoop == 1L || isLoop == 1L) return false
  return when (showStatus) {
    1L -> true
    0L, 2L -> false
    else -> null
  }
}
