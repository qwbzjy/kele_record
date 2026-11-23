@file:OptIn(ExperimentalMaterial3Api::class)

package com.cola.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cola.record.data.MainViewModel
import com.cola.record.data.sync.SyncUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CloudSyncSection(vm: MainViewModel) {
    val config by vm.syncConfig.collectAsState()
    val ui by vm.syncUi.collectAsState()
    val server by vm.syncServerState.collectAsState()
    val busy by vm.busy.collectAsState()

    var enabled by remember { mutableStateOf(config.enabled) }
    var url by remember { mutableStateOf(config.baseUrl) }
    var token by remember { mutableStateOf(config.token) }
    var deviceName by remember { mutableStateOf(config.deviceName) }
    var wifiOnly by remember { mutableStateOf(config.wifiOnly) }
    var isPrimary by remember { mutableStateOf(config.isPrimary) }
    var showToken by remember { mutableStateOf(false) }
    var confirmForceUpload by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        enabled = config.enabled
        url = config.baseUrl
        token = config.token
        deviceName = config.deviceName
        wifiOnly = config.wifiOnly
        isPrimary = config.isPrimary
    }

    SectionCard(
        emoji = "☁️",
        title = "云端同步",
        subtitle = "自动上传到你的腾讯云服务器，手动从云端恢复"
    ) {
        LabeledSwitch(
            "本机为主设备（允许录入和上传）",
            isPrimary,
            enabled = !busy
        ) {
            isPrimary = it
            if (!it) enabled = false
        }
        if (!isPrimary) {
            Text(
                "本机为只读设备：可以查看记录、从云端恢复，但不能录入或上传。录入请使用主设备。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        LabeledSwitch(
            "启用自动上传（记录变化后 30 秒上传）",
            enabled,
            enabled = isPrimary && !busy
        ) { enabled = it }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("服务器地址") },
            placeholder = { Text("http://公网IP 或 https://域名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (url.trimStart().startsWith("http://")) {
            Text(
                "⚠ 当前使用明文 HTTP，Token 和照片可能被网络窃听；建议后续配置域名 + HTTPS。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("同步 Token") },
            singleLine = true,
            visualTransformation = if (showToken) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (showToken) "隐藏 Token" else "显示 Token"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("设备名（云端会显示是谁上传的）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        LabeledSwitch("仅 Wi-Fi 上传（自动上传时）", wifiOnly) { wifiOnly = it }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    vm.saveSyncSettings(enabled, url, token, deviceName, wifiOnly, isPrimary)
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("保存设置") }
            Button(
                onClick = {
                    vm.saveSyncSettings(enabled, url, token, deviceName, wifiOnly, isPrimary)
                    vm.testSyncConnection()
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("测试连接") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    vm.saveSyncSettings(enabled, url, token, deviceName, wifiOnly, isPrimary)
                    if (vm.syncSettings.snapshot().conflict) {
                        confirmForceUpload = true
                    } else {
                        vm.uploadNow(force = false)
                    }
                },
                enabled = !busy && isPrimary,
                modifier = Modifier.weight(1f)
            ) { Text("立即上传") }
            OutlinedButton(
                onClick = {
                    vm.saveSyncSettings(enabled, url, token, deviceName, wifiOnly, isPrimary)
                    vm.downloadCloudBackup()
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("从云端恢复") }
        }

        when (val state = ui) {
            is SyncUiState.Uploading -> {
                Text(
                    "上传中：${state.phase}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            is SyncUiState.Downloading -> {
                val text = if (state.total > 0) {
                    "下载中：${(state.done * 100 / state.total).toInt()}%"
                } else {
                    "下载中：${state.done / 1024} KB"
                }
                Text(text, style = MaterialTheme.typography.bodySmall)
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            is SyncUiState.Success -> {
                Text(
                    "已同步 revision ${state.revision}，本次上传照片 ${state.photos} 张",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            is SyncUiState.Error -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is SyncUiState.Conflict -> {
                Text(
                    "云端已有来自「${state.deviceName}」的数据（revision ${state.revision}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            SyncUiState.Idle -> Unit
        }

        if (config.conflict) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "检测到云端数据来自「${config.conflictDevice.ifBlank { "其他设备" }}」。自动上传已暂停，请选择：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            vm.saveSyncSettings(enabled, url, token, deviceName, wifiOnly, isPrimary)
                            vm.downloadCloudBackup()
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("从云端恢复") }
                    Button(
                        onClick = {
                            vm.saveSyncSettings(enabled, url, token, deviceName, wifiOnly, isPrimary)
                            confirmForceUpload = true
                        },
                        enabled = !busy && isPrimary,
                        modifier = Modifier.weight(1f)
                    ) { Text("以本机覆盖云端") }
                }
            }
        }

        if (config.lastSuccessAt > 0) {
            Text(
                "上次同步：${formatSyncTime(config.lastSuccessAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (config.lastRevision != null) {
            Text(
                "已同步版本：revision ${config.lastRevision}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        server?.let { state ->
            Text(
                "云端：revision ${state.revision} · " +
                    "来源 ${state.deviceName.ifBlank { "无" }} · " +
                    "记录 ${state.recordCount} 条 · 照片 ${state.imageCount} 张" +
                    if (state.receivedAt > 0) "（${formatSyncTime(state.receivedAt)}）" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.primaryDeviceName.isNotBlank() || state.primaryDeviceId.isNotBlank()) {
                Text(
                    "云端主设备：${state.primaryDeviceName.ifBlank { state.primaryDeviceId }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "本机设备 ID：${config.deviceId}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "说明：自动上传只写云端、不会动本机；从云端恢复是覆盖式，会替换本机全部记录和照片。" +
                "建议只在一台手机上开启自动上传；两台手机同时开启时，云端数据以最后上传的为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
    }

    if (confirmForceUpload) {
        ConfirmDialog(
            title = "以本机覆盖云端？",
            text = "云端的记录将被本机当前数据替换。若云端有另一台手机的新记录，这些记录会丢失（本机数据不受影响）。",
            confirmText = "覆盖云端",
            onConfirm = {
                confirmForceUpload = false
                vm.uploadNow(force = true)
            },
            onDismiss = { confirmForceUpload = false }
        )
    }
}

private fun formatSyncTime(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
