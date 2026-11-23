@file:OptIn(ExperimentalMaterial3Api::class)

package com.cola.record.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cola.record.data.MainViewModel
import com.cola.record.util.DateUtils
import com.cola.record.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsState()
    val pending by vm.pendingImport.collectAsState()
    val pendingCloud by vm.pendingCloud.collectAsState()
    val syncConfig by vm.syncConfig.collectAsState()
    val readOnly = !syncConfig.isPrimary

    var photoBytes by remember { mutableStateOf(0L) }
    var dbBytes by remember { mutableStateOf(0L) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(busy) {
        if (!busy) {
            photoBytes = withContext(Dispatchers.IO) { PhotoStore.totalBytes(context) }
            dbBytes = context.getDatabasePath("cola_record.db").length()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.prepareImport(uri)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "我的",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "备份、恢复与说明",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            SectionCard(emoji = "💾", title = "备份与恢复") {
                Text(
                    "导出的 zip 包含全部记录和照片，可保存到手机文件或发微信/传到电脑长期保存。" +
                        "换手机时把 zip 传过去，点「导入备份」即可全部恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        exportLauncher.launch("可乐成长记备份_${DateUtils.today()}.zip")
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出备份（.zip）") }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/json",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    enabled = !busy && !readOnly,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导入备份") }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        item { CloudSyncSection(vm) }

        item {
            SectionCard(emoji = "📦", title = "本机数据") {
                InfoRow("照片占用", formatBytes(photoBytes))
                InfoRow("数据库大小", formatBytes(dbBytes))
                InfoRow("数据位置", "App 私有目录（卸载会删除）")
                Text(
                    "重要：卸载 App 或清除应用数据会删除本机记录，请定期导出备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = !busy && !readOnly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("清空所有记录和照片", color = MaterialTheme.colorScheme.error)
                }
                if (readOnly) {
                    Text(
                        "只读设备：导入和清空已禁用，只能从云端恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(emoji = "ℹ️", title = "关于") {
                InfoRow("App", "可乐成长记")
                InfoRow("版本", "1.0")
                InfoRow("网络", "默认离线，可选云端同步")
                Text(
                    "不开启云端同步时，所有记录只保存在这台手机上。" +
                        "开启后仅在你配置的腾讯云服务器与手机之间传输备份包。" +
                        "照片选择使用系统相册选择器，不需要存储权限。" +
                        "资料库只保存链接和标题，打开时由系统浏览器或对应 App 处理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(emoji = "📝", title = "使用小提示") {
                Text(
                    "· 今日页可切换日期，补录前几天的记录\n" +
                        "· 情绪卡直接点表情即可打分，编辑可写备注\n" +
                        "· 药物在「药物」页按类别添加，今日页自动生成打卡\n" +
                        "· 照片会自动压缩，备份时一起打包\n" +
                        "· 资料库粘贴分享内容后点「智能提取」，自动识别标题、链接和平台",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "确认清空全部数据？",
            text = "会删除手机上所有记录和照片，且无法恢复。建议先导出备份。",
            confirmText = "清空",
            onConfirm = {
                confirmClear = false
                vm.clearAllData()
            },
            onDismiss = { confirmClear = false }
        )
    }

    pending?.let { p ->
        val cloud = pendingCloud
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("确认导入备份？") },
            text = {
                Text(
                    buildString {
                        if (cloud != null) {
                            append("来源：云端（${cloud.deviceName}）\n")
                            append("云端版本：revision ${cloud.revision}\n")
                            append("服务器接收时间：${formatTime(cloud.receivedAt)}\n")
                        }
                        append("备份时间：${formatTime(p.data.exportedAt)}\n")
                        append("记录条数：${p.recordCount} 条\n")
                        if (p.photoCount > 0) append("照片：${p.photoCount} 张\n")
                        if (cloud != null && cloud.imagesMissing > 0) {
                            append("提示：有 ${cloud.imagesMissing} 张服务器缺失的照片，恢复后可能显示不出来\n")
                        }
                        append("\n导入会覆盖当前手机上的全部记录和照片，且无法撤销。")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmImport() }) {
                    Text("覆盖导入", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelImport) { Text("取消") }
            }
        )
    }
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
