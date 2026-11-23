package com.cola.record.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cola.record.data.Bath
import com.cola.record.data.BookRecord
import com.cola.record.data.Companionship
import com.cola.record.data.Exercise
import com.cola.record.data.Game
import com.cola.record.data.Kindergarten
import com.cola.record.data.Meal
import com.cola.record.data.MedicalReport
import com.cola.record.data.MedCategory
import com.cola.record.data.Mood
import com.cola.record.data.Nap
import com.cola.record.data.NightSleep
import com.cola.record.data.Outing
import com.cola.record.data.Poop
import com.cola.record.data.toPhotoList
import com.cola.record.data.toPhotoString
import com.cola.record.util.DateUtils

sealed interface TodaySheet {
    data class Night(val existing: NightSleep?) : TodaySheet
    data class NapT(val existing: Nap?) : TodaySheet
    data class MealT(val existing: Meal?, val type: String) : TodaySheet
    data class PoopT(val existing: Poop?) : TodaySheet
    data class ExerciseT(val existing: Exercise?) : TodaySheet
    data class BathT(val existing: Bath?) : TodaySheet
    data class OutingT(val existing: Outing?) : TodaySheet
    data class MoodT(val existing: Mood?, val period: String) : TodaySheet
    data class CompanionT(val existing: Companionship?) : TodaySheet
    data class KindergartenT(val existing: Kindergarten?) : TodaySheet
    data class GameT(val existing: Game?) : TodaySheet
    data class BookT(val existing: BookRecord?) : TodaySheet
    data class ReportT(val existing: MedicalReport?) : TodaySheet
}

object MealTypes {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK1 = "snack1"
    const val SNACK2 = "snack2"

    val all = listOf(BREAKFAST, LUNCH, DINNER, SNACK1, SNACK2)

    fun label(type: String): String = when (type) {
        BREAKFAST -> "早餐"
        LUNCH -> "午餐"
        DINNER -> "晚餐"
        SNACK1 -> "上午点心"
        SNACK2 -> "下午点心"
        else -> "餐食"
    }
}

object MoodPeriods {
    const val MORNING = "morning"
    const val NOON = "noon"
    const val EVENING = "evening"

    val all = listOf(MORNING, NOON, EVENING)

    fun label(period: String): String = when (period) {
        MORNING -> "早上"
        NOON -> "中午"
        EVENING -> "晚上"
        else -> period
    }
}

val moodLabels = listOf("😭 哭闹", "😞 低落", "😐 平静", "😄 开心", "🤩 超开心")
val qualityLabels = listOf("差", "一般", "好")
val amountLabels = listOf("很少", "一般", "不错", "光盘")

