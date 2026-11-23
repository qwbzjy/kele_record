@file:OptIn(ExperimentalMaterial3Api::class)

package com.cola.record.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cola.record.data.MainViewModel
import com.cola.record.data.MedCategory
import com.cola.record.data.MedLog

@Composable
fun MedsScreen(vm: MainViewModel) {
    val date by vm.selectedDate.collectAsState()
    val categories by vm.medCategories.collectAsState()
    val logs by vm.medLogs.collectAsState()
    val syncConfig by vm.syncConfig.collectAsState()
    val readOnly = !syncConfig.isPrimary

    var editing by remember { mutableStateOf<MedCategory?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    val active = categories.filter { it.active }
    val totalSlots = active.sumOf { cat ->
        cat.plannedTimes.split(",").count { it.isNotBlank() }
    }
    val doneSlots = active.sumOf { cat ->
        cat.plannedTimes.split(",").filter { it.isNotBlank() }.count { slot ->
            logs.any { it.categoryId == cat.id && it.slot == slot.trim() }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "药物 / 营养品",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "按类别管理，如「益生菌：每天 2 次，08:00 / 20:00」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                DateNavBar(date = date, onDateChange = vm::setDate)
            }
            item {
                Text(
                    if (totalSlots == 0) "在下方添加正在吃的药物类别，今日页会出现打卡项。"
                    else "这一天已完成 $doneSlots/$totalSlots 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (categories.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("还没有药物记录", fontWeight = FontWeight.SemiBold)
                            Text(
                                "点右下角 + 添加，例如：\n· 益生菌：每天 2 次，08:00 / 20:00，1 袋\n· 维生素D：每天 1 次，09:00，1 滴",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(categories, key = { it.id }) { category ->
                CategoryCard(
                    category = category,
                    date = date,
                    logs = logs,
                    onEdit = {
                        if (readOnly) {
                            vm.notifyReadOnly()
                        } else {
                            editing = category
                            showSheet = true
                        }
                    },
                    onToggle = { slot, taken ->
                        if (readOnly) vm.notifyReadOnly()
                        else vm.toggleMed(category.id, date, slot, taken)
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
            Icon(Icons.Filled.Add, "添加药物")
        }
    }

    if (showSheet) {
        CategorySheet(
            existing = editing,
            onDismiss = { showSheet = false },
            onSave = {
                vm.saveCategory(it)
                showSheet = false
            },
            onDelete = if (editing != null) {
                {
                    vm.deleteCategory(editing!!)
                    showSheet = false
                }
            } else null
        )
    }
}

@Composable
private fun CategoryCard(
    category: MedCategory,
    date: String,
    logs: List<MedLog>,
    onEdit: () -> Unit,
    onToggle: (slot: String, taken: Boolean) -> Unit
) {
    val slots = category.plannedTimes.split(",").map { it.trim() }.filter { it.isNotBlank() }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (category.active) {
                            Spacer(Modifier.size(8.dp))
                            Tag("正在吃")
                        } else {
                            Spacer(Modifier.size(8.dp))
                            Tag(
                                "已停用",
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (category.dosage.isNotBlank()) {
                        Text(
                            category.dosage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                if (slots.isEmpty()) "未设置服用时间"
                else "每天 ${slots.size} 次：${slots.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (category.active && slots.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slots.forEach { slot ->
                        val log = logs.firstOrNull {
                            it.categoryId == category.id && it.slot == slot
                        }
                        FilterChip(
                            selected = log != null,
                            onClick = { onToggle(slot, log == null) },
                            label = {
                                Text(
                                    if (log != null) {
                                        "$slot ✓" + if (log.takenAt.isNotBlank()) " ${log.takenAt}" else ""
                                    } else slot
                                )
                            }
                        )
                    }
                }
            }
            if (category.note.isNotBlank()) {
                Text(
                    category.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
