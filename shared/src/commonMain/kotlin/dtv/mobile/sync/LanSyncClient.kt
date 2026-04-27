package dtv.mobile.sync

import dtv.mobile.net.createHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

object LanSyncClient {
  suspend fun fetchManifest(baseUrl: String, token: String): LanSyncManifest {
    val client = createHttpClient()
    try {
      return client.get("$baseUrl$LAN_SYNC_HTTP_PATH") {
        parameter("token", token)
      }.body()
    } finally {
      client.close()
    }
  }

  suspend fun fetchPayload(baseUrl: String, token: String): LanSyncPayload {
    val client = createHttpClient()
    try {
      return client.get("$baseUrl$LAN_SYNC_PAYLOAD_PATH") {
        parameter("token", token)
      }.body()
    } finally {
      client.close()
    }
  }
}

