package dtv.mobile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import dtv.mobile.state.ThemeMode
import dtv.mobile.ui.system.SystemBarsEffect

@Immutable
data class DtvExtras(
  val accentGradient: Brush,
)

private val LocalExtras = staticCompositionLocalOf<DtvExtras> {
  error("DtvExtras not provided")
}

val MaterialTheme.dtvExtras: DtvExtras
  @Composable get() = LocalExtras.current

private fun dayScheme(): ColorScheme = lightColorScheme(
  primary = DtvColors.DayAccent,
  onPrimary = DtvColors.DayTextPrimary,
  secondary = DtvColors.DayBgTertiary,
  onSecondary = DtvColors.DayTextPrimary,
  background = DtvColors.DayBgPrimary,
  onBackground = DtvColors.DayTextPrimary,
  surface = DtvColors.DayBgSecondary,
  onSurface = DtvColors.DayTextPrimary,
  outline = DtvColors.DayBorder,
)

private fun nightScheme(): ColorScheme = darkColorScheme(
  primary = DtvColors.NightAccent,
  onPrimary = DtvColors.NightTextPrimary,
  secondary = DtvColors.NightBgTertiary,
  onSecondary = DtvColors.NightTextPrimary,
  background = DtvColors.NightBgPrimary,
  onBackground = DtvColors.NightTextPrimary,
  surface = DtvColors.NightBgSecondary,
  onSurface = DtvColors.NightTextPrimary,
  outline = DtvColors.NightBorder,
)

@Composable
fun DtvTheme(
  themeMode: ThemeMode,
  content: @Composable () -> Unit,
) {
  val dark = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
  }
  val scheme = if (dark) nightScheme() else dayScheme()

  SystemBarsEffect(darkTheme = dark)

  val accentGradient = Brush.linearGradient(
    colors = listOf(
      if (dark) DtvColors.NightAccent else DtvColors.DayAccent,
      if (dark) DtvColors.NightAccentHover else DtvColors.DayAccentHover,
    ),
  )

  androidx.compose.runtime.CompositionLocalProvider(
    LocalExtras provides DtvExtras(accentGradient = accentGradient),
  ) {
    MaterialTheme(
      colorScheme = scheme,
      content = content,
    )
  }
}