@Composable
fun NightSheet(
    existing: NightSleep?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (NightSleep) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var bedtime by remember { mutableStateOf(existing?.bedtime ?: "") }
    var sleepTime by remember { mutableStateOf(existing?.sleepTime ?: "") }
    var fallAsleep by remember { mutableStateOf(existing?.fallAsleepMinutes ?: 0) }
    var wakeCount by remember { mutableStateOf(existing?.wakeCount ?: 0) }
    var wakeTimes by remember { mutableStateOf(existing?.wakeTimes ?: "") }
    var wakeReason by remember { mutableStateOf(existing?.wakeReason ?: "") }
    var morningWake by remember { mutableStateOf(existing?.morningWakeTime ?: "") }
    var quality by remember { mutableStateOf(existing?.quality ?: 0) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val bedMinutes = DateUtils.durationMinutes(sleepTime.ifBlank { bedtime }, morningWake)
    val sleepMinutes = bedMinutes?.let { if (fallAsleep > 0) it - fallAsleep else it }

    FormSheet(
        title = "🌙 夜间睡眠",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: NightSleep(date = date)).copy(
                    date = date,
                    bedtime = bedtime,
                    sleepTime = sleepTime,
                    fallAsleepMinutes = if (fallAsleep == 0) null else fallAsleep,
                    wakeCount = wakeCount,
                    wakeTimes = wakeTimes,
                    wakeReason = wakeReason,
                    morningWakeTime = morningWake,
                    quality = quality,
                    note = note
                )
            )
        }
    ) {
        TimeField("上床时间", bedtime) { bedtime = it }
        TimeField("实际入睡时间", sleepTime) { sleepTime = it }
        NumberField("入睡时长", fallAsleep, { fallAsleep = it }, suffix = "分钟")
        TimeField("早上醒来时间", morningWake) { morningWake = it }
        NumberField("夜醒次数", wakeCount, { wakeCount = it }, suffix = "次")
        TextFieldRow(
            "夜醒时间点",
            wakeTimes,
            { wakeTimes = it },
            placeholder = "如 02:10, 04:30（多个用逗号分隔）"
        )
        TextFieldRow(
            "夜醒原因 / 情况",
            wakeReason,
            { wakeReason = it },
            minLines = 2,
            placeholder = "如：要喝水、找妈妈、翻身醒来"
        )
        ScoreRow("睡眠质量", quality, qualityLabels) { quality = it }
        TextFieldRow("备注", note, { note = it }, minLines = 2)
        if (bedMinutes != null) {
            Text(
                "在床时长约 ${DateUtils.durationText(bedMinutes)}" +
                    if (sleepMinutes != null && fallAsleep > 0) "，估算入睡后睡眠 ${DateUtils.durationText(sleepMinutes)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun NapSheet(
    existing: Nap?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Nap) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var startTime by remember { mutableStateOf(existing?.startTime ?: "") }
    var endTime by remember { mutableStateOf(existing?.endTime ?: "") }
    var fallAsleep by remember { mutableStateOf(existing?.fallAsleepMinutes ?: 0) }
    var quality by remember { mutableStateOf(existing?.quality ?: 0) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val duration = DateUtils.durationMinutes(startTime, endTime)

    FormSheet(
        title = "😴 午睡",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Nap(date = date)).copy(
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    fallAsleepMinutes = if (fallAsleep == 0) null else fallAsleep,
                    quality = quality,
                    note = note
                )
            )
        }
    ) {
        TimeField("入睡时间", startTime) { startTime = it }
        TimeField("醒来时间", endTime) { endTime = it }
        NumberField("入睡时长", fallAsleep, { fallAsleep = it }, suffix = "分钟")
        ScoreRow("睡眠质量", quality, qualityLabels) { quality = it }
        TextFieldRow("备注", note, { note = it }, minLines = 2)
        if (duration != null) {
            Text(
                "午睡时长约 ${DateUtils.durationText(duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MealSheet(
    existing: Meal?,
    date: String,
    type: String,
    onDismiss: () -> Unit,
    onSave: (Meal) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var time by remember { mutableStateOf(existing?.time ?: "") }
    var food by remember { mutableStateOf(existing?.food ?: "") }
    var amount by remember { mutableStateOf(existing?.amount ?: 0) }
    var photos by remember { mutableStateOf((existing?.photos ?: "").toPhotoList()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "🍚 ${MealTypes.label(type)}",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Meal(date = date, type = type)).copy(
                    date = date,
                    type = type,
                    time = time,
                    food = food,
                    amount = amount,
                    photos = photos.toPhotoString(),
                    note = note
                )
            )
        }
    ) {
        TimeField("吃饭时间", time) { time = it }
        TextFieldRow(
            "吃了什么",
            food,
            { food = it },
            minLines = 3,
            placeholder = "如：小米粥、鸡蛋、西兰花、半根香蕉"
        )
        ScoreRow("食量", amount, amountLabels) { amount = it }
        Text("照片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it })
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
fun PoopSheet(
    existing: Poop?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Poop) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var time by remember { mutableStateOf(existing?.time ?: "") }
    var shape by remember { mutableStateOf(existing?.shape ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: "") }
    var amount by remember { mutableStateOf(existing?.amount ?: 0) }
    var photos by remember { mutableStateOf((existing?.photos ?: "").toPhotoList()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "💩 拉臭臭",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Poop(date = date)).copy(
                    date = date,
                    time = time,
                    shape = shape,
                    color = color,
                    amount = amount,
                    photos = photos.toPhotoString(),
                    note = note
                )
            )
        }
    ) {
        TimeField("时间", time) { time = it }
        ChoiceChips("性状", listOf("干硬", "成形软便", "稀糊", "水样"), shape) { shape = it }
        ChoiceChips("颜色", listOf("黄色", "黄绿色", "棕色", "黑色", "红色", "白色"), color) { color = it }
        ScoreRow("量", amount, listOf("少", "中", "多")) { amount = it }
        Text("照片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it })
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
fun ExerciseSheet(
    existing: Exercise?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Exercise) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var type by remember { mutableStateOf(existing?.type ?: "") }
    var minutes by remember { mutableStateOf(existing?.minutes ?: 0) }
    var intensity by remember { mutableStateOf(existing?.intensity ?: 0) }
    var time by remember { mutableStateOf(existing?.time ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "🏃 运动",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Exercise(date = date)).copy(
                    date = date,
                    type = type,
                    minutes = minutes,
                    intensity = intensity,
                    time = time,
                    note = note
                )
            )
        }
    ) {
        TextFieldRow("运动类型", type, { type = it }, placeholder = "如：户外跑跳、骑车、球类、游泳")
        QuickChips(listOf("户外跑跳", "骑车", "球类", "游泳", "平衡车", "亲子早教")) { type = it }
        NumberField("时长", minutes, { minutes = it }, suffix = "分钟")
        ScoreRow("强度", intensity, listOf("轻", "中", "大")) { intensity = it }
        TimeField("时间", time) { time = it }
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
private fun QuickChips(
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            androidx.compose.material3.AssistChip(
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
fun BathSheet(
    existing: Bath?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Bath) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var time by remember { mutableStateOf(existing?.time ?: "") }
    var duration by remember { mutableStateOf(existing?.durationMinutes ?: 0) }
    var washedHair by remember { mutableStateOf(existing?.washedHair ?: false) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "🛁 洗澡",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Bath(date = date)).copy(
                    date = date,
                    time = time,
                    durationMinutes = duration,
                    washedHair = washedHair,
                    note = note
                )
            )
        }
    ) {
        TimeField("洗澡时间", time) { time = it }
        NumberField("时长", duration, { duration = it }, suffix = "分钟")
        LabeledSwitch("今天洗头了", washedHair) { washedHair = it }
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
fun OutingSheet(
    existing: Outing?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Outing) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var place by remember { mutableStateOf(existing?.place ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var rating by remember { mutableStateOf(existing?.rating ?: 0) }
    var minutes by remember { mutableStateOf(existing?.minutes ?: 0) }
    var time by remember { mutableStateOf(existing?.time ?: "") }
    var photos by remember { mutableStateOf((existing?.photos ?: "").toPhotoList()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "📍 外出活动",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Outing(date = date)).copy(
                    date = date,
                    place = place.trim(),
                    category = category,
                    rating = rating,
                    minutes = minutes,
                    time = time,
                    note = note,
                    photos = photos.toPhotoString()
                )
            )
        }
    ) {
        TextFieldRow(
            "活动地点",
            place,
            { place = it },
            placeholder = "如：XX公园、XX商场儿童乐园、XX水族馆"
        )
        ChoiceChips(
            "地点类型",
            listOf("公园", "商场", "游乐场", "水族馆", "图书馆", "亲子乐园", "其他"),
            category
        ) { category = it }
        ScoreRow("喜爱打分", rating, listOf("1", "2", "3", "4", "5")) { rating = it }
        NumberField("活动时长", minutes, { minutes = it }, suffix = "分钟")
        TimeField("时间", time) { time = it }
        Text("照片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it })
        TextFieldRow(
            "备注",
            note,
            { note = it },
            minLines = 2,
            placeholder = "如：玩了滑梯、看了鱼、和小朋友一起玩"
        )
    }
}

@Composable
fun MoodSheet(
    existing: Mood?,
    date: String,
    period: String,
    onDismiss: () -> Unit,
    onSave: (Mood) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var score by remember { mutableStateOf(existing?.score ?: 0) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "😊 情绪 · ${MoodPeriods.label(period)}",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Mood(date = date, period = period)).copy(
                    date = date,
                    period = period,
                    score = score,
                    note = note
                )
            )
        }
    ) {
        ScoreRow("这个时段的状态", score, moodLabels) { score = it }
        TextFieldRow("备注", note, { note = it }, minLines = 2, placeholder = "因为什么开心 / 闹脾气？")
    }
}

