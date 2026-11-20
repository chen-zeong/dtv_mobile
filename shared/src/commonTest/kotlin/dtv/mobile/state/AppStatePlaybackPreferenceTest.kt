package dtv.mobile.state

import dtv.mobile.model.Platform
import dtv.mobile.repo.fake.FakeDtvRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStatePlaybackPreferenceTest {
  @Test
  fun keepsPlaybackPreferencesIndependentPerRoom() {
    val store = RecordingStore().apply {
      items = listOf(
        PlaybackPreference(Platform.Douyin, "room-a", qualityId = "ORIGIN", lineId = "hls"),
        PlaybackPreference(Platform.Douyin, "room-b", qualityId = "HD1", lineId = "flv"),
      )
    }
    val appState = AppState(FakeDtvRepository(), store)

    assertEquals("ORIGIN", appState.playbackPreference(Platform.Douyin, "room-a").qualityId)
    assertEquals("HD1", appState.playbackPreference(Platform.Douyin, "room-b").qualityId)

    appState.updatePlaybackPreference(Platform.Douyin, "room-a", qualityId = null, lineId = "hls")

    assertNull(appState.playbackPreference(Platform.Douyin, "room-a").qualityId)
    assertEquals("HD1", appState.playbackPreference(Platform.Douyin, "room-b").qualityId)
    assertEquals(2, store.items.size)
  }

  private class RecordingStore : SubscriptionStore by InMemorySubscriptionStore {
    var items: List<PlaybackPreference> = emptyList()

    override fun loadPlaybackPreferences(): List<PlaybackPreference> = items

    override fun savePlaybackPreferences(items: List<PlaybackPreference>) {
      this.items = items
    }
  }
}
