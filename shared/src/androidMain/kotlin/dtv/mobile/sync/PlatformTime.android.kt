package dtv.mobile.sync

import java.time.Instant

actual fun currentIsoTimestamp(): String = Instant.now().toString()

