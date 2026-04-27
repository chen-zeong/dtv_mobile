package dtv.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dtv.mobile.state.AppState
import dtv.mobile.sync.LAN_SYNC_DEFAULT_TOKEN
import dtv.mobile.sync.LanSyncClient
import dtv.mobile.sync.LanSyncDiscoveredPeer
import dtv.mobile.sync.LanSyncServerInfo
import dtv.mobile.sync.applyIncrementalLanSyncImport
import dtv.mobile.sync.buildMobileLanSyncPayload
import dtv.mobile.sync.normalizeImportTarget
import dtv.mobile.sync.rememberLanSyncController
import dtv.mobile.ui.system.rememberQrCodeScanLauncher
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(
  appState: AppState,
  modifier: Modifier = Modifier,
) {
  val controller = rememberLanSyncController()
  val scope = rememberCoroutineScope()
  val cardShape = RoundedCornerShape(16.dp)

  var tabIndex by remember { mutableIntStateOf(0) }

  var serverInfo by remember { mutableStateOf<LanSyncServerInfo?>(null) }
  var exportBusy by remember { mutableStateOf(false) }
  var exportError by remember { mutableStateOf<String?>(null) }

  var peers by remember { mutableStateOf<List<LanSyncDiscoveredPeer>>(emptyList()) }
  var discoverBusy by remember { mutableStateOf(false) }
  var discoverError by remember { mutableStateOf<String?>(null) }

  var manualTarget by remember { mutableStateOf("") }
  var importBusy by remember { mutableStateOf(false) }
  var importError by remember { mutableStateOf<String?>(null) }
  var importResult by remember { mutableStateOf<String?>(null) }

  fun startImport(target: String, tokenInput: String) {
    if (importBusy) return
    importBusy = true
    importError = null
    importResult = null
    scope.launch {
      runCatching {
        val (baseUrl, token) = normalizeImportTarget(target, tokenInput.ifBlank { LAN_SYNC_DEFAULT_TOKEN })
        val payload = LanSyncClient.fetchPayload(baseUrl, token)
        val result = applyIncrementalLanSyncImport(appState, payload)
        "导入成功：新增关注 ${result.addedFollowedStreamers}，新增订阅分区 ${result.addedSubscribedPartitions}，新增屏蔽词 ${result.addedDanmuBlockKeywords}" +
          (if (result.skippedPartitions > 0) "（${result.skippedPartitions} 个分区未匹配已跳过）" else "")
      }.onSuccess { importResult = it }
        .onFailure { importError = it.message ?: it.toString() }
      importBusy = false
    }
  }

  val launchQrScan = rememberQrCodeScanLauncher { text ->
    val scanned = text?.trim().orEmpty()
    if (scanned.isBlank()) return@rememberQrCodeScanLauncher
    manualTarget = scanned
    startImport(target = scanned, tokenInput = LAN_SYNC_DEFAULT_TOKEN)
  }

  LaunchedEffect(Unit) {
    serverInfo = runCatching { controller.serverStatus() }.getOrNull()
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    TabRow(selectedTabIndex = tabIndex) {
      Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("导出共享") })
      Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("导入") })
    }

    if (tabIndex == 0) {
      ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("一键导出（局域网共享）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "开启后，桌面端或其他设备可通过局域网导入（增量导入，重复跳过）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          )

          if (serverInfo != null) {
            val info = serverInfo!!
            val ips = info.hosts.filter { it != "127.0.0.1" && it != "localhost" }
            Text(
              "端口：${info.port}    Token：${info.token}",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              "本机 IP：${if (ips.isEmpty()) "未获取到" else ips.joinToString(" / ")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
          } else {
            Text(
              "状态：未共享",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
          }

          if (exportError != null) {
            Text(exportError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (serverInfo == null) {
              Button(
                onClick = {
                  if (exportBusy) return@Button
                  exportBusy = true
                  exportError = null
                  scope.launch {
                    runCatching {
                      val payload = buildMobileLanSyncPayload(appState)
                      serverInfo = controller.startServer(payload)
                    }.onFailure { exportError = it.message ?: it.toString() }
                    exportBusy = false
                  }
                },
                enabled = !exportBusy,
              ) {
                Text("开始共享")
              }
            } else {
              OutlinedButton(
                onClick = {
                  if (exportBusy) return@OutlinedButton
                  exportBusy = true
                  exportError = null
                  scope.launch {
                    runCatching { controller.stopServer() }
                      .onFailure { exportError = it.message ?: it.toString() }
                    serverInfo = runCatching { controller.serverStatus() }.getOrNull()
                    exportBusy = false
                  }
                },
                enabled = !exportBusy,
              ) {
                Text("结束共享")
              }
            }

            FilledTonalButton(
              onClick = {
                exportError = null
                scope.launch { serverInfo = runCatching { controller.serverStatus() }.getOrNull() }
              },
            ) {
              Text("刷新状态")
            }
          }
        }
      }
    } else {
      ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("一键导入（局域网发现）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "扫描同一局域网内正在共享的数据源（mDNS）。如果扫描不到，请使用下方手动导入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          )

          if (discoverError != null) {
            Text(discoverError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = {
                if (discoverBusy) return@Button
                discoverBusy = true
                discoverError = null
                scope.launch {
                  runCatching { peers = controller.discoverPeers() }
                    .onFailure { discoverError = it.message ?: it.toString() }
                  discoverBusy = false
                }
              },
              enabled = !discoverBusy,
            ) {
              Text("搜索设备")
            }
            FilledTonalButton(
              onClick = { peers = emptyList() },
              enabled = !discoverBusy,
            ) {
              Text("清空")
            }
          }

          if (peers.isEmpty()) {
            Text("未发现共享端。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
          } else {
            peers.forEach { peer ->
              ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(peer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                      "${peer.host}:${peer.port}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  FilledTonalButton(
                    onClick = { startImport(target = peer.baseUrl, tokenInput = peer.token ?: LAN_SYNC_DEFAULT_TOKEN) },
                    enabled = !importBusy,
                  ) {
                    Text("导入")
                  }
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
            }
          }
        }
      }

      ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("手动导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

          OutlinedTextField(
            value = manualTarget,
            onValueChange = { manualTarget = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("共享端 IP") },
          )

          if (importError != null) {
            Text(importError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          }
          if (importResult != null) {
            Text(importResult!!, style = MaterialTheme.typography.bodySmall)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
              onClick = { launchQrScan() },
              enabled = !importBusy,
            ) {
              Text("扫码导入")
            }
            Button(
              onClick = { startImport(target = manualTarget, tokenInput = LAN_SYNC_DEFAULT_TOKEN) },
              enabled = manualTarget.trim().isNotEmpty() && !importBusy,
            ) {
              Text("导入")
            }
          }
        }
      }
    }
  }
}
