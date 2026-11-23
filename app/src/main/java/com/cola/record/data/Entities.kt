package com.cola.record.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 夜间睡眠 */
@Serializable
@Entity(tableName = "night_sleep")
data class NightSleep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                   // 归属日期 yyyy-MM-dd（当晚）
    val bedtime: String = "",           // 上床时间 HH:mm
    val sleepTime: String = "",         // 实际入睡时间 HH:mm
    val fallAsleepMinutes: Int? = null, // 入睡时长（分钟）
    val wakeTimes: String = "",         // 夜醒时间点，逗号分隔
    val wakeCount: Int = 0,             // 夜醒次数
    val wakeReason: String = "",        // 夜醒原因 / 备注
    val morningWakeTime: String = "",   // 早上醒来时间 HH:mm
    val quality: Int = 0,               // 0未评 1差 2一般 3好
    val note: String = ""
)

/** 午睡 */
@Serializable
@Entity(tableName = "nap")
data class Nap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startTime: String = "",
    val endTime: String = "",
    val fallAsleepMinutes: Int? = null,
    val quality: Int = 0,
    val note: String = ""
)

/** 三餐 + 两次点心 */
@Serializable
@Entity(tableName = "meal")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,                   // breakfast/lunch/dinner/snack1/snack2
    val time: String = "",
    val food: String = "",
    val amount: Int = 0,                // 0未评 1很少 2一般 3不错 4光盘
    val photos: String = "",            // 图片文件名，逗号分隔
    val note: String = ""
)

/** 拉臭臭 */
@Serializable
@Entity(tableName = "poop")
data class Poop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String = "",
    val shape: String = "",             // 干硬/成形软便/稀糊/水样
    val color: String = "",
    val amount: Int = 0,                // 0未评 1少 2中 3多
    val photos: String = "",
    val note: String = ""
)

/** 正在吃的药物 / 营养品类别 */
@Serializable
@Entity(tableName = "med_category")
data class MedCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timesPerDay: Int = 1,
    val plannedTimes: String = "",      // "08:00,20:00"
    val dosage: String = "",
    val active: Boolean = true,
    val note: String = ""
)

/** 服药打卡 */
@Serializable
@Entity(tableName = "med_log")
data class MedLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val date: String,
    val slot: String = "",              // 计划时间点
    val takenAt: String = "",           // 实际打卡时间
    val note: String = ""
)

/** 运动 */
@Serializable
@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String = "",
    val minutes: Int = 0,
    val intensity: Int = 0,             // 0未评 1轻 2中 3大
    val time: String = "",
    val note: String = ""
)

/** 洗澡 */
@Serializable
@Entity(tableName = "bath")
data class Bath(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String = "",
    val durationMinutes: Int = 0,
    val washedHair: Boolean = false,
    val note: String = ""
)

/** 外出活动地点 */
@Serializable
@Entity(tableName = "outing")
data class Outing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val place: String = "",
    val category: String = "",          // 公园/商场/游乐场/水族馆/图书馆...
    val rating: Int = 0,                // 1-5
    val minutes: Int = 0,
    val time: String = "",
    val note: String = "",
    val photos: String = ""
)

/** 情绪（早/中/晚） */
@Serializable
@Entity(tableName = "mood")
data class Mood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val period: String,                 // morning/noon/evening
    val score: Int = 0,                 // 1哭闹 2低落 3平静 4开心 5超开心
    val note: String = ""
)

/** 父母陪伴 */
@Serializable
@Entity(tableName = "companionship")
data class Companionship(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val who: String = "",               // 爸爸/妈妈/双方
    val minutes: Int = 0,
    val activity: String = "",
    val phoneFree: Boolean = true,
    val selfScore: Int = 0,             // 1-5
    val note: String = ""
)

/** 幼儿园上午表现 */
@Serializable
@Entity(tableName = "kindergarten")
data class Kindergarten(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val moodScore: Int = 0,
    val ateWell: Boolean = false,
    val napWell: Boolean = false,
    val participated: Boolean = false,
    val teacherFeedback: String = "",
    val note: String = ""
)

/** 每天设计的游戏 */
@Serializable
@Entity(tableName = "game")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String = "",
    val goal: String = "",
    val description: String = "",
    val played: Boolean = false,
    val babyReaction: Int = 0,
    val photos: String = "",
    val note: String = ""
)

/** 医生报告资料 */
@Serializable
@Entity(tableName = "medical_report")
data class MedicalReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val hospital: String = "",
    val doctor: String = "",
    val title: String = "",
    val content: String = "",
    val photos: String = "",
    val note: String = ""
)

/** 绘本讲解 */
@Serializable
@Entity(tableName = "book")
data class BookRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val title: String = "",
    val minutes: Int = 0,
    val timesRead: Int = 1,
    val babyReaction: Int = 0,
    val photos: String = "",
    val note: String = ""
)

/** 资料库：收藏的链接（小红书 / 抖音 / 微信视频等） */
@Serializable
@Entity(tableName = "link_item")
data class LinkItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val platform: String = "其他",
    val tags: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** 照片列表辅助函数 */
fun String.toPhotoList(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun List<String>.toPhotoString(): String =
    filter { it.isNotBlank() }.joinToString(",")