@Composable
fun CompanionSheet(
    existing: Companionship?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Companionship) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var who by remember { mutableStateOf(existing?.who ?: "妈妈") }
    var minutes by remember { mutableStateOf(existing?.minutes ?: 0) }
    var activity by remember { mutableStateOf(existing?.activity ?: "") }
    var phoneFree by remember { mutableStateOf(existing?.phoneFree ?: true) }
    var selfScore by remember { mutableStateOf(existing?.selfScore ?: 0) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "👨‍👩‍👧 父母陪伴",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Companionship(date = date)).copy(
                    date = date,
                    who = who,
                    minutes = minutes,
                    activity = activity,
                    phoneFree = phoneFree,
                    selfScore = selfScore,
                    note = note
                )
            )
        }
    ) {
        ChoiceChips("谁陪伴", listOf("爸爸", "妈妈", "双方"), who) { who = it }
        NumberField("高质量陪伴时长", minutes, { minutes = it }, suffix = "分钟")
        TextFieldRow(
            "做了什么",
            activity,
            { activity = it },
            minLines = 2,
            placeholder = "如：一起搭积木、公园散步、读绘本"
        )
        LabeledSwitch("全程放下手机", phoneFree) { phoneFree = it }
        ScoreRow("陪伴到位自评", selfScore, listOf("1", "2", "3", "4", "5")) { selfScore = it }
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
fun KindergartenSheet(
    existing: Kindergarten?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Kindergarten) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var moodScore by remember { mutableStateOf(existing?.moodScore ?: 0) }
    var ateWell by remember { mutableStateOf(existing?.ateWell ?: false) }
    var napWell by remember { mutableStateOf(existing?.napWell ?: false) }
    var participated by remember { mutableStateOf(existing?.participated ?: false) }
    var feedback by remember { mutableStateOf(existing?.teacherFeedback ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "🏫 幼儿园上午表现",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Kindergarten(date = date)).copy(
                    date = date,
                    moodScore = moodScore,
                    ateWell = ateWell,
                    napWell = napWell,
                    participated = participated,
                    teacherFeedback = feedback,
                    note = note
                )
            )
        }
    ) {
        ScoreRow("上午情绪", moodScore, listOf("1", "2", "3", "4", "5")) { moodScore = it }
        LabeledSwitch("吃饭情况好", ateWell) { ateWell = it }
        LabeledSwitch("午睡情况好", napWell) { napWell = it }
        LabeledSwitch("积极参与活动", participated) { participated = it }
        TextFieldRow(
            "老师反馈",
            feedback,
            { feedback = it },
            minLines = 3,
            placeholder = "记录老师说的话或家园联系册内容"
        )
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
fun GameSheet(
    existing: Game?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Game) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var goal by remember { mutableStateOf(existing?.goal ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var played by remember { mutableStateOf(existing?.played ?: false) }
    var reaction by remember { mutableStateOf(existing?.babyReaction ?: 0) }
    var photos by remember { mutableStateOf((existing?.photos ?: "").toPhotoList()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "🎲 游戏设计",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: Game(date = date)).copy(
                    date = date,
                    name = name,
                    goal = goal,
                    description = description,
                    played = played,
                    babyReaction = reaction,
                    photos = photos.toPhotoString(),
                    note = note
                )
            )
        }
    ) {
        TextFieldRow("游戏名称", name, { name = it }, placeholder = "如：颜色分类小搬运工")
        ChoiceChips(
            "锻炼目标",
            listOf("大运动", "精细动作", "语言", "认知", "社交", "艺术"),
            goal
        ) { goal = it }
        TextFieldRow(
            "玩法设计",
            description,
            { description = it },
            minLines = 4,
            placeholder = "准备什么、怎么玩、想锻炼什么能力"
        )
        LabeledSwitch("今天玩过了", played) { played = it }
        ScoreRow("可乐喜爱度", reaction, listOf("1", "2", "3", "4", "5")) { reaction = it }
        Text("照片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it })
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
fun BookSheet(
    existing: BookRecord?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (BookRecord) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var minutes by remember { mutableStateOf(existing?.minutes ?: 0) }
    var timesRead by remember { mutableStateOf(existing?.timesRead ?: 1) }
    var reaction by remember { mutableStateOf(existing?.babyReaction ?: 0) }
    var photos by remember { mutableStateOf((existing?.photos ?: "").toPhotoList()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "📖 绘本讲解",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: BookRecord(date = date)).copy(
                    date = date,
                    title = title,
                    minutes = minutes,
                    timesRead = timesRead,
                    babyReaction = reaction,
                    photos = photos.toPhotoString(),
                    note = note
                )
            )
        }
    ) {
        TextFieldRow("绘本名称", title, { title = it }, placeholder = "如：好饿的毛毛虫")
        NumberField("讲解时长", minutes, { minutes = it }, suffix = "分钟")
        NumberField("今天读了几遍", timesRead, { timesRead = it }, suffix = "遍")
        ScoreRow("可乐反应", reaction, listOf("1", "2", "3", "4", "5")) { reaction = it }
        Text("照片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it })
        TextFieldRow("备注", note, { note = it }, minLines = 2, placeholder = "讲了什么、可乐的反应、复述了什么")
    }
}

@Composable
fun ReportSheet(
    existing: MedicalReport?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (MedicalReport) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var reportDate by remember { mutableStateOf(existing?.date ?: date) }
    var hospital by remember { mutableStateOf(existing?.hospital ?: "") }
    var doctor by remember { mutableStateOf(existing?.doctor ?: "") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }
    var photos by remember { mutableStateOf((existing?.photos ?: "").toPhotoList()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    FormSheet(
        title = "🏥 医生报告资料",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: MedicalReport(date = reportDate)).copy(
                    date = reportDate,
                    hospital = hospital,
                    doctor = doctor,
                    title = title,
                    content = content,
                    photos = photos.toPhotoString(),
                    note = note
                )
            )
        }
    ) {
        DateField("报告 / 就诊日期", reportDate) { reportDate = it }
        TextFieldRow("医院 / 机构", hospital, { hospital = it })
        TextFieldRow("医生", doctor, { doctor = it })
        TextFieldRow("报告主题", title, { title = it }, placeholder = "如：血常规、体检报告、过敏原检测")
        TextFieldRow(
            "结论 / 医嘱",
            content,
            { content = it },
            minLines = 4,
            placeholder = "记录关键指标、诊断结论、医生嘱咐"
        )
        Text("报告照片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhotoEditor(photos, { photos = it })
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}

@Composable
fun CategorySheet(
    existing: MedCategory?,
    onDismiss: () -> Unit,
    onSave: (MedCategory) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dosage by remember { mutableStateOf(existing?.dosage ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val times = remember {
        mutableStateListOf<String>().apply {
            addAll(
                (existing?.plannedTimes ?: "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf("08:00") }
            )
        }
    }

    FormSheet(
        title = "💊 ${if (existing == null) "添加药物" else "编辑药物"}",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                (existing ?: MedCategory(name = "")).copy(
                    name = name,
                    timesPerDay = times.size,
                    plannedTimes = times.joinToString(","),
                    dosage = dosage,
                    active = active,
                    note = note
                )
            )
        }
    ) {
        TextFieldRow("名称 / 类别", name, { name = it }, placeholder = "如：益生菌、维生素D、退烧药")
        Text(
            "每天 ${times.size} 次",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        times.forEachIndexed { index, time ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TimeField(
                    label = "第 ${index + 1} 次时间",
                    value = time,
                    onChange = { times[index] = it },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { if (times.size > 1) times.removeAt(index) }
                ) { Text("移除") }
            }
        }
        TextButton(onClick = { times.add("12:00") }) { Text("+ 添加一次") }
        TextFieldRow("剂量说明", dosage, { dosage = it }, placeholder = "如：1 袋 / 1 粒，饭后吃")
        LabeledSwitch("正在吃（启用每日打卡）", active) { active = it }
        TextFieldRow("备注", note, { note = it }, minLines = 2)
    }
}
