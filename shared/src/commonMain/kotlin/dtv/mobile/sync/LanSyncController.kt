package dtv.mobile.sync

import androidx.compose.runtime.Composable

interface LanSyncController {
  suspend fun discoverPeers(timeoutMs: Long = 1200): List<LanSyncDiscoveredPeer>
  suspend fun startServer(payload: LanSyncPayload): LanSyncServerInfo
  suspend fun stopServer()
  suspend fun serverStatus(): LanSyncServerInfo?
}

@Composable
expect fun rememberLanSyncController(): LanSyncController

