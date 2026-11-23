@file:OptIn(ExperimentalMaterial3Api::class)

package com.cola.record.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cola.record.data.Bath
import com.cola.record.data.BookRecord
import com.cola.record.data.Companionship
import com.cola.record.data.Exercise
import com.cola.record.data.Game
import com.cola.record.data.Kindergarten
import com.cola.record.data.Meal
import com.cola.record.data.MedCategory
import com.cola.record.data.MedLog
import com.cola.record.data.Mood
import com.cola.record.data.Nap
import com.cola.record.data.NightSleep
import com.cola.record.data.Poop
import com.cola.record.data.toPhotoList
import com.cola.record.data.MainViewModel
import com.cola.record.util.DateUtils

private val moodEmojis = listOf("😭", "😞", "😐", "😄", "🤩")

@Composable
fun TodayScreen(vm: MainViewModel, onOpenMeds: () -> Unit) {
    val date by vm.selectedDate.collectAsState()
    val nights by vm.nights.collectAsState()
    val naps by vm.naps.collectAsState()
    val meals by vm.meals.collectAsState()
    val poops by vm.poops.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val baths by vm.baths.collectAsState()
    val outings by vm.outings.collectAsState()
    val moods by vm.moods.collectAsState()
    val companions by vm.companionships.collectAsState()
    val kindergartens by vm.kindergartens.collectAsState()
    val games by vm.games.collectAsState()
    val books by vm.books.collectAsState()
    val medCategories by vm.medCategories.collectAsState()
    val medLogs by vm.medLogs.collectAsState()
    val weekBaths by vm.weekBaths.collectAsState()
    val syncConfig by vm.syncConfig.collectAsState()
    val readOnly = !syncConfig.isPrimary

    var sheet by remember { mutableStateOf<TodaySheet?>(null) }

    fun openSheet(target: TodaySheet) {
        if (readOnly) {
            vm.notifyReadOnly()
        } else {
            sheet = target
        }
    }

    val activeCats = medCategories.filter { it.active }
    val medSlots = activeCats.sumOf { cat -> cat.plannedTimes.split(",").count { it.isNotBlank() } }
    val medDone = activeCats.sumOf { cat ->
        cat.plannedTimes.split(",").filter { it.isNotBlank() }.count { slot ->
            medLogs.any { it.categoryId == cat.id && it.slot == slot.trim() }
        }
    }
    val medsComplete = medSlots > 0 && medDone >= medSlots

    val recordedGroups = listOf(
        nights.isNotEmpty(),
        naps.isNotEmpty(),
        meals.isNotEmpty(),
        poops.isNotEmpty(),
        medsComplete,
        exercises.isNotEmpty(),
        baths.isNotEmpty(),
        outings.isNotEmpty(),
        moods.isNotEmpty(),
        companions.isNotEmpty(),
        kindergartens.isNotEmpty(),
        games.isNotEmpty(),
        books.isNotEmpty()
    ).count { it }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "可乐成长记",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "已记录 $recordedGroups/13 类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                DateNavBar(date = date, onDateChange = vm::setDate)
            }
        }

        item {
            SectionCard(
                emoji = "🌙",
                title = "夜间睡眠",
                subtitle = "当晚入睡 → 次日清晨",
                actionText = if (nights.isEmpty()) "记录" else "编辑",
                onAction = { openSheet(TodaySheet.Night(nights.firstOrNull())) }
            ) {
                val night = nights.firstOrNull()
                if (night == null) {
                    EmptyHint("记录上床、入睡、夜醒和早上醒来的情况")
                } else {
                    NightSummary(night)
                }
            }
        }

        item {
            SectionCard(
                emoji = "😴",
                title = "午睡",
                subtitle = napSubtitle(naps),
                actionText = "添加",
                onAction = { openSheet(TodaySheet.NapT(null)) }
            ) {
                if (naps.isEmpty()) {
                    EmptyHint("记录午睡起止时间和入睡快慢")
                } else {
                    naps.forEach { nap ->
                        EntryRow(
                            title = napTitle(nap),
                            subtitle = napSubtitleLine(nap),
                            onEdit = { openSheet(TodaySheet.NapT(nap)) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "🍚",
                title = "三餐与点心",
                subtitle = "早餐 · 午餐 · 晚餐 · 上午/下午点心"
            ) {
                MealTypes.all.forEach { type ->
                    val meal = meals.firstOrNull { it.type == type }
                    MealRow(
                        type = type,
                        meal = meal,
                        onClick = { openSheet(TodaySheet.MealT(meal, type)) }
                    )
                }
            }
        }

        item {
            SectionCard(
                emoji = "💩",
                title = "拉臭臭",
                subtitle = if (poops.isEmpty()) null else "今天 ${poops.size} 次",
                actionText = "添加",
                onAction = { openSheet(TodaySheet.PoopT(null)) }
            ) {
                if (poops.isEmpty()) {
                    EmptyHint("记录时间、性状、颜色、量，可上传照片")
                } else {
                    poops.forEach { poop ->
                        EntryRow(
                            title = listOf(
                                poop.time.ifBlank { "时间未记" },
                                poop.shape
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            subtitle = listOf(
                                poop.color,
                                if (poop.amount > 0) "量${listOf("少", "中", "多").getOrElse(poop.amount - 1) { "" }}" else "",
                                poop.note
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            photos = poop.photos.toPhotoList(),
                            onEdit = { openSheet(TodaySheet.PoopT(poop)) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "💊",
                title = "用药打卡",
                subtitle = if (activeCats.isEmpty()) {
                    "还没有添加正在吃的药物"
                } else {
                    "今日 $medDone/$medSlots 次已完成"
                },
                actionText = "管理",
                onAction = onOpenMeds
            ) {
                if (activeCats.isEmpty()) {
                    EmptyHint("在「药物」页添加类别，如益生菌每天 2 次，这里会生成每日打卡")
                } else {
                    activeCats.forEach { cat ->
                        MedicineRow(
                            cat = cat,
                            logs = medLogs,
                            onToggle = { slot, taken -> vm.toggleMed(cat.id, date, slot, taken) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "🏃",
                title = "运动",
                subtitle = if (exercises.isEmpty()) null
                else "今日共 ${exercises.sumOf { it.minutes }} 分钟",
                actionText = "添加",
                onAction = { openSheet(TodaySheet.ExerciseT(null)) }
            ) {
                if (exercises.isEmpty()) {
                    EmptyHint("记录户外活动、跑跳、骑车等运动量")
                } else {
                    exercises.forEach { exercise ->
                        EntryRow(
                            title = listOf(
                                exercise.type.ifBlank { "运动" },
                                if (exercise.minutes > 0) "${exercise.minutes}分钟" else ""
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            subtitle = listOf(
                                exercise.time,
                                if (exercise.intensity > 0)
                                    "强度${listOf("轻", "中", "大").getOrElse(exercise.intensity - 1) { "" }}" else "",
                                exercise.note
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            onEdit = { openSheet(TodaySheet.ExerciseT(exercise)) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "🛁",
                title = "洗澡",
                subtitle = "本周已洗 ${weekBaths.size} 次",
                actionText = "添加",
                onAction = { openSheet(TodaySheet.BathT(null)) }
            ) {
                if (baths.isEmpty()) {
                    EmptyHint("记录今天洗没洗澡、时长和是否洗头")
                } else {
                    baths.forEach { bath ->
                        EntryRow(
                            title = listOf(
                                bath.time.ifBlank { "洗澡" },
                                if (bath.durationMinutes > 0) "${bath.durationMinutes}分钟" else ""
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            subtitle = listOf(
                                if (bath.washedHair) "洗头了" else "",
                                bath.note
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            onEdit = { openSheet(TodaySheet.BathT(bath)) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "📍",
                title = "外出活动",
                subtitle = if (outings.isEmpty()) "今天去了哪里？好不好玩？"
                else "今天 ${outings.size} 个地点",
                actionText = "添加",
                onAction = { openSheet(TodaySheet.OutingT(null)) }
            ) {
                if (outings.isEmpty()) {
                    EmptyHint("记录外出地点并打分，回顾页会汇总宝宝最喜欢去的地方")
                } else {
                    outings.forEach { outing ->
                        EntryRow(
                            title = listOf(
                                outing.place.ifBlank { "未命名地点" },
                                if (outing.rating > 0) "喜爱 ${outing.rating}/5" else "未打分"
                            ).joinToString(" · "),
                            subtitle = listOf(
                                outing.category,
                                if (outing.minutes > 0) "${outing.minutes}分钟" else "",
                                outing.time,
                                outing.note
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            photos = outing.photos.toPhotoList(),
                            onEdit = { openSheet(TodaySheet.OutingT(outing)) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "😊",
                title = "情绪",
                subtitle = "早 · 中 · 晚 三个时段"
            ) {
                MoodPeriods.all.forEach { period ->
                    val mood = moods.firstOrNull { it.period == period }
                    MoodQuickRow(
                        period = period,
                        mood = mood,
                        onSet = { score -> vm.setMoodScore(date, period, score, mood) },
                        onOpen = { openSheet(TodaySheet.MoodT(mood, period)) }
                    )
                }
            }
        }

        item {
            SectionCard(
                emoji = "👨‍👩‍👧",
                title = "父母陪伴",
                subtitle = if (companions.isEmpty()) "监督今天有没有高质量陪娃"
                else "今日共 ${companions.sumOf { it.minutes }} 分钟",
                actionText = "添加",
                onAction = { openSheet(TodaySheet.CompanionT(null)) }
            ) {
                if (companions.isEmpty()) {
                    EmptyHint("记录谁陪、陪多久、是否放下手机、自己的到位自评")
                } else {
                    companions.forEach { item ->
                        EntryRow(
                            title = listOf(
                                item.who.ifBlank { "陪伴" },
                                "${item.minutes}分钟"
                            ).joinToString(" · "),
                            subtitle = listOf(
                                if (item.phoneFree) "放下手机 ✓" else "看手机 ✗",
                                if (item.selfScore > 0) "自评 ${item.selfScore}/5" else "",
                                item.activity,
                                item.note
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            onEdit = { openSheet(TodaySheet.CompanionT(item)) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "🏫",
                title = "幼儿园上午表现",
                actionText = if (kindergartens.isEmpty()) "记录" else "编辑",
                onAction = { openSheet(TodaySheet.KindergartenT(kindergartens.firstOrNull())) }
            ) {
                val kg = kindergartens.firstOrNull()
                if (kg == null) {
                    EmptyHint("记录上午情绪、吃饭、午睡、参与活动和老师反馈")
                } else {
                    if (kg.moodScore > 0) InfoRow("上午情绪", "${kg.moodScore}/5")
                    InfoRow(
                        "状态",
                        listOf(
                            if (kg.ateWell) "吃饭好" else "",
                            if (kg.napWell) "午睡好" else "",
                            if (kg.participated) "积极参与" else ""
                        ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" }
                    )
                    if (kg.teacherFeedback.isNotBlank()) InfoRow("老师反馈", kg.teacherFeedback)
                    if (kg.note.isNotBlank()) InfoRow("备注", kg.note)
                }
            }
        }

        item {
            SectionCard(
                emoji = "🎲",
                title = "游戏设计",
                subtitle = if (games.isEmpty()) null else "今天 ${games.size} 个",
                actionText = "添加",
                onAction = { openSheet(TodaySheet.GameT(null)) }
            ) {
                if (games.isEmpty()) {
                    EmptyHint("记录每天设计的亲子游戏、锻炼目标和玩法")
                } else {
                    games.forEach { game ->
                        EntryRow(
                            title = (game.name.ifBlank { "未命名游戏" }) +
                                if (game.played) " · 已玩" else " · 未玩",
                            subtitle = listOf(
                                game.goal,
                                if (game.babyReaction > 0) "喜爱度 ${game.babyReaction}/5" else "",
                                game.description
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            photos = game.photos.toPhotoList(),
                            onEdit = { openSheet(TodaySheet.GameT(game)) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                emoji = "📖",
                title = "绘本讲解",
                subtitle = if (books.isEmpty()) null
                else "今天 ${books.size} 本 · ${books.sumOf { it.minutes }} 分钟",
                actionText = "添加",
                onAction = { openSheet(TodaySheet.BookT(null)) }
            ) {
                if (books.isEmpty()) {
                    EmptyHint("记录绘本名称、讲了几遍、可乐的反应")
                } else {
                    books.forEach { book ->
                        EntryRow(
                            title = book.title.ifBlank { "未命名绘本" },
                            subtitle = listOf(
                                if (book.minutes > 0) "${book.minutes}分钟" else "",
                                "${book.timesRead}遍",
                                if (book.babyReaction > 0) "反应 ${book.babyReaction}/5" else "",
                                book.note
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            photos = book.photos.toPhotoList(),
                            onEdit = { openSheet(TodaySheet.BookT(book)) }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    when (val current = sheet) {
        null -> Unit
        is TodaySheet.Night -> NightSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveNight(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteNight(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.NapT -> NapSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveNap(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteNap(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.MealT -> MealSheet(
            existing = current.existing,
            date = date,
            type = current.type,
            onDismiss = { sheet = null },
            onSave = { vm.saveMeal(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteMeal(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.PoopT -> PoopSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.savePoop(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deletePoop(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.ExerciseT -> ExerciseSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveExercise(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteExercise(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.BathT -> BathSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveBath(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteBath(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.OutingT -> OutingSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveOuting(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteOuting(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.MoodT -> MoodSheet(
            existing = current.existing,
            date = date,
            period = current.period,
            onDismiss = { sheet = null },
            onSave = { vm.saveMood(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteMood(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.CompanionT -> CompanionSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveCompanionship(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteCompanionship(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.KindergartenT -> KindergartenSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveKindergarten(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteKindergarten(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.GameT -> GameSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveGame(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteGame(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.BookT -> BookSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveBook(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteBook(current.existing); sheet = null }
            } else null
        )
        is TodaySheet.ReportT -> ReportSheet(
            existing = current.existing,
            date = date,
            onDismiss = { sheet = null },
            onSave = { vm.saveReport(it); sheet = null },
            onDelete = if (current.existing != null) {
                { vm.deleteReport(current.existing); sheet = null }
            } else null
        )
    }
}

@Composable
private fun NightSummary(night: NightSleep) {
    if (night.bedtime.isNotBlank() || night.sleepTime.isNotBlank()) {
        InfoRow(
            "上床 / 入睡",
            listOf(night.bedtime, night.sleepTime).filter { it.isNotBlank() }.joinToString(" / ")
        )
    }
    InfoRow(
        "入睡时长",
        night.fallAsleepMinutes?.let { "$it 分钟" } ?: "—"
    )
    InfoRow(
        "夜醒",
        "${night.wakeCount} 次" +
            if (night.wakeTimes.isNotBlank()) "（${night.wakeTimes}）" else ""
    )
    InfoRow("早上醒来", night.morningWakeTime)
    val total = DateUtils.durationMinutes(
        night.sleepTime.ifBlank { night.bedtime },
        night.morningWakeTime
    )
    InfoRow("夜间时长", DateUtils.durationText(total))
    if (night.quality > 0) {
        InfoRow("睡眠质量", qualityLabels.getOrElse(night.quality - 1) { "" })
    }
    if (night.wakeReason.isNotBlank()) InfoRow("夜醒情况", night.wakeReason)
    if (night.note.isNotBlank()) InfoRow("备注", night.note)
}

@Composable
private fun EntryRow(
    title: String,
    subtitle: String = "",
    photos: List<String> = emptyList(),
    onEdit: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (photos.isNotEmpty()) {
                PhotoThumbs(photos, 44.dp)
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
}

@Composable
private fun MealRow(type: String, meal: Meal?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(72.dp)) {
            Text(
                MealTypes.label(type),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            meal?.time?.takeIf { it.isNotBlank() }?.let { time ->
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                meal?.food?.ifBlank { "未记录" } ?: "未记录",
                style = MaterialTheme.typography.bodyMedium,
                color = if (meal == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val sub = listOf(
                if ((meal?.amount ?: 0) > 0)
                    "食量${amountLabels.getOrElse(meal!!.amount - 1) { "" }}" else "",
                meal?.note ?: ""
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            meal?.photos?.takeIf { it.isNotBlank() }?.let { photos ->
                PhotoThumbs(photos.toPhotoList(), 40.dp)
            }
        }
        Icon(
            Icons.Filled.Edit,
            "编辑",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MoodQuickRow(
    period: String,
    mood: Mood?,
    onSet: (Int) -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            MoodPeriods.label(period),
            Modifier.width(44.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            moodEmojis.forEachIndexed { index, emoji ->
                val selected = mood?.score == index + 1
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSet(index + 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 20.sp)
                }
            }
        }
        if (!mood?.note.isNullOrBlank()) {
            Text(
                "有备注",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onOpen) {
            Icon(
                Icons.Filled.Edit,
                "编辑备注",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MedicineRow(
    cat: MedCategory,
    logs: List<MedLog>,
    onToggle: (slot: String, taken: Boolean) -> Unit
) {
    val slots = cat.plannedTimes.split(",").map { it.trim() }.filter { it.isNotBlank() }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cat.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (cat.dosage.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    cat.dosage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (slots.isEmpty()) {
            Text(
                "未设置服用时间，去「药物」页编辑",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                slots.forEach { slot ->
                    val log = logs.firstOrNull { it.categoryId == cat.id && it.slot == slot }
                    FilterChip(
                        selected = log != null,
                        onClick = { onToggle(slot, log == null) },
                        label = {
                            Text(
                                if (log != null) "$slot ✓" else slot
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun napTitle(nap: Nap): String {
    val range = listOf(nap.startTime, nap.endTime)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
    return range.ifBlank { "午睡" }
}

private fun napSubtitleLine(nap: Nap): String {
    val duration = DateUtils.durationMinutes(nap.startTime, nap.endTime)
    return listOf(
        duration?.let { "约 ${DateUtils.durationText(it)}" } ?: "",
        if (nap.quality > 0) "质量${qualityLabels.getOrElse(nap.quality - 1) { "" }}" else "",
        nap.note
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun napSubtitle(naps: List<Nap>): String? {
    if (naps.isEmpty()) return null
    val total = naps.sumOf { DateUtils.durationMinutes(it.startTime, it.endTime) ?: 0 }
    return "今天共 ${naps.size} 次 · 约 ${DateUtils.durationText(total)}"
}
