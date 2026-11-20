package dtv.mobile.platform.douyin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import java.security.MessageDigest
import java.net.URLDecoder

/**
 * Generates Douyin webcast WebSocket `signature` param by evaluating the same `sign.js` used in DTV-heroui.
 *
 * NOTE: This is Android-only; Rhino is already a dependency of `shared` androidMain.
 */
internal class DouyinWebcastSignatureAndroid(
  private val appContext: Context,
) {
  companion object {
    private const val TAG = "DTV-Douyin"
    private const val SIGN_JS_RESOURCE = "douyin/sign.js"

    private val SIGN_KEYS = listOf(
      "live_id",
      "aid",
      "version_code",
      "webcast_sdk_version",
      "room_id",
      "sub_room_id",
      "sub_channel_id",
      "did_rule",
      "user_unique_id",
      "device_platform",
      "device_type",
      "ac",
      "identity",
    )
  }

  private val lock = Any()
  private var scope: Scriptable? = null

  private fun md5Hex(input: String): String {
    val md = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return md.joinToString("") { "%02x".format(it) }
  }

  private fun loadSignJs(): String {
    val cl = appContext.classLoader
    val stream = cl.getResourceAsStream(SIGN_JS_RESOURCE)
      ?: error("Missing resource $SIGN_JS_RESOURCE (copy DTV-heroui sign.js to shared androidMain resources)")
    // Rhino 1.7.x does not support `let` reliably; the upstream sign.js only uses it in the tiny wrapper.
    // Convert it to ES5 `var` to avoid parse errors.
    val src = stream.use { it.readBytes().toString(Charsets.UTF_8) }
      .replace(Regex("\\blet\\b"), "var")

    // Rhino does not implement browser Annex-B hoisting for function declarations inside blocks.
    // The upstream sign.js has:
    //   if (...) { function w_...(){ var a=w_0x42f5(); ... } ( ... )(w_0x42f5, ...); function w_0x42f5(){...} }
    // which works in browsers but fails in Rhino with:
    //   ReferenceError: "w_0x42f5" is not defined
    // Move `function w_0x42f5(){...}` to the beginning of the block to make it available before first use.
    return runCatching { moveBlockFunctionEarlier(src, functionName = "w_0x42f5") }
      .getOrDefault(src)
  }

  private fun moveBlockFunctionEarlier(src: String, functionName: String): String {
    val blockStart = src.indexOf("if (!window.byted_acrawler) {")
    if (blockStart < 0) return src

    val declStart = src.indexOf("function $functionName", startIndex = blockStart)
    if (declStart < 0) return src

    val braceOpen = src.indexOf('{', startIndex = declStart)
    if (braceOpen < 0) return src

    // Find matching close brace for the function body.
    var depth = 0
    var i = braceOpen
    while (i < src.length) {
      when (src[i]) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) {
            val declEndExclusive = i + 1
            val functionDecl = src.substring(declStart, declEndExclusive).trimEnd()
            val without = buildString(src.length) {
              append(src, 0, declStart)
              // skip the original declaration
              append(src, declEndExclusive, src.length)
            }

            val insertPos = without.indexOf('{', startIndex = blockStart).let { if (it >= 0) it + 1 else return src }
            return buildString(without.length + functionDecl.length + 2) {
              append(without, 0, insertPos)
              append('\n')
              append(functionDecl)
              append('\n')
              append(without, insertPos, without.length)
            }
          }
        }
      }
      i++
    }
    return src
  }

  private fun ensureRhinoInitialized(cx: RhinoContext): Scriptable {
    val existing = scope
    if (existing != null) return existing

    val s = cx.initStandardObjects()
    val bootstrap = """
      // Rhino may not provide `globalThis` in all modes; keep bootstrap ES5-compatible.
      this.window = this;
      this.self = this;
      this.document = {};
    """.trimIndent()
    cx.evaluateString(s, bootstrap, "[bootstrap]", 1, null)
    cx.evaluateString(s, loadSignJs(), SIGN_JS_RESOURCE, 1, null)
    scope = s
    return s
  }

  private fun configureRhino(cx: RhinoContext) {
    // sign.js is large and heavily obfuscated; disable JIT for stability.
    cx.optimizationLevel = -1

    // Important: do NOT force ES6 here.
    //
    // The upstream sign.js contains function declarations inside a block (`if (...) { function ... }`)
    // and references them before the declaration site. Browsers rely on Annex B (web-compat)
    // hoisting semantics in sloppy mode; Rhino ES6 mode can treat those as block-scoped and
    // throw `ReferenceError: w_xxx is not defined`.
    //
    // Using ES5.1 semantics matches the desktop version (V8 sloppy mode) better for this script.
    runCatching { cx.languageVersion = RhinoContext.VERSION_1_8 }.getOrNull()
  }

  /**
   * Re-implements DTV-heroui `generate_signature()`:
   * - Build `toSignStr` from selected query params.
   * - MD5 hex it into `X-MS-STUB`.
   * - Run `get_sign(md5)` from sign.js to get final signature.
   */
  suspend fun signatureForWsUrl(wsUrlWithoutSignature: String): String = withContext(Dispatchers.Default) {
    val query = wsUrlWithoutSignature.substringAfter('?', "")
    val params = query.split('&')
      .mapNotNull { kv ->
        val i = kv.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val k = kv.substring(0, i)
        val v = kv.substring(i + 1)
        // Match Rust Url::query_pairs(): percent-decode both key and value.
        runCatching { URLDecoder.decode(k, "UTF-8") }.getOrDefault(k) to
          runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
      }
      .toMap()

    val toSignStr = SIGN_KEYS.joinToString(",") { k -> "$k=${params[k].orEmpty()}" }
    val md5 = md5Hex(toSignStr)

    synchronized(lock) {
      val cx = RhinoContext.enter()
      try {
        configureRhino(cx)
        val s = ensureRhinoInitialized(cx)
        val script = "get_sign('$md5')"
        val out = cx.evaluateString(s, script, "[get_sign]", 1, null)
        val sig = RhinoContext.toString(out).trim()
        if (sig.isBlank()) error("empty douyin signature")
        sig
      } catch (t: Throwable) {
        throw t
      } finally {
        RhinoContext.exit()
      }
    }
  }
}
