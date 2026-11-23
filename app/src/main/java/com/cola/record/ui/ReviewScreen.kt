@file:OptIn(ExperimentalMaterial3Api::class)

package com.cola.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cola.record.data.MainViewModel
import com.cola.record.data.MedicalReport
import com.cola.record.data.WeekStats
import com.cola.record.data.toPhotoList
import com.cola.record.util.DateUtils

private val emojiByScore = listOf("😭", "😞", "😐", "😄", "🤩")

@Composable
fun ReviewScreen(vm: MainViewModel) {
    val date by vm.selectedDate.collectAsState()
    val nights by vm.nights.collectAsState()
    val naps by vm.naps.collectAsState()
    val meals by vm.meals.collectAsState()
    val poops by vm.poops.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val baths by vm.baths.collectAsState()
    val outings by vm.outings.collectAsState()
    val placeStats by vm.placeStats.collectAsState()
    val moods by vm.moods.collectAsState()
    val companions by vm.companionships.collectAsState()
    val kindergartens by vm.kindergartens.collectAsState()
    val games by vm.games.collectAsState()
    val books by vm.books.collectAsState()
    val medLogs by vm.medLogs.collectAsState()
    val reports by vm.medicalReports.collectAsState()
    val syncConfig by vm.syncConfig.collectAsState()
    val readOnly = !syncConfig.isPrimary

    var stats by remember { mutableStateOf<WeekStats?>(null) }
    LaunchedEffect(date) {
        stats = vm.loadWeekStats(date, 7)
    }

    var editingReport by remember { mutableStateOf<MedicalReport?>(null) }
    var showReportSheet by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    "回顾",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                DateNavBar(date = date, onDateChange = vm::setDate)
            }
        }

        item {
            SectionCard(emoji = "📋", title = "这一天的记录") {
                val night = nights.firstOrNull()
                val moodLine = MoodPeriods.all.joinToString("   ") { period ->
                    val score = moods.firstOrNull { it.period == period }?.score ?: 0
                    MoodPeriods.label(period).take(1) + (emojiByScore.getOrNull(score - 1) ?: "—")
                }
                val medSlots = medLogs.size
                val mealLine = meals
                    .sortedBy { MealTypes.all.indexOf(it.type) }
                    .joinToString("；") { "${MealTypes.label(it.type)} ${it.food.ifBlank { "已记" }}" }

                if (nights.isEmpty() && naps.isEmpty() && meals.isEmpty() && poops.isEmpty() &&
                    exercises.isEmpty() && baths.isEmpty() && outings.isEmpty() &&
                    moods.isEmpty() && companions.isEmpty() && kindergartens.isEmpty() &&
                    games.isEmpty() && books.isEmpty() && medLogs.isEmpty()
                ) {
                    EmptyHint("这一天还没有记录")
                } else {
                    if (night != null) {
                        InfoRow(
                            "夜间睡眠",
                            listOf(
                                night.sleepTime.ifBlank { night.bedtime },
                                night.morningWakeTime
                            ).filter { it.isNotBlank() }.joinToString(" → ") +
                                if (night.fallAsleepMinutes != null) "，入睡${night.fallAsleepMinutes}分" else ""
                        )
                    }
                    if (naps.isNotEmpty()) {
                        InfoRow(
                            "午睡",
                            naps.joinToString("；") {
                                listOf(it.startTime, it.endTime).filter { t -> t.isNotBlank() }
                                    .joinToString("-")
                            }
                        )
                    }
                    if (mealLine.isNotBlank()) InfoRow("饮食", mealLine)
                    if (poops.isNotEmpty()) InfoRow("排便", "${poops.size} 次")
                    if (medSlots > 0) InfoRow("用药打卡", "$medSlots 次")
                    if (exercises.isNotEmpty()) InfoRow("运动", "${exercises.sumOf { it.minutes }} 分钟")
                    if (baths.isNotEmpty()) InfoRow("洗澡", "${baths.size} 次")
                    if (outings.isNotEmpty()) {
                        InfoRow(
                            "外出",
                            outings.joinToString("；") { outing ->
                                outing.place.ifBlank { "未命名地点" } +
                                    if (outing.rating > 0) " ${outing.rating}分" else ""
                            }
                        )
                    }
                    if (moods.isNotEmpty()) InfoRow("情绪", moodLine)
                    if (companions.isNotEmpty()) {
                        InfoRow("陪伴", "${companions.sumOf { it.minutes }} 分钟")
                    }
                    val kg = kindergartens.firstOrNull()
                    if (kg != null) {
                        InfoRow(
                            "幼儿园",
                            listOf(
                                if (kg.moodScore > 0) "情绪${kg.moodScore}/5" else "",
                                if (kg.ateWell) "吃饭好" else "",
                                if (kg.napWell) "午睡好" else "",
                                if (kg.participated) "积极参与" else "",
                                kg.teacherFeedback
                            ).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                    if (games.isNotEmpty()) {
                        InfoRow("游戏", games.joinToString("；") { it.name.ifBlank { "未命名" } })
                    }
                    if (books.isNotEmpty()) {
                        InfoRow("绘本", books.joinToString("；") { it.title.ifBlank { "未命名" } })
                    }
                }
            }
        }

        item {
            SectionCard(emoji = "📊", title = "最近 7 天统计") {
                val s = stats
                if (s == null) {
                    EmptyHint("统计中…")
                } else {
                    Text(
                        "睡眠（小时）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    MiniBarChart(
                        values = s.days.map { it.sleepHours + it.napHours },
                        labels = s.days.map { DateUtils.weekday(it.date).removePrefix("周") },
                        color = MaterialTheme.colorScheme.primary
                    )
                    InfoRow("平均夜间睡眠", DateUtils.hourText(s.sleepAvgHours))
                    InfoRow("平均午睡", DateUtils.hourText(s.napAvgHours))
                    InfoRow(
                        "平均入睡耗时",
                        s.avgFallAsleepMinutes?.let { "%.0f 分钟".format(it) } ?: "—"
                    )
                    InfoRow(
                        "平均夜醒",
                        s.avgWakeCount?.let { "%.1f 次".format(it) } ?: "—"
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "运动（分钟）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    MiniBarChart(
                        values = s.days.map { it.exerciseMinutes.toFloat() },
                        labels = s.days.map { DateUtils.weekday(it.date).removePrefix("周") },
                        color = MaterialTheme.colorScheme.secondary
                    )
                    InfoRow("运动总时长", "${s.exerciseTotal} 分钟")

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "排便（次）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    MiniBarChart(
                        values = s.days.map { it.poopCount.toFloat() },
                        labels = s.days.map { DateUtils.weekday(it.date).removePrefix("周") },
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    InfoRow("一周排便", "${s.poopTotal} 次")

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "父母陪伴（分钟）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    MiniBarChart(
                        values = s.days.map { it.companionMinutes.toFloat() },
                        labels = s.days.map { DateUtils.weekday(it.date).removePrefix("周") },
                        color = MaterialTheme.colorScheme.primary
                    )

                    InfoRow("爸爸陪伴", "${s.dadMinutes} 分钟")
                    InfoRow("妈妈陪伴", "${s.momMinutes} 分钟")
                    InfoRow(
                        "陪伴到位自评",
                        s.companionAvgScore?.let { "%.1f / 5".format(it) } ?: "—"
                    )
                    InfoRow(
                        "放下手机比例",
                        s.phoneFreeRate?.let { "${(it * 100).toInt()}%" } ?: "—"
                    )

                    Spacer(Modifier.height(8.dp))
                    val moodValues = s.days.mapNotNull { it.moodAvg }
                    InfoRow(
                        "情绪周均",
                        if (moodValues.isEmpty()) "—" else "%.1f / 5".format(moodValues.average())
                    )
                    InfoRow("本周洗澡", "${s.bathCount} 次")
                    InfoRow("本周外出", "${s.outingCount} 次")
                    InfoRow(
                        "幼儿园情绪均分",
                        s.kindergartenAvg?.let { "%.1f / 5".format(it) } ?: "—"
                    )
                    InfoRow("游戏 / 绘本", "${s.gameCount} 个 / ${s.bookCount} 本")
                    InfoRow("绘本总时长", "${s.bookMinutes} 分钟")
                    InfoRow("用药打卡", "${s.medTakenCount} 次")
                    InfoRow("有三餐记录的天数", "${s.mealDays} 天")
                }
            }
        }

        item {
            SectionCard(
                emoji = "📍",
                title = "喜欢去的地方",
                subtitle = if (placeStats.isEmpty()) null else "共 ${placeStats.size} 个地点"
            ) {
                if (placeStats.isEmpty()) {
                    EmptyHint("录入外出活动并打分后，这里会按喜爱度汇总常去的地点")
                } else {
                    placeStats.take(15).forEachIndexed { index, stat ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                Modifier.width(24.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stat.place,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (stat.category.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Tag(stat.category)
                                    }
                                }
                                Text(
                                    buildString {
                                        append("去 ${stat.visits} 次")
                                        append(" · ")
                                        append(stat.avgRating?.let { "平均 %.1f 分".format(it) } ?: "未打分")
                                        if (stat.totalMinutes > 0) append(" · 累计 ${stat.totalMinutes} 分钟")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "🏥",
                title = "医生报告资料",
                subtitle = "接种、体检、化验、诊断记录",
                actionText = "添加",
                onAction = {
                    if (readOnly) {
                        vm.notifyReadOnly()
                    } else {
                        editingReport = null
                        showReportSheet = true
                    }
                }
            ) {
                if (reports.isEmpty()) {
                    EmptyHint("保存体检报告、化验单、医嘱，支持拍照留存")
                } else {
                    reports.forEach { report ->
                        ReportRow(report) {
                            if (readOnly) {
                                vm.notifyReadOnly()
                            } else {
                                editingReport = report
                                showReportSheet = true
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showReportSheet) {
        ReportSheet(
            existing = editingReport,
            date = date,
            onDismiss = { showReportSheet = false },
            onSave = {
                vm.saveReport(it)
                showReportSheet = false
            },
            onDelete = if (editingReport != null) {
                {
                    vm.deleteReport(editingReport!!)
                    showReportSheet = false
                }
            } else null
        )
    }
}

@Composable
private fun ReportRow(report: MedicalReport, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${report.date}  ${report.title.ifBlank { "医生报告" }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            val sub = listOf(report.hospital, report.doctor, report.content)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (report.photos.isNotBlank()) {
                PhotoThumbs(report.photos.toPhotoList(), 44.dp)
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.Edit,
                "编辑",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
