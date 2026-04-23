package dtv.mobile.util

fun normalizeHttpUrl(raw: String?): String? {
  val value = raw?.trim().orEmpty()
  if (value.isEmpty()) return null
  return when {
    value.startsWith("//") -> "https:$value"
    else -> value
  }
}

