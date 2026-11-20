package dtv.mobile.platform.douyu

import dtv.mobile.repo.DanmakuMessage
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class DouyuDecodedBatch(
  val messages: List<DanmakuMessage>,
  val malformed: Boolean,
)

internal object DouyuDanmakuCodecAndroid {
  private const val HEADER_SIZE = 12
  private const val TRAILER_SIZE = 1

  fun encodeClientMessage(message: String): ByteArray {
    val messageBytes = message.toByteArray(Charsets.UTF_8)
    val declaredLength = messageBytes.size + 9
    val buffer = ByteBuffer.allocate(4 + messageBytes.size + 9)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(declaredLength)
    buffer.putInt(declaredLength)
    buffer.putShort(689.toShort())
    buffer.put(0)
    buffer.put(0)
    buffer.put(messageBytes)
    buffer.put(0)
    return buffer.array()
  }

  fun decodeChatMessages(data: ByteArray, roomId: String): DouyuDecodedBatch {
    val messages = ArrayList<DanmakuMessage>()
    var offset = 0
    var malformed = false

    while (offset + 4 <= data.size) {
      val declaredLength = ByteBuffer.wrap(data, offset, 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
      val packetSize = declaredLength + 4
      if (packetSize < HEADER_SIZE + TRAILER_SIZE || offset + packetSize > data.size) {
        malformed = true
        break
      }

      val payloadStart = offset + HEADER_SIZE
      val payloadEnd = offset + packetSize - TRAILER_SIZE
      if (payloadEnd > payloadStart) {
        val payload = data.copyOfRange(payloadStart, payloadEnd)
        parseChatMessage(payload, roomId)?.let(messages::add)
      }
      offset += packetSize
    }

    if (offset != data.size) malformed = true
    return DouyuDecodedBatch(messages = messages, malformed = malformed)
  }

  private fun parseChatMessage(payload: ByteArray, roomId: String): DanmakuMessage? {
    val text = payload.toString(Charsets.UTF_8)
    val map = parseMap(text)
    if (map["type"] != "chatmsg") return null
    return DanmakuMessage(
      roomId = roomId,
      user = map["nn"].orEmpty().ifBlank { "匿名" },
      content = map["txt"].orEmpty(),
      userLevel = map["level"]?.toIntOrNull() ?: 0,
      fansClubLevel = map["bl"]?.toIntOrNull() ?: 0,
      color = map["col"],
    )
  }

  private fun parseMap(content: String): Map<String, String> {
    val map = HashMap<String, String>(16)
    content.split('/').forEach { item ->
      if (item.isBlank()) return@forEach
      val separator = item.indexOf("@=")
      if (separator <= 0) return@forEach
      val key = item.substring(0, separator)
      val value = item.substring(separator + 2)
        .replace("@S", "/")
        .replace("@A", "@")
      map[key] = value
    }
    return map
  }
}
