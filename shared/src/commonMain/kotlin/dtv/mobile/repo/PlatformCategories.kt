package dtv.mobile.repo

data class HuyaCate1(
  val name: String,
  val href: String? = null,
  val cate2List: List<HuyaCate2>,
)

data class HuyaCate2(
  val gid: String,
  val name: String,
  val href: String? = null,
)

data class BilibiliCate1(
  val parentAreaId: Int,
  val name: String,
  val href: String? = null,
  val cate2List: List<BilibiliCate2>,
)

data class BilibiliCate2(
  val areaId: Int,
  val parentAreaId: Int,
  val name: String,
  val href: String? = null,
)

data class DouyinCate1(
  val name: String,
  val href: String? = null,
  val cate2List: List<DouyinCate2>,
)

data class DouyinCate2(
  val partition: String,
  val partitionType: String,
  val name: String,
  val href: String? = null,
)

