package dtv.mobile.repo.fake

import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.repo.DouyuCate1
import dtv.mobile.repo.DouyuCate2
import dtv.mobile.repo.DouyuCate3
import dtv.mobile.repo.DouyuCategories
import dtv.mobile.repo.DouyuPlayInfo
import dtv.mobile.repo.DouyuPlayVariant
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.BilibiliQrCode
import dtv.mobile.repo.BilibiliQrPollResult
import dtv.mobile.repo.BilibiliQrStatus
import dtv.mobile.repo.DtvRepository
import dtv.mobile.repo.PagedResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.random.Random

class FakeDtvRepository : DtvRepository {
  override suspend fun searchAnchors(platform: Platform, keyword: String): List<Streamer> {
    if (keyword.isBlank()) return emptyList()
    return fakeRooms(platform = platform, start = 0, count = 12).map { it.copy(title = "搜索：${it.title}") }
  }

  override suspend fun fetchLiveStatus(streamer: Streamer): Boolean? {
    return streamer.isLive
  }

  override suspend fun fetchFollowedStreamerSnapshot(streamer: Streamer): Streamer? {
    return streamer
  }

  override suspend fun fetchHuyaCategories(): List<dtv.mobile.repo.HuyaCate1> {
    return listOf(
      dtv.mobile.repo.HuyaCate1(
        name = "网游竞技",
        cate2List = listOf(
          dtv.mobile.repo.HuyaCate2(gid = "1", name = "英雄联盟"),
          dtv.mobile.repo.HuyaCate2(gid = "862", name = "CS2"),
        ),
      ),
    )
  }

  override suspend fun fetchBilibiliCategories(): List<dtv.mobile.repo.BilibiliCate1> {
    return listOf(
      dtv.mobile.repo.BilibiliCate1(
        parentAreaId = 2,
        name = "网游",
        cate2List = listOf(
          dtv.mobile.repo.BilibiliCate2(areaId = 86, parentAreaId = 2, name = "英雄联盟"),
        ),
      ),
    )
  }

  override suspend fun fetchDouyinCategories(): List<dtv.mobile.repo.DouyinCate1> {
    return listOf(
      dtv.mobile.repo.DouyinCate1(
        name = "竞技游戏",
        cate2List = listOf(
          dtv.mobile.repo.DouyinCate2(partition = "1010014", partitionType = "1", name = "英雄联盟"),
          dtv.mobile.repo.DouyinCate2(partition = "1010045", partitionType = "1", name = "王者荣耀"),
        ),
      ),
    )
  }

  override suspend fun fetchDouyuCategories(): DouyuCategories {
    val cate1 = DouyuCate1(
      id = "1",
      name = "推荐",
      cate2List = listOf(
        DouyuCate2(id = "1", name = "推荐", shortName = "rec", iconUrl = null),
        DouyuCate2(id = "2", name = "娱乐", shortName = "yl", iconUrl = null),
        DouyuCate2(id = "3", name = "手游", shortName = "sy", iconUrl = null),
      ),
    )
    return DouyuCategories(cate1List = listOf(cate1))
  }

  override suspend fun fetchDouyuThreeCate(cate2Id: String): List<DouyuCate3> {
    return listOf(
      DouyuCate3(id = "$cate2Id-1", name = "子分类 A"),
      DouyuCate3(id = "$cate2Id-2", name = "子分类 B"),
    )
  }

  override suspend fun fetchDouyuLiveListByCate2(cate2Id: String, offset: Int, limit: Int): PagedResult<Streamer> {
    return PagedResult(items = fakeRooms(platform = Platform.Douyu, start = offset, count = limit), total = 999)
  }

  override suspend fun fetchDouyuLiveListByCate3(cate3Id: String, page: Int, limit: Int): PagedResult<Streamer> {
    val start = (page - 1).coerceAtLeast(0) * limit
    return PagedResult(items = fakeRooms(platform = Platform.Douyu, start = start, count = limit), total = null)
  }

