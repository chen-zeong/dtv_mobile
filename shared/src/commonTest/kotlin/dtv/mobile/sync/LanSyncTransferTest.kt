package dtv.mobile.sync

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LanSyncTransferTest {
  @Test
  fun rejectsUnexpectedPayloadKind() {
    assertFailsWith<IllegalArgumentException> {
      validateLanSyncPayload(payload(kind = "other"))
    }
  }

  @Test
  fun rejectsUnsupportedPayloadVersion() {
    assertFailsWith<IllegalArgumentException> {
      validateLanSyncPayload(payload(version = LAN_SYNC_VERSION + 1))
    }
  }

  private fun payload(
    kind: String = LAN_SYNC_KIND,
    version: Int = LAN_SYNC_VERSION,
  ): LanSyncPayload {
    return LanSyncPayload(
      kind = kind,
      version = version,
      exportedAt = "2026-11-20T00:00:00Z",
      source = LanSyncSource(client = "test"),
      entries = emptyMap(),
    )
  }
}
