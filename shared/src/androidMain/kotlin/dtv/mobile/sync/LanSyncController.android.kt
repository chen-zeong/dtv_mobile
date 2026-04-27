package dtv.mobile.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val NSD_SERVICE_TYPE = "_dtv-lan-sync._tcp."

private val serverJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

private data class RunningServer(
  val engine: ApplicationEngine,
  val payload: LanSyncPayload,
  val token: String,
  val port: Int,
  val hosts: List<String>,
  val nsdManager: NsdManager,
  val registrationListener: NsdManager.RegistrationListener?,
)

private object AndroidLanSyncRuntime {
  private val lock = Any()
  private var running: RunningServer? = null

  suspend fun start(context: Context, payload: LanSyncPayload, token: String): LanSyncServerInfo {
    stop()

    val appContext = context.applicationContext
    val hosts = buildHosts()

    val desiredPort = LAN_SYNC_FIXED_PORT
    val (engine, port) = runCatching { startEngine(payload = payload, token = token, port = desiredPort) to desiredPort }
      .getOrElse {
        val fallback = pickFreePort()
        startEngine(payload = payload, token = token, port = fallback) to fallback
      }

    val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    val registrationListener = registerMdns(nsdManager = nsdManager, port = port, token = token)

    synchronized(lock) {
      running = RunningServer(
        engine = engine,
        payload = payload,
        token = token,
        port = port,
        hosts = hosts,
        nsdManager = nsdManager,
        registrationListener = registrationListener,
      )
    }

    return LanSyncServerInfo(port = port, hosts = hosts, token = token)
  }

  suspend fun stop() {
    val previous = synchronized(lock) { running.also { running = null } } ?: return
    runCatching {
      withContext(Dispatchers.Main) {
        previous.registrationListener?.let { listener ->
          runCatching { previous.nsdManager.unregisterService(listener) }
        }
      }
    }
    runCatching { previous.engine.stop(250, 750) }
  }

  fun status(): LanSyncServerInfo? {
    val r = synchronized(lock) { running } ?: return null
    return LanSyncServerInfo(port = r.port, hosts = r.hosts, token = r.token)
  }

  // --- engine wiring ---
  private fun startEngine(payload: LanSyncPayload, token: String, port: Int): ApplicationEngine {
    val engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
      install(ContentNegotiation) { json(serverJson) }
      routing {
        get(LAN_SYNC_HTTP_PATH) {
          if (!checkAuth(call, token)) return@get
          call.respond(buildManifest(payload))
        }
        get(LAN_SYNC_PAYLOAD_PATH) {
          if (!checkAuth(call, token)) return@get
          call.respond(payload)
        }
      }
    }
    engine.start(wait = false)
    return engine
  }

  private suspend fun checkAuth(call: ApplicationCall, expectedToken: String): Boolean {
    val provided = call.request.queryParameters["token"].orEmpty()
    if (provided != expectedToken) {
      call.respond(HttpStatusCode.Unauthorized, "Unauthorized.")
      return false
    }

    val host = call.request.origin.remoteHost
    val ip = runCatching { InetAddress.getByName(host) }.getOrNull()
    if (ip == null || !isPrivateIp(ip)) {
      call.respond(HttpStatusCode.Forbidden, "Forbidden: non-private network.")
      return false
    }

    return true
  }

  private fun buildManifest(payload: LanSyncPayload): LanSyncManifest {
    val entries = payload.entries
    val summary = LanSyncSummary(
      followedStreamers = countJsonArray(entries["followedStreamers"]),
      followFolders = countJsonArray(entries["followFolders"]),
      followListOrder = countJsonArray(entries["followListOrder"]),
      customCategories = countJsonArray(entries["dtv_custom_categories_v1"]),
      totalBytes = entries.entries.sumOf { (k, v) -> k.length + v.length },
    )
    return LanSyncManifest(
      kind = payload.kind,
      version = payload.version,
      exportedAt = payload.exportedAt,
      source = payload.source,
      summary = summary,
    )
  }

  private fun countJsonArray(raw: String?): Int {
    if (raw.isNullOrBlank()) return 0
    return runCatching {
      val el = serverJson.parseToJsonElement(raw)
      (el as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }.getOrElse { 0 }
  }

  private fun pickFreePort(): Int {
    val socket = java.net.ServerSocket(0)
    val port = socket.localPort
    socket.close()
    return port
  }

  private fun registerMdns(nsdManager: NsdManager, port: Int, token: String): NsdManager.RegistrationListener? {
    val serviceInfo = NsdServiceInfo().apply {
      serviceName = buildInstanceName(port)
      serviceType = NSD_SERVICE_TYPE
      this.port = port
      setAttribute("kind", LAN_SYNC_KIND)
      setAttribute("ver", LAN_SYNC_VERSION.toString())
      setAttribute("path", LAN_SYNC_HTTP_PATH)
      setAttribute("token", token)
    }

    val listener = object : NsdManager.RegistrationListener {
      override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
      override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
      override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
      override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
    }

    runCatching {
      // Must be on main thread.
      android.os.Handler(android.os.Looper.getMainLooper()).post {
        runCatching { nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
      }
    }
    return listener
  }

  private fun buildInstanceName(port: Int): String {
    val model = android.os.Build.MODEL?.ifBlank { null } ?: "android"
    val sanitized = model
      .lowercase(Locale.ROOT)
      .map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '-' }
      .joinToString("")
    return "dtv-sync-$sanitized-$port"
  }

  private fun buildHosts(): List<String> {
    val out = ArrayList<String>()
    out.add("127.0.0.1")
    out.add("localhost")

    val ifaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrElse { emptyList() }
    for (iface in ifaces) {
      val addrs = runCatching { Collections.list(iface.inetAddresses) }.getOrElse { emptyList() }
      for (addr in addrs) {
        if (addr is Inet4Address && !addr.isLoopbackAddress) {
          out.add(addr.hostAddress ?: continue)
        }
      }
    }
    return out.distinct().sorted()
  }

  private fun isPrivateIp(addr: InetAddress): Boolean {
    if (addr.isLoopbackAddress) return true
    if (addr is Inet4Address) {
      val bytes = addr.address
      val b0 = bytes[0].toInt() and 0xFF
      val b1 = bytes[1].toInt() and 0xFF
      // 10.0.0.0/8
      if (b0 == 10) return true
      // 172.16.0.0/12
      if (b0 == 172 && b1 in 16..31) return true
      // 192.168.0.0/16
      if (b0 == 192 && b1 == 168) return true
      // 169.254.0.0/16
      if (b0 == 169 && b1 == 254) return true
      return false
    }
    // IPv6: allow unique-local and link-local.
    return addr.isLinkLocalAddress || addr.isSiteLocalAddress
  }
}