  override suspend fun resolveDouyuStreamUrl(roomId: String, quality: String?, cdn: String?): String {
    return "https://example.invalid/stream/$roomId.m3u8"
  }

  override suspend fun fetchDouyuPlayInfo(roomId: String): DouyuPlayInfo {
    return DouyuPlayInfo(
      cdns = listOf("ws-h5", "tct-h5", "ali-h5"),
      variants = listOf(
        DouyuPlayVariant(name = "原画", rate = 0),
        DouyuPlayVariant(name = "高清", rate = 4),
        DouyuPlayVariant(name = "标清", rate = 3),
      ),
    )
  }

  override fun observeDouyuDanmaku(roomId: String): Flow<DanmakuMessage> = emptyFlow()

  override fun observeHuyaDanmaku(roomId: String): Flow<DanmakuMessage> = emptyFlow()

  override fun observeDouyinDanmaku(webRid: String): Flow<DanmakuMessage> = emptyFlow()

  override suspend fun fetchHuyaLiveList(gid: String, page: Int, limit: Int): PagedResult<Streamer> {
    val start = (page - 1).coerceAtLeast(0) * limit
    return PagedResult(items = fakeRooms(platform = Platform.Huya, start = start, count = limit), total = null)
  }

  override suspend fun resolveHuyaStreamUrl(roomId: String): String {
    return "https://example.invalid/huya/$roomId.m3u8"
  }

  override suspend fun fetchDouyinPartitionLiveList(
    partition: String,
    partitionType: String,
    offset: Int,
    limit: Int,
    msToken: String,
  ): PagedResult<Streamer> {
    return PagedResult(items = fakeRooms(platform = Platform.Douyin, start = offset, count = limit), total = null)
  }

  override suspend fun resolveDouyinStreamUrl(webRid: String, desiredQuality: String?): String {
    return "https://example.invalid/douyin/$webRid.m3u8"
  }

  override suspend fun fetchBilibiliLiveList(parentAreaId: Int, areaId: Int, page: Int, pageSize: Int): PagedResult<Streamer> {
    val start = (page - 1).coerceAtLeast(0) * pageSize
    return PagedResult(items = fakeRooms(platform = Platform.Bilibili, start = start, count = pageSize), total = null)
  }

  override suspend fun resolveBilibiliStreamUrl(roomId: String, qn: Int?): String {
    return "https://example.invalid/bilibili/$roomId.m3u8"
  }

  override fun observeBilibiliDanmaku(roomId: String): Flow<DanmakuMessage> = emptyFlow()

  override suspend fun generateBilibiliQrCode(): BilibiliQrCode {
    return BilibiliQrCode(url = "https://example.invalid/bili-qr", qrcodeKey = "fake-key")
  }

  override suspend fun pollBilibiliQrCode(qrcodeKey: String): BilibiliQrPollResult {
    return BilibiliQrPollResult(status = BilibiliQrStatus.Waiting, message = "Fake")
  }

  override suspend fun getBilibiliCookie(): String? = null

  override suspend fun clearBilibiliCookie() {}

  private fun fakeRooms(platform: Platform, start: Int, count: Int): List<Streamer> {
    val titles = listOf(
      "深夜高能整活，来就对了",
      "上分冲冲冲｜双倍快乐",
      "手把手教学｜今天必胜",
      "新版本开荒｜全程讲解",
      "随便播播｜陪你摸鱼",
    )
    return (0 until count).map { i ->
      val idx = start + i + 1
      Streamer(
        platform = platform,
        roomId = idx.toString(),
        name = "${platform.title} 主播 $idx",
        title = titles[idx % titles.size],
        viewerText = "${Random.nextInt(2000, 220000)} 人",
        avatarUrl = null,
        coverUrl = null,
        isLive = true,
      )
    }
  }
}
