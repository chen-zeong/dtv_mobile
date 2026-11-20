package dtv.mobile.repo

data class PlaybackSelectionIds(
  val qualityId: String?,
  val lineId: String?,
)

object PlaybackSelectionResolver {
  fun normalize(
    options: PlaybackOptions,
    preferredQualityId: String?,
    preferredLineId: String?,
  ): PlaybackSelectionIds {
    val preferredLine = preferredLineId?.let { line(options, it) }
    val effectiveLine = preferredLine ?: defaultLine(options)
    val quality = preferredQualityId?.let { qualityId ->
      effectiveLine?.qualities?.firstOrNull { it.id.equals(qualityId, ignoreCase = true) }?.id
    }
    return PlaybackSelectionIds(
      qualityId = quality,
      lineId = preferredLine?.id,
    )
  }

  fun line(options: PlaybackOptions, lineId: String): PlaybackLine? {
    return options.lines.firstOrNull { it.id == lineId }
      ?: options.lines.firstOrNull { it.id.equals(lineId, ignoreCase = true) }
  }

  fun defaultLine(options: PlaybackOptions): PlaybackLine? {
    return options.defaultLineId?.let { line(options, it) } ?: options.lines.firstOrNull()
  }

  fun effectiveLine(options: PlaybackOptions, lineId: String?): PlaybackLine? {
    return lineId?.let { line(options, it) } ?: defaultLine(options)
  }

  fun quality(line: PlaybackLine, qualityId: String): PlaybackQuality? {
    return line.qualities.firstOrNull { it.id == qualityId }
      ?: line.qualities.firstOrNull { it.id.equals(qualityId, ignoreCase = true) }
  }

  fun exactQualityOrThrow(line: PlaybackLine, qualityId: String): PlaybackQuality {
    return quality(line, qualityId)
      ?: error("当前线路不提供所选画质")
  }
}
