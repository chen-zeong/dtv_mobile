package dtv.mobile.repo

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import kotlinx.coroutines.flow.Flow

data class DouyuCate1(
  val id: String,
  val name: String,
  val cate2List: List<DouyuCate2>,
)

data class DouyuCate2(
  val id: String,
  val name: String,
  val shortName: String,
  val iconUrl: String?,
)

data class DouyuCate3(
  val id: String,
  val name: String,
  val iconUrl: String? = null,
)

data class DouyuCategories(
  val cate1List: List<DouyuCate1>,
)

data class PagedResult<T>(
  val items: List<T>,
  val total: Int? = null,
)

data class DouyuPlayVariant(
  val name: String,
  val rate: Int,
  val bit: Int? = null,
)

data class DouyuPlayInfo(
  val cdns: List<String>,
  val variants: List<DouyuPlayVariant>,
)

data class DanmakuMessage(
  val roomId: String,
  val user: String,
  val content: String,
  val userLevel: Int = 0,
  val fansClubLevel: Int = 0,
  val color: String? = null,
)

data class BilibiliQrCode(
  val url: String,
  val qrcodeKey: String,
)

enum class BilibiliQrStatus { Waiting, Scanned, Confirmed, Expired, Failed }

data class BilibiliQrPollResult(
  val status: BilibiliQrStatus,
  val message: String? = null,
)

interface DtvRepository {
  suspend fun searchAnchors(platform: Platform, keyword: String): List<Streamer>

  /**
   * Fetch live status for an existing streamer.
   *
   * Returns null when the platform doesn't support querying or the request fails.
   */
  suspend fun fetchLiveStatus(streamer: Streamer): Boolean?

  /**
   * Refresh a followed streamer card snapshot (avatar/nickname/title/viewers/live).
   *
   * Returns null when the platform doesn't support querying or the request fails.
   */
  suspend fun fetchFollowedStreamerSnapshot(streamer: Streamer): Streamer?

  suspend fun fetchHuyaCategories(): List<HuyaCate1>

  suspend fun fetchBilibiliCategories(): List<BilibiliCate1>

  suspend fun fetchDouyinCategories(): List<DouyinCate1>

  suspend fun fetchDouyuCategories(): DouyuCategories

  suspend fun fetchDouyuThreeCate(cate2Id: String): List<DouyuCate3>

  suspend fun fetchDouyuLiveListByCate2(cate2Id: String, offset: Int, limit: Int): PagedResult<Streamer>

  suspend fun fetchDouyuLiveListByCate3(cate3Id: String, page: Int, limit: Int): PagedResult<Streamer>

  suspend fun fetchDouyuPlayInfo(roomId: String): DouyuPlayInfo

  suspend fun resolveDouyuStreamUrl(roomId: String, quality: String? = null, cdn: String? = null): String

  fun observeDouyuDanmaku(roomId: String): Flow<DanmakuMessage>

  fun observeHuyaDanmaku(roomId: String): Flow<DanmakuMessage>

  fun observeDouyinDanmaku(webRid: String): Flow<DanmakuMessage>

  suspend fun fetchHuyaLiveList(gid: String, page: Int, limit: Int): PagedResult<Streamer>

  suspend fun resolveHuyaStreamUrl(roomId: String): String

  suspend fun fetchDouyinPartitionLiveList(
    partition: String,
    partitionType: String,
    offset: Int,
    limit: Int,
    msToken: String,
  ): PagedResult<Streamer>

  suspend fun resolveDouyinStreamUrl(webRid: String, desiredQuality: String? = null): String

  suspend fun fetchBilibiliLiveList(parentAreaId: Int, areaId: Int, page: Int, pageSize: Int): PagedResult<Streamer>

  suspend fun resolveBilibiliStreamUrl(roomId: String, qn: Int? = null): String

  fun observeBilibiliDanmaku(roomId: String): Flow<DanmakuMessage>

  suspend fun generateBilibiliQrCode(): BilibiliQrCode

  suspend fun pollBilibiliQrCode(qrcodeKey: String): BilibiliQrPollResult

  suspend fun getBilibiliCookie(): String?

  suspend fun clearBilibiliCookie()
}
