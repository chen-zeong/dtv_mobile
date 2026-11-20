package dtv.mobile.platform.douyu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DouyuBetardResponse(
  val room: DouyuBetardRoom? = null,
)

@Serializable
data class DouyuBetardRoom(
  @SerialName("room_id") val roomId: JsonElement? = null,
  @SerialName("show_status") val showStatus: JsonElement? = null,
  @SerialName("videoLoop") val videoLoop: JsonElement? = null,
  @SerialName("video_loop") val videoLoopSnakeCase: JsonElement? = null,
  @SerialName("isLoop") val isLoop: JsonElement? = null,
  @SerialName("is_loop") val isLoopSnakeCase: JsonElement? = null,
)

@Serializable
data class DouyuHomeH5EncResponse(
  val error: Int = -1,
  val msg: String? = null,
  val data: Map<String, String>? = null,
)

@Serializable
data class DouyuH5PlayResponse(
  val error: Int = -1,
  val msg: String? = null,
  val data: DouyuH5PlayData? = null,
)

@Serializable
data class DouyuH5PlayData(
  @SerialName("rtmp_url") val rtmpUrl: String? = null,
  @SerialName("rtmp_live") val rtmpLive: String? = null,
  @SerialName("cdnsWithName") val cdnsWithName: List<DouyuCdnItem>? = null,
  @SerialName("multirates") val multirates: List<DouyuRateItem>? = null,
)

@Serializable
data class DouyuCdnItem(
  val cdn: String? = null,
)

@Serializable
data class DouyuRateItem(
  val name: String? = null,
  val rate: Int? = null,
  val bit: Int? = null,
)

