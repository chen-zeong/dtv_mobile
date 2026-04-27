package dtv.mobile.theme

import androidx.compose.ui.graphics.Color

// From desktop `web/src/app/legacy-global.css`
internal object DtvColors {
  // livestream-hub (aligned tokens)
  val HubAccent = Color(0xFFA3E635) // lime-400
  val HubAccentHover = Color(0xFFBEF264) // lime-200

  // day
  val DayBgPrimary = Color(0xFFF5F5F5)
  val DayBgSecondary = Color(0xFFFFFFFF)
  val DayBgTertiary = Color(0xFFE5E5E5)
  val DayTextPrimary = Color(0xFF0B0B0B)
  val DayTextSecondary = Color(0xFF6B7280)
  val DayAccent = HubAccent
  val DayAccentHover = HubAccentHover
  val DayBorder = Color(0xFFE5E7EB)

  // night
  // A slightly cooler, softer dark palette for better readability.
  val NightBgPrimary = Color(0xFF0B0F14)
  val NightBgSecondary = Color(0xFF121720)
  val NightBgTertiary = Color(0xFF1A2230)
  val NightTextPrimary = Color(0xFFF9FAFB)
  val NightTextSecondary = Color(0xFF9CA3AF)
  val NightAccent = HubAccent
  val NightAccentHover = HubAccentHover
  val NightBorder = Color(0xFF263244)

  val StatusLive = Color(0xFFFF3E3E)
}

