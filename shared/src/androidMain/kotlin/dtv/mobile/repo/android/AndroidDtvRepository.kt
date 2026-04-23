package dtv.mobile.repo.android

import android.content.Context
import dtv.mobile.util.AppLog
import dtv.mobile.model.Platform
import dtv.mobile.model.Streamer
import dtv.mobile.net.createHttpClient
import dtv.mobile.platform.bilibili.BilibiliAuthApiAndroid
import dtv.mobile.platform.bilibili.BilibiliCookieStoreAndroid
import dtv.mobile.platform.bilibili.BilibiliDanmakuClientAndroid
import dtv.mobile.platform.bilibili.BilibiliLiveListApiAndroid
import dtv.mobile.platform.bilibili.BilibiliSearchApiAndroid
import dtv.mobile.platform.bilibili.BilibiliStreamUrlResolverAndroid
import dtv.mobile.platform.douyin.DouyinWebApiAndroid
import dtv.mobile.platform.douyin.DouyinDanmakuClientAndroid
import dtv.mobile.platform.douyu.DouyuCate2DirectoryApi
import dtv.mobile.platform.douyu.DouyuCate3DirectoryApi
import dtv.mobile.platform.douyu.DouyuCategoriesApi
import dtv.mobile.platform.douyu.DouyuMobileApi
import dtv.mobile.platform.douyu.DouyuDanmakuClientAndroid
import dtv.mobile.platform.douyu.DouyuSearchApiAndroid
import dtv.mobile.platform.douyu.DouyuThreeCateApi
import dtv.mobile.platform.douyu.DouyuStreamUrlResolverAndroid
import dtv.mobile.platform.huya.HuyaDanmakuClientAndroid
import dtv.mobile.platform.huya.HuyaLiveListApiAndroid
import dtv.mobile.platform.huya.HuyaSearchApiAndroid
import dtv.mobile.platform.huya.HuyaStreamUrlResolverAndroid
import dtv.mobile.repo.BilibiliCate1
import dtv.mobile.repo.BilibiliCate2
import dtv.mobile.repo.BilibiliQrCode
import dtv.mobile.repo.BilibiliQrPollResult
import dtv.mobile.repo.DouyinCate1
import dtv.mobile.repo.DouyinCate2
import dtv.mobile.repo.DouyuCate1
import dtv.mobile.repo.DouyuCate2
import dtv.mobile.repo.DouyuCate3
import dtv.mobile.repo.DouyuCategories
import dtv.mobile.repo.DouyuPlayInfo
import dtv.mobile.repo.DtvRepository
import dtv.mobile.repo.DanmakuMessage
import dtv.mobile.repo.HuyaCate1
import dtv.mobile.repo.HuyaCate2
import dtv.mobile.repo.PagedResult
import dtv.mobile.repo.fake.FakeDtvRepository
import dtv.mobile.util.formatViewerCountWanIfNeeded
import dtv.mobile.util.normalizeHttpUrl
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class AndroidDtvRepository(
  private val appContext: Context,
) : DtvRepository {
  private val client = createHttpClient()
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private val douyuMobileApi = DouyuMobileApi(client)
  private val douyuCategoriesApi = DouyuCategoriesApi(client)
  private val douyuThreeCateApi = DouyuThreeCateApi(client)
  private val douyuCate2DirectoryApi = DouyuCate2DirectoryApi(client)
  private val douyuCate3DirectoryApi = DouyuCate3DirectoryApi(client)
  private val douyuStreamResolver = DouyuStreamUrlResolverAndroid(appContext, client)
  private val douyuDanmakuClient = DouyuDanmakuClientAndroid()
  private val douyuSearchApi = DouyuSearchApiAndroid(client)

  private val huyaLiveListApi = HuyaLiveListApiAndroid(client)
  private val huyaStreamResolver = HuyaStreamUrlResolverAndroid(client)
  private val huyaDanmakuClient = HuyaDanmakuClientAndroid()
  private val huyaSearchApi = HuyaSearchApiAndroid(client)

  private val douyinWebApi = DouyinWebApiAndroid(client)
  private val douyinDanmakuClient = DouyinDanmakuClientAndroid(appContext, douyinWebApi)

  private val bilibiliCookieStore = BilibiliCookieStoreAndroid(appContext)
  private val bilibiliAuthApi = BilibiliAuthApiAndroid(client, bilibiliCookieStore)
  private val bilibiliLiveListApi = BilibiliLiveListApiAndroid(client)
  private val bilibiliStreamResolver = BilibiliStreamUrlResolverAndroid(client) { bilibiliCookieStore.getCookie() }
  private val bilibiliSearchApi = BilibiliSearchApiAndroid(client, bilibiliCookieStore)
  private val bilibiliDanmakuClient = BilibiliDanmakuClientAndroid(
    httpClient = client,
    cookieProvider = { bilibiliCookieStore.getCookie() },
  )

  private val fallback = FakeDtvRepository()

  private fun readAssetText(path: String): String {
    return appContext.assets.open(path).bufferedReader().use { it.readText() }
  }

  private fun JsonElement?.stringValueOrNull(): String? {
    val p = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
  }

  override suspend fun searchAnchors(platform: Platform, keyword: String): List<Streamer> {
    val trimmed = keyword.trim()
    if (trimmed.isEmpty()) return emptyList()
    return when (platform) {
      Platform.Douyu -> runCatching { douyuSearchApi.searchAnchors(trimmed) }.getOrElse { emptyList() }
      Platform.Huya -> runCatching { huyaSearchApi.searchAnchors(trimmed) }.getOrElse { emptyList() }
      Platform.Bilibili -> runCatching { bilibiliSearchApi.searchRooms(trimmed) }.getOrElse { emptyList() }
      Platform.Douyin -> runCatching {
        val info = douyinWebApi.fetchRoomEnter(trimmed)
        val title = info.title?.trim().orEmpty().ifBlank { "直播中" }
        val name = info.anchorName?.trim().orEmpty().ifBlank { "抖音主播" }
        val isLive = info.hlsPullUrlMap.isNotEmpty() || info.flvPullUrlMap.isNotEmpty()
        listOf(
          Streamer(
            platform = Platform.Douyin,
            roomId = trimmed,
            name = name,
            title = title,
            viewerText = "",
            avatarUrl = normalizeHttpUrl(info.avatarUrl),
            coverUrl = null,
            isLive = isLive,
          ),
        )
      }.getOrElse { emptyList() }
      else -> emptyList()
    }
  }

  override suspend fun fetchLiveStatus(streamer: Streamer): Boolean? {
    return when (streamer.platform) {
      Platform.Douyu -> runCatching { douyuStreamResolver.isLive(streamer.roomId) }.getOrNull()
      Platform.Huya -> runCatching { huyaStreamResolver.isLive(streamer.roomId) }.getOrNull()
      Platform.Douyin -> runCatching {
        val info = douyinWebApi.fetchRoomEnter(streamer.roomId)
        info.hlsPullUrlMap.isNotEmpty() || info.flvPullUrlMap.isNotEmpty()
      }.getOrNull()
      Platform.Bilibili -> runCatching { fetchBilibiliLiveStatus(streamer.roomId) }.getOrNull()
      else -> null
    }
  }

  override suspend fun fetchFollowedStreamerSnapshot(streamer: Streamer): Streamer? {
    val roomId = streamer.roomId.trim()
    if (roomId.isBlank()) return null

    return when (streamer.platform) {
      Platform.Douyu -> runCatching {
        val info = fetchDouyuFollowInfo(roomId)
        streamer.copy(
          name = info.name ?: streamer.name,
          title = info.title ?: streamer.title,
          viewerText = info.viewerText ?: streamer.viewerText,
          avatarUrl = info.avatarUrl ?: streamer.avatarUrl,
          coverUrl = info.coverUrl ?: streamer.coverUrl,
          isLive = info.isLive ?: streamer.isLive,
        )
      }.getOrNull()

      Platform.Huya -> runCatching {
        val info = fetchHuyaFollowInfo(roomId)
        streamer.copy(
          name = info.name ?: streamer.name,
          title = info.title ?: streamer.title,
          viewerText = info.viewerText ?: streamer.viewerText,
          avatarUrl = info.avatarUrl ?: streamer.avatarUrl,
          coverUrl = info.coverUrl ?: streamer.coverUrl,
          isLive = info.isLive ?: streamer.isLive,
        )
      }.getOrNull()

      Platform.Bilibili -> runCatching {
        val info = fetchBilibiliFollowInfo(roomId)
        streamer.copy(
          name = info.name ?: streamer.name,
          title = info.title ?: streamer.title,
          viewerText = info.viewerText ?: streamer.viewerText,
          avatarUrl = info.avatarUrl ?: streamer.avatarUrl,
          coverUrl = info.coverUrl ?: streamer.coverUrl,
          isLive = info.isLive ?: streamer.isLive,
        )
      }.getOrNull()

      Platform.Douyin -> runCatching {
        val info = douyinWebApi.fetchRoomEnter(roomId)
        val isLive = (info.hlsPullUrlMap.isNotEmpty() || info.flvPullUrlMap.isNotEmpty())
        streamer.copy(
          name = info.anchorName?.trim()?.ifBlank { null } ?: streamer.name,
          title = info.title?.trim()?.ifBlank { null } ?: streamer.title,
          viewerText = info.viewerCountStr?.trim()?.ifBlank { null } ?: streamer.viewerText,
          avatarUrl = normalizeHttpUrl(info.avatarUrl) ?: streamer.avatarUrl,
          coverUrl = normalizeHttpUrl(info.coverUrl) ?: streamer.coverUrl,
          isLive = isLive,
        )
      }.getOrNull()

      else -> null
    }
  }

  private data class FollowInfo(
    val name: String?,
    val title: String?,
    val viewerText: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val isLive: Boolean?,
  )

  private suspend fun fetchDouyuFollowInfo(roomId: String): FollowInfo {
    val url = "https://www.douyu.com/betard/$roomId"
    val text = client.get(url) {
      headers {
        append("Accept", "application/json, text/plain, */*")
        append("Accept-Language", "zh-CN,zh;q=0.9")
        append("Cache-Control", "no-cache")
        append("Pragma", "no-cache")
        append("Referer", "https://www.douyu.com/$roomId")
        append(
          "User-Agent",
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        )
      }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val roomObj = (root["data"]?.jsonObject?.get("room")?.jsonObject)
      ?: (root["room"]?.jsonObject)
      ?: (root["data"]?.jsonObject)
      ?: root

    val title = roomObj["room_name"].stringValueOrNull()?.trim()?.ifBlank { null }
    val name = roomObj["nickname"].stringValueOrNull()?.trim()?.ifBlank { null }
    val avatar = normalizeHttpUrl(
      roomObj["avatar_mid"].stringValueOrNull()
        ?: roomObj["avatarMid"].stringValueOrNull()
        ?: roomObj["avatar"].stringValueOrNull(),
    )

    fun normalizeDouyuCover(raw: String?): String? {
      val v = raw?.trim().orEmpty()
      if (v.isBlank()) return null
      return when {
        v.startsWith("http://") || v.startsWith("https://") -> v
        v.startsWith("//") -> "https:$v"
        v.startsWith("/") -> "https://rpic.douyucdn.cn$v"
        else -> "https://rpic.douyucdn.cn/$v"
      }
    }

    // `room_src` is often a relative path like `asrpic/...` which won't render if used directly.
    // Prefer absolute keys first (`coverSrc` / `room_pic`), then normalize `room_src` as rpic CDN path.
    val cover = normalizeDouyuCover(
      roomObj["coverSrc"].stringValueOrNull()
        ?: roomObj["room_pic"].stringValueOrNull()
        ?: roomObj["roomPic"].stringValueOrNull()
        ?: roomObj["room_src"].stringValueOrNull()
        ?: roomObj["roomSrc"].stringValueOrNull()
        ?: roomObj["room_thumb"].stringValueOrNull()
        ?: roomObj["roomThumb"].stringValueOrNull(),
    )?.let(::normalizeHttpUrl)

    val showStatus = roomObj["show_status"]?.jsonPrimitive?.intOrNull
    val videoLoop = roomObj["videoLoop"]?.jsonPrimitive?.intOrNull
    val liveStatus: Boolean? = if (showStatus == 1) {
      when (videoLoop) {
        0 -> true
        1 -> false
        else -> null
      }
    } else if (showStatus == 2 || showStatus == 0) {
      false
    } else {
      null
    }
    val isLive = liveStatus ?: runCatching { douyuStreamResolver.isLive(roomId) }.getOrNull()

    val online = (
      roomObj["iol"].stringValueOrNull()
        ?: roomObj["ol"].stringValueOrNull()
        ?: roomObj["online"].stringValueOrNull()
        ?: roomObj["hn"].stringValueOrNull()
    )?.trim()?.ifBlank { null }

    val hot = roomObj["room_biz_all"]?.jsonObject?.get("hot").stringValueOrNull()?.trim()?.ifBlank { null }
    val viewerText = when {
      online.isNullOrBlank() -> hot
      online == "0" && !hot.isNullOrBlank() -> hot
      else -> online
    }
    return FollowInfo(
      name = name,
      title = title,
      viewerText = viewerText?.takeIf { it.isNotBlank() },
      avatarUrl = avatar,
      coverUrl = cover,
      isLive = isLive,
    )
  }

  private suspend fun fetchHuyaFollowInfo(roomId: String): FollowInfo {
    val url = "https://mp.huya.com/cache.php?m=Live&do=profileRoom&roomid=$roomId"
    val text = client.get(url) {
      headers { append("User-Agent", "Mozilla/5.0") }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val data = root["data"]?.jsonObject ?: return FollowInfo(null, null, null, null, null, null)

    val profile = data["profileInfo"]?.jsonObject
    val liveData = data["liveData"]?.jsonObject

    val name = profile?.get("nick").stringValueOrNull()?.trim()?.ifBlank { null }
    val avatar = normalizeHttpUrl(profile?.get("avatar180").stringValueOrNull() ?: liveData?.get("avatar180").stringValueOrNull())
    val title = liveData?.get("introduction").stringValueOrNull()?.trim()?.ifBlank { null }
    val cover = normalizeHttpUrl(liveData?.get("screenshot").stringValueOrNull() ?: liveData?.get("sScreenshot").stringValueOrNull())

    val liveStatus = data["liveStatus"].stringValueOrNull()?.trim()?.uppercase()
    val isLive = when (liveStatus) {
      "ON" -> true
      "OFF" -> false
      else -> runCatching { huyaStreamResolver.isLive(roomId) }.getOrNull()
    }

    val totalCount = liveData?.get("totalCount")?.jsonPrimitive?.longOrNull?.toString()
    return FollowInfo(
      name = name,
      title = title,
      viewerText = totalCount?.takeIf { it.isNotBlank() },
      avatarUrl = avatar,
      coverUrl = cover,
      isLive = isLive,
    )
  }

  private suspend fun fetchBilibiliFollowInfo(roomId: String): FollowInfo {
    val url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getH5InfoByRoom?room_id=$roomId"
    val text = client.get(url) {
      headers {
        append("User-Agent", "Mozilla/5.0")
        append("Referer", "https://live.bilibili.com/")
      }
    }.bodyAsText()

    val root = json.parseToJsonElement(text).jsonObject
    val data = root["data"]?.jsonObject ?: return FollowInfo(null, null, null, null, null, null)
    val roomInfo = data["room_info"]?.jsonObject
    val anchorBase = data["anchor_info"]?.jsonObject?.get("base_info")?.jsonObject

    val title = roomInfo?.get("title").stringValueOrNull()?.trim()?.ifBlank { null }
    val name = anchorBase?.get("uname").stringValueOrNull()?.trim()?.ifBlank { null }
    val avatar = normalizeHttpUrl(anchorBase?.get("face").stringValueOrNull())
    val cover = normalizeHttpUrl(
      roomInfo?.get("keyframe").stringValueOrNull()
        ?: roomInfo?.get("cover").stringValueOrNull()
        ?: roomInfo?.get("user_cover").stringValueOrNull(),
    )

    val liveStatus = roomInfo?.get("live_status")?.jsonPrimitive?.intOrNull
    val isLive = liveStatus?.let { it == 1 }
    val online = roomInfo?.get("online")?.jsonPrimitive?.longOrNull?.toString()

    return FollowInfo(
      name = name,
      title = title,
      viewerText = online?.takeIf { it.isNotBlank() },
      avatarUrl = avatar,
      coverUrl = cover,
      isLive = isLive,
    )
  }

  private suspend fun fetchBilibiliLiveStatus(roomId: String): Boolean {
    val url = "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomId"
    val text = client.get(url).bodyAsText()
    val obj = json.parseToJsonElement(text).jsonObject
    val data = obj["data"]?.jsonObject ?: return false
    val status = data["live_status"]?.jsonPrimitive?.intOrNull ?: 0
    return status == 1
  }

  override suspend fun fetchHuyaCategories(): List<HuyaCate1> {
    return runCatching {
      val text = readAssetText("categories/huya_categories.json")
      val root = json.parseToJsonElement(text)
      val arr = root as? JsonArray ?: return emptyList()
      arr.mapNotNull { c1El ->
        val obj = c1El.jsonObject
        val name = obj["title"].stringValueOrNull()?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        val href = obj["href"].stringValueOrNull()
        val sub = obj["subcategories"]?.jsonArray.orEmpty().mapNotNull { c2El ->
          val c2Obj = c2El.jsonObject
          val gid = c2Obj["id"].stringValueOrNull()?.trim().orEmpty()
          val c2Name = c2Obj["title"].stringValueOrNull()?.trim().orEmpty()
          if (gid.isBlank() || c2Name.isBlank()) return@mapNotNull null
          HuyaCate2(
            gid = gid,
            name = c2Name,
            href = c2Obj["href"].stringValueOrNull(),
          )
        }
        HuyaCate1(name = name, href = href, cate2List = sub)
      }.filter { it.cate2List.isNotEmpty() }
    }.getOrElse { emptyList() }
  }

  override suspend fun fetchBilibiliCategories(): List<BilibiliCate1> {
    return runCatching {
      val text = readAssetText("categories/bilibili_categories.json")
      val root = json.parseToJsonElement(text)
      val arr = root as? JsonArray ?: return emptyList()

      val list = arr.mapNotNull { c1El ->
        val obj = c1El.jsonObject
        val parentId = obj["id"]?.jsonPrimitive?.intOrNull ?: obj["id"].stringValueOrNull()?.toIntOrNull() ?: return@mapNotNull null
        val name = obj["title"].stringValueOrNull()?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        val href = obj["href"].stringValueOrNull()
        val sub = obj["subcategories"]?.jsonArray.orEmpty().mapNotNull { c2El ->
          val c2Obj = c2El.jsonObject
          val areaId = c2Obj["id"].stringValueOrNull()?.toIntOrNull()
            ?: c2Obj["id"]?.jsonPrimitive?.intOrNull
            ?: return@mapNotNull null
          val c2Name = c2Obj["title"].stringValueOrNull()?.trim().orEmpty()
          if (c2Name.isBlank()) return@mapNotNull null
          BilibiliCate2(
            areaId = areaId,
            parentAreaId = c2Obj["parent_id"].stringValueOrNull()?.toIntOrNull() ?: parentId,
            name = c2Name,
            href = c2Obj["href"].stringValueOrNull(),
          )
        }
        BilibiliCate1(parentAreaId = parentId, name = name, href = href, cate2List = sub)
      }.filter { it.cate2List.isNotEmpty() }
      list
    }.getOrElse { emptyList() }
  }

  override suspend fun fetchDouyinCategories(): List<DouyinCate1> {
    return runCatching {
      val text = readAssetText("categories/douyin_categories.json")
      val root = json.parseToJsonElement(text)
      val arr = root as? JsonArray ?: return emptyList()

      fun parseHrefToPartition(href: String): Pair<String, String>? {
        val parts = href.split("_")
        if (parts.size < 2) return null
        val partition = parts.last().trim()
        val partitionType = parts[parts.size - 2].trim()
        if (partition.isEmpty() || partitionType.isEmpty()) return null
        if (partition.toLongOrNull() == null || partitionType.toLongOrNull() == null) return null
        return partition to partitionType
      }

      arr.mapNotNull { c1El ->
        val obj = c1El.jsonObject
        val name = obj["title"].stringValueOrNull()?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        val href = obj["href"].stringValueOrNull()
        val sub = obj["subcategories"]?.jsonArray.orEmpty().mapNotNull { c2El ->
          val c2Obj = c2El.jsonObject
          val c2Name = c2Obj["title"].stringValueOrNull()?.trim().orEmpty()
          val c2Href = c2Obj["href"].stringValueOrNull()?.trim().orEmpty()
          if (c2Name.isBlank() || c2Href.isBlank()) return@mapNotNull null
          val parsed = parseHrefToPartition(c2Href) ?: return@mapNotNull null
          DouyinCate2(
            partition = parsed.first,
            partitionType = parsed.second,
            name = c2Name,
            href = c2Href,
          )
        }
        DouyinCate1(name = name, href = href, cate2List = sub)
      }.filter { it.cate2List.isNotEmpty() }
    }.getOrElse { emptyList() }
  }

  override suspend fun fetchDouyuCategories(): DouyuCategories {
    return runCatching {
      val resp = douyuCategoriesApi.fetchCateList()
      if (resp.code != 0) return fallback.fetchDouyuCategories()
      val data = resp.data ?: return fallback.fetchDouyuCategories()

      val cate2ByCate1 = data.cate2Info.groupBy { it.cate1Id }
      val cate1List = data.cate1Info.map { c1 ->
        val rawC2 = cate2ByCate1[c1.cate1Id].orEmpty()
        val c2List = rawC2.map { c2 ->
          DouyuCate2(
            id = c2.cate2Id.toString(),
            name = c2.cate2Name,
            shortName = c2.shortName,
            iconUrl = c2.icon,
          )
        }.toMutableList()

        // 与桌面端 Rust 保持一致：娱乐天地(cate1Id=2) 下硬编码加入“一起看”
        if (c1.cate1Id == 2) {
          c2List.add(
            DouyuCate2(
              id = "208",
              name = "一起看",
              shortName = "yqk",
              iconUrl = "https://sta-op.douyucdn.cn/dycatr/7c723d30bfb4399be7592c9fa12026e3.png",
            ),
          )
        }

        DouyuCate1(
          id = c1.cate1Id.toString(),
          name = c1.cate1Name,
          cate2List = c2List,
        )
      }

      DouyuCategories(cate1List = cate1List)
    }.getOrElse { fallback.fetchDouyuCategories() }
  }

  override suspend fun fetchDouyuThreeCate(cate2Id: String): List<DouyuCate3> {
    return runCatching {
      val resp = douyuThreeCateApi.fetchThreeCate(cate2Id)
      if (resp.error != 0) return emptyList()
      resp.data.orEmpty()
        .mapNotNull { raw ->
          val id = raw.id.trim()
          val name = raw.name.trim()
          if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
          DouyuCate3(id = id, name = name, iconUrl = raw.iconResolved)
        }
    }.getOrDefault(emptyList())
  }

  override suspend fun fetchDouyuLiveListByCate2(cate2Id: String, offset: Int, limit: Int): PagedResult<Streamer> {
    return runCatching {
      // Douyu 的 `m.douyu.com/hgapi/.../newRecList` 近期经常返回 “系统异常”，改用网页目录接口 mixListV1 更稳定。
      val page = (offset / limit) + 1
      val resp = douyuCate2DirectoryApi.fetchMixListV1(cate2Id = cate2Id, page = page, limit = limit)
      if (resp.code != 0) return PagedResult(emptyList(), total = null)
      val data = resp.data ?: return PagedResult(emptyList(), total = null)
      val items = data.rl.map { s ->
        Streamer(
          platform = Platform.Douyu,
          roomId = s.rid.toString(),
          name = s.nn,
          title = s.rn,
          viewerText = formatViewerCountWanIfNeeded(s.ol.toString()),
          avatarUrl = normalizeHttpUrl(s.av.ifBlank { null }),
          coverUrl = normalizeHttpUrl(s.rs16.ifBlank { null }),
          isLive = s.type?.let { it == 1 } ?: true,
        )
      }
      PagedResult(items = items, total = null)
    }.getOrElse {
      PagedResult(items = emptyList(), total = null)
    }
  }

  override suspend fun fetchDouyuLiveListByCate3(cate3Id: String, page: Int, limit: Int): PagedResult<Streamer> {
    return runCatching {
      val resp = douyuCate3DirectoryApi.fetchMixListV1(cate3Id = cate3Id, page = page, limit = limit)
      if (resp.code != 0) return PagedResult(emptyList(), total = null)
      val data = resp.data ?: return PagedResult(emptyList(), total = null)
      val items = data.rl.map { s ->
        Streamer(
          platform = Platform.Douyu,
          roomId = s.rid.toString(),
          name = s.nn,
          title = s.rn,
          viewerText = formatViewerCountWanIfNeeded(s.ol.toString()),
          avatarUrl = normalizeHttpUrl(s.av.ifBlank { null }),
          coverUrl = normalizeHttpUrl(s.rs16.ifBlank { null }),
          isLive = s.type?.let { it == 1 } ?: true,
        )
      }
      PagedResult(items = items, total = null)
    }.getOrElse {
      PagedResult(items = emptyList(), total = null)
    }
  }

  override suspend fun fetchDouyuPlayInfo(roomId: String): DouyuPlayInfo {
    return runCatching { douyuStreamResolver.fetchPlayInfo(roomId) }
      .onFailure { AppLog.e("DTV-Douyu", "fetchPlayInfo failed roomId=$roomId", it) }
      .getOrThrow()
  }

  override suspend fun resolveDouyuStreamUrl(roomId: String, quality: String?, cdn: String?): String {
    return runCatching {
      douyuStreamResolver.resolve(roomId = roomId, quality = quality, cdn = cdn)
    }
      .onSuccess { AppLog.i("DTV-Douyu", "resolved stream url roomId=$roomId quality=$quality cdn=$cdn url=$it") }
      .onFailure { AppLog.e("DTV-Douyu", "resolve stream url failed roomId=$roomId quality=$quality cdn=$cdn", it) }
      .getOrThrow()
  }

  override fun observeDouyuDanmaku(roomId: String): Flow<DanmakuMessage> {
    return douyuDanmakuClient.observe(roomId)
  }

  override fun observeHuyaDanmaku(roomId: String): Flow<DanmakuMessage> {
    return huyaDanmakuClient.observe(roomId)
  }

  override fun observeDouyinDanmaku(webRid: String): Flow<DanmakuMessage> {
    return douyinDanmakuClient.observe(webRid)
  }

  override suspend fun fetchHuyaLiveList(gid: String, page: Int, limit: Int): PagedResult<Streamer> {
    return runCatching {
      val items = huyaLiveListApi.fetchLiveList(gid = gid, page = page, pageSize = limit)
      PagedResult(items = items, total = null)
    }.getOrElse { PagedResult(items = emptyList(), total = null) }
  }

  override suspend fun resolveHuyaStreamUrl(roomId: String): String {
    return runCatching { huyaStreamResolver.resolve(roomId) }
      .onFailure { AppLog.e("DTV-Huya", "resolve stream url failed roomId=$roomId", it) }
      .getOrThrow()
  }

  override suspend fun fetchDouyinPartitionLiveList(
    partition: String,
    partitionType: String,
    offset: Int,
    limit: Int,
    msToken: String,
  ): PagedResult<Streamer> {
    return runCatching {
      AppLog.i("DTV-Douyin", "fetch partition list partition=$partition type=$partitionType offset=$offset limit=$limit")
      val resp = douyinWebApi.fetchPartitionRooms(
        partition = partition,
        partitionType = partitionType,
        offset = offset,
        limit = limit,
        msToken = msToken,
      )
      val items = resp.rooms.map { r ->
        Streamer(
          platform = Platform.Douyin,
          roomId = r.webRid,
          name = r.ownerNickname,
          title = r.title,
          viewerText = formatViewerCountWanIfNeeded(r.userCountStr),
          avatarUrl = normalizeHttpUrl(r.avatarUrl),
          coverUrl = normalizeHttpUrl(r.coverUrl),
          isLive = true,
        )
      }
      AppLog.i("DTV-Douyin", "fetch partition list ok partition=$partition offset=$offset got=${items.size} hasMore=${resp.hasMore}")
      PagedResult(items = items, total = null)
    }.getOrElse { err ->
      AppLog.e("DTV-Douyin", "fetch partition live list failed partition=$partition type=$partitionType offset=$offset", err)
      PagedResult(items = emptyList(), total = null)
    }
  }

  override suspend fun resolveDouyinStreamUrl(webRid: String, desiredQuality: String?): String {
    return runCatching {
      val room = douyinWebApi.fetchRoomEnter(webRid)
      room.roomId?.takeIf { it.isNotBlank() }?.let { roomId ->
        douyinDanmakuClient.prime(webRid = webRid, roomId = roomId, msToken = room.msToken)
      }
      val q = desiredQuality?.trim()?.takeIf { it.isNotBlank() }
      val hls = when {
        q == null -> null
        room.hlsPullUrlMap.containsKey(q) -> room.hlsPullUrlMap[q]
        room.hlsPullUrlMap.containsKey(q.uppercase()) -> room.hlsPullUrlMap[q.uppercase()]
        else -> null
      }
      val flv = when {
        q == null -> null
        room.flvPullUrlMap.containsKey(q) -> room.flvPullUrlMap[q]
        room.flvPullUrlMap.containsKey(q.uppercase()) -> room.flvPullUrlMap[q.uppercase()]
        else -> null
      }

      val hlsFallback = room.hlsPullUrlMap["ORIGIN"] ?: room.hlsPullUrlMap.values.firstOrNull()
      val flvFallback = room.flvPullUrlMap["ORIGIN"] ?: room.flvPullUrlMap.values.firstOrNull()
      // Mobile reality: some Douyin FLV streams may be H.265-in-FLV which Media3/ExoPlayer may not render
      // (audio-only on many devices). Prefer HLS first; fallback to FLV.
      (hls ?: hlsFallback ?: flv ?: flvFallback) ?: error("未找到抖音播放地址")
    }
      .onFailure { AppLog.e("DTV-Douyin", "resolve stream url failed webRid=$webRid", it) }
      .getOrThrow()
  }

  override suspend fun fetchBilibiliLiveList(parentAreaId: Int, areaId: Int, page: Int, pageSize: Int): PagedResult<Streamer> {
    return runCatching {
      val items = bilibiliLiveListApi.fetchLiveList(parentAreaId = parentAreaId, areaId = areaId, page = page, pageSize = pageSize)
      PagedResult(items = items, total = null)
    }.getOrElse { err ->
      AppLog.e("DTV-Bilibili", "fetch live list failed parent=$parentAreaId area=$areaId page=$page", err)
      PagedResult(items = emptyList(), total = null)
    }
  }

  override suspend fun resolveBilibiliStreamUrl(roomId: String, qn: Int?): String {
    return runCatching { bilibiliStreamResolver.resolve(roomId = roomId, qn = qn) }
      .onFailure { AppLog.e("DTV-Bilibili", "resolve stream url failed roomId=$roomId", it) }
      .getOrThrow()
  }

  override fun observeBilibiliDanmaku(roomId: String): Flow<DanmakuMessage> = bilibiliDanmakuClient.observe(roomId)

  override suspend fun generateBilibiliQrCode(): BilibiliQrCode {
    return bilibiliAuthApi.generateQrCode()
  }

  override suspend fun pollBilibiliQrCode(qrcodeKey: String): BilibiliQrPollResult {
    return bilibiliAuthApi.pollQrCode(qrcodeKey)
  }

  override suspend fun getBilibiliCookie(): String? {
    return bilibiliCookieStore.getCookie()
  }

  override suspend fun clearBilibiliCookie() {
    bilibiliCookieStore.clear()
  }
}
