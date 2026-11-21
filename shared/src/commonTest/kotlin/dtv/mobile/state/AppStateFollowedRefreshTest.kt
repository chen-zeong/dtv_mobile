package dtv.mobile.state

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.repo.DtvRepository
import dtv.mobile.repo.fake.FakeDtvRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateFollowedRefreshTest {
  @Test
  fun refreshUpdatesExistingStreamerWithoutDroppingNewFollows() = runBlocking {
    val repository = DelayedFollowedRepository()
    val appState = AppState(
      repo = repository,
      subscriptionStore = EmptySubscriptionStore,
    )
    val original = streamer("1")
    val newlyFollowed = streamer("2")
    appState.toggleFollow(original)

    val refresh = launch { appState.refreshFollowedStreamerCards() }
    repository.started.await()
    appState.toggleFollow(newlyFollowed)
    repository.release.complete(Unit)
    refresh.join()

    assertEquals(listOf("1", "2"), appState.followedStreamers.map { it.roomId })
    assertEquals("刷新后的主播", appState.followedStreamers.first().name)
  }

  @Test
  fun refreshDoesNotRestoreStreamerRemovedWhileRequestIsRunning() = runBlocking {
    val repository = DelayedFollowedRepository()
    val appState = AppState(
      repo = repository,
      subscriptionStore = EmptySubscriptionStore,
    )
    val original = streamer("1")
    val newlyFollowed = streamer("2")
    appState.toggleFollow(original)

    val refresh = launch { appState.refreshFollowedStreamerCards() }
    repository.started.await()
    appState.toggleFollow(original)
    appState.toggleFollow(newlyFollowed)
    repository.release.complete(Unit)
    refresh.join()

    assertEquals(listOf("2"), appState.followedStreamers.map { it.roomId })
  }

  private fun streamer(roomId: String): Streamer {
    return Streamer(
      platform = Platform.Douyu,
      roomId = roomId,
      name = "主播 $roomId",
      title = "标题",
      viewerText = "",
    )
  }

  private class DelayedFollowedRepository : DtvRepository by FakeDtvRepository() {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun fetchFollowedStreamerSnapshot(streamer: Streamer): Streamer {
      started.complete(Unit)
      release.await()
      return streamer.copy(name = "刷新后的主播")
    }
  }

  private object EmptySubscriptionStore : SubscriptionStore by InMemorySubscriptionStore {
    override fun loadFollowedStreamers(): List<Streamer> = emptyList()

    override fun saveFollowedStreamers(items: List<Streamer>) = Unit

    override fun loadPinnedFollowedStreamerKeys(): List<String> = emptyList()

    override fun savePinnedFollowedStreamerKeys(items: List<String>) = Unit
  }
}
