package dtv.mobile.platform.common

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.jsonStringOrNull(): String? {
  val primitive = this as? JsonPrimitive ?: return null
  if (primitive is JsonNull) return null
  return primitive.content
}

internal fun JsonElement?.jsonLongOrNull(): Long? {
  val primitive = this as? JsonPrimitive ?: return null
  return primitive.longOrNull ?: primitive.content.toLongOrNull()
}

internal fun JsonElement?.jsonBooleanOrNull(): Boolean? {
  val primitive = this as? JsonPrimitive ?: return null
  return primitive.booleanOrNull ?: primitive.content.toBooleanStrictOrNull()
}

internal fun JsonObject.objectAt(path: List<String>): JsonObject? {
  var current: JsonElement = this
  for (part in path) {
    current = (current as? JsonObject)?.get(part) ?: return null
  }
  return current as? JsonObject
}

internal fun JsonObject.arrayAt(path: List<String>): JsonArray? {
  var current: JsonElement = this
  for (part in path) {
    current = (current as? JsonObject)?.get(part) ?: return null
  }
  return current as? JsonArray
}

internal fun JsonObject.stringAt(path: List<String>): String? {
  var current: JsonElement = this
  for (part in path) {
    current = (current as? JsonObject)?.get(part) ?: return null
  }
  return current.jsonStringOrNull()
}

internal fun JsonElement?.jsonObjects(): List<JsonObject> =
  (this as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

internal fun JsonElement?.jsonStrings(): List<String> =
  (this as? JsonArray).orEmpty().mapNotNull { it.jsonStringOrNull() }
