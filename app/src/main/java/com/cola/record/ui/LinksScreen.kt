@file:OptIn(ExperimentalMaterial3Api::class)

package com.cola.record.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cola.record.data.LinkItem
import com.cola.record.data.MainViewModel
import com.cola.record.util.DateUtils
import com.cola.record.util.ShareParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val platformOptions = listOf("小红书", "抖音", "微信视频", "其他")

@Composable
fun LinksScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val links by vm.linkItems.collectAsState()
    val syncConfig by vm.syncConfig.collectAsState()
    val readOnly = !syncConfig.isPrimary
    var query by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("全部") }
    var editing by remember { mutableStateOf<LinkItem?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LinkItem?>(null) }

    val filtered = links.filter { link ->
        (platform == "全部" || link.platform == platform) &&
            (query.isBlank() ||
                link.title.contains(query, ignoreCase = true) ||
                link.tags.contains(query, ignoreCase = true) ||
                link.note.contains(query, ignoreCase = true) ||
                link.url.contains(query, ignoreCase = true))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "资料库",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "收藏小红书、抖音、微信视频里的育儿干货",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索标题、标签、备注") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (listOf("全部") + platformOptions).forEach { option ->
                        FilterChip(
                            selected = platform == option,
                            onClick = { platform = option },
                            label = { Text(option) }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyHint(
                        if (links.isEmpty()) "还没有收藏链接。点右下角按钮，粘贴分享内容即可自动提取标题。"
                        else "没有符合条件的链接"
                    )
                }
            }
            items(filtered, key = { it.id }) { link ->
                LinkCard(
                    link = link,
                    onOpen = { openLink(context, link.url) },
                    onEdit = {
                        if (readOnly) {
                            vm.notifyReadOnly()
                        } else {
                            editing = link
                            showSheet = true
                        }
                    },
                    onDelete = {
                        if (readOnly) vm.notifyReadOnly() else deleting = link
                    }
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }

        FloatingActionButton(
            onClick = {
                if (readOnly) {
                    vm.notifyReadOnly()
                } else {
                    editing = null
                    showSheet = true
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, "收藏链接")
        }
    }

    if (showSheet) {
        LinkSheet(
            existing = editing,
            onDismiss = { showSheet = false },
            onSave = {
                vm.saveLink(it)
                showSheet = false
            },
            onDelete = if (editing != null) {
                {
                    vm.deleteLink(editing!!)
                    showSheet = false
                }
            } else null
        )
    }

    deleting?.let { link ->
        ConfirmDialog(
            title = "删除链接",
            text = "确定删除「${link.title.ifBlank { link.url }}」吗？",
            onConfirm = {
                vm.deleteLink(link)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun LinkCard(
    link: LinkItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                link.title.ifBlank { link.url },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tag(link.platform.ifBlank { "其他" })
                link.tags.split(",", "，").map { it.trim() }.filter { it.isNotBlank() }
                    .forEach { tag ->
                        Tag(
                            "#$tag",
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                Text(
                    DateUtils.shortLabel(
                        Instant.ofEpochMilli(link.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (link.note.isNotBlank()) {
                Text(
                    link.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    link.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.OpenInNew,
                        "打开链接",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        "编辑",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkSheet(
    existing: LinkItem?,
    onDismiss: () -> Unit,
    onSave: (LinkItem) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var shareText by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var platform by remember { mutableStateOf(existing?.platform ?: "其他") }
    var tags by remember { mutableStateOf(existing?.tags ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf("") }

    FormSheet(
        title = if (existing == null) "📚 收藏链接" else "📚 编辑链接",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            if (url.isBlank()) {
                error = "请先填写链接地址"
            } else {
                onSave(
                    (existing ?: LinkItem(url = "")).copy(
                        url = normalizeUrl(url),
                        title = title,
                        platform = platform,
                        tags = tags,
                        note = note
                    )
                )
            }
        }
    ) {
        if (existing == null) {
            Text(
                "把小红书 / 抖音 / 微信视频的分享内容整段粘贴进来，点「智能提取」自动识别标题、链接和平台。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextFieldRow(
                "粘贴分享内容",
                shareText,
                { newText ->
                    val isBulkInput = newText.length - shareText.length > 8
                    shareText = newText
                    if (isBulkInput && url.isBlank()) {
                        val parsed = ShareParser.parse(newText)
                        if (parsed.url.isNotBlank()) {
                            url = parsed.url
                            if (title.isBlank()) title = parsed.title
                            platform = parsed.platform
                        }
                    }
                },
                minLines = 3,
                placeholder = "如：宝宝秋季辅食这样吃… https://xhslink.com/xxxx"
            )
            TextButton(onClick = {
                val parsed = ShareParser.parse(shareText)
                if (parsed.url.isNotBlank()) {
                    url = parsed.url
                    title = parsed.title
                    platform = parsed.platform
                    error = ""
                } else {
                    error = "没有识别到链接，请检查分享内容"
                }
            }) { Text("智能提取标题和链接") }
        }
        TextFieldRow("链接地址", url, { url = it }, placeholder = "https://...")
        TextFieldRow("标题", title, { title = it }, placeholder = "链接的标题或你起的名字")
        ChoiceChips("平台", platformOptions, platform) { platform = it.ifBlank { "其他" } }
        TextFieldRow(
            "分类标签",
            tags,
            { tags = it },
            placeholder = "如：辅食, 睡眠, 绘本（用逗号分隔）"
        )
        TextFieldRow("备注", note, { note = it }, minLines = 2)
        if (error.isNotBlank()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
    else "https://$trimmed"
}

private fun openLink(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(url)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "没有找到可以打开该链接的应用", Toast.LENGTH_SHORT).show()
    }
}
