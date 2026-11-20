package dtv.mobile.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlayerControlsStressTest {
  @Test
  fun rapidRepeatedControlClicksRemainStable() = runComposeUiTest {
    var settingsClicks = 0
    var reloadClicks = 0
    var fullscreenClicks = 0

    setContent {
      MaterialTheme {
        PlayerSideControlsOverlay(
          fullscreen = false,
          showFullscreen = true,
          actionsEnabled = true,
          onToggleFullscreen = { fullscreenClicks += 1 },
          onOpenSettings = { settingsClicks += 1 },
          onReload = { reloadClicks += 1 },
        )
      }
    }

    repeat(50) {
      onNodeWithTag("player_settings").performClick()
      onNodeWithTag("player_reload").performClick()
      onNodeWithTag("player_fullscreen").performClick()
    }
    waitForIdle()

    assertEquals(50, settingsClicks)
    assertEquals(50, reloadClicks)
    assertEquals(50, fullscreenClicks)
  }
}