private class AndroidLanSyncController(
  private val appContext: Context,
) : LanSyncController {
  override suspend fun discoverPeers(timeoutMs: Long): List<LanSyncDiscoveredPeer> {
    return withContext(Dispatchers.Main) {
      val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
      discoverPeersInternal(nsdManager = nsdManager, timeoutMs = timeoutMs)
    }
  }

  override suspend fun startServer(payload: LanSyncPayload): LanSyncServerInfo {
    return AndroidLanSyncRuntime.start(context = appContext, payload = payload, token = LAN_SYNC_DEFAULT_TOKEN)
  }

  override suspend fun stopServer() {
    AndroidLanSyncRuntime.stop()
  }

  override suspend fun serverStatus(): LanSyncServerInfo? = AndroidLanSyncRuntime.status()
}

private suspend fun discoverPeersInternal(nsdManager: NsdManager, timeoutMs: Long): List<LanSyncDiscoveredPeer> {
  return suspendCancellableCoroutine { cont ->
    val found = ConcurrentHashMap<String, LanSyncDiscoveredPeer>()
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val resolveListener = object : NsdManager.ResolveListener {
      override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

      override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        val host = serviceInfo.host?.hostAddress ?: return
        val port = serviceInfo.port
        val name = serviceInfo.serviceName ?: host
        val kind = serviceInfo.attributes["kind"]?.toString(Charsets.UTF_8)
        val path = serviceInfo.attributes["path"]?.toString(Charsets.UTF_8)
        if (kind != LAN_SYNC_KIND || path != LAN_SYNC_HTTP_PATH) return
        val token = serviceInfo.attributes["token"]?.toString(Charsets.UTF_8)
        val baseUrl = "http://$host:$port"
        found[name] = LanSyncDiscoveredPeer(name = name, host = host, port = port, token = token, baseUrl = baseUrl)
      }
    }

    val discoveryListener = object : NsdManager.DiscoveryListener {
      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        runCatching { nsdManager.stopServiceDiscovery(this) }
        if (cont.isActive) cont.resumeWithException(IllegalStateException("mDNS discover failed: $errorCode"))
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        runCatching { nsdManager.stopServiceDiscovery(this) }
      }

      override fun onDiscoveryStarted(serviceType: String) = Unit

      override fun onDiscoveryStopped(serviceType: String) = Unit

      override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        if (serviceInfo.serviceType != NSD_SERVICE_TYPE) return
        runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
      }

      override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
    }

    runCatching { nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
      .onFailure {
        cont.resumeWithException(it)
        return@suspendCancellableCoroutine
      }

    cont.invokeOnCancellation {
      scope.cancel()
      runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
    }

    // Finish after timeout.
    val timeout = timeoutMs.coerceIn(300, 5000)
    scope.launch {
      delay(timeout)
      runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
      val peers = found.values.toList().sortedBy { it.name }
      if (cont.isActive) cont.resume(peers)
    }
  }
}

@Composable
actual fun rememberLanSyncController(): LanSyncController {
  val ctx = LocalContext.current.applicationContext
  return remember(ctx) { AndroidLanSyncController(ctx) }
}
