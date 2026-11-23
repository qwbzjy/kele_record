package com.cola.record.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.cola.record.ColaApp
import com.cola.record.data.sync.CloudDownload
import com.cola.record.data.sync.SyncConfig
import com.cola.record.data.sync.SyncException
import com.cola.record.data.sync.SyncManager
import com.cola.record.data.sync.SyncOutcome
import com.cola.record.data.sync.SyncSettings
import com.cola.record.data.sync.SyncStateDto
import com.cola.record.data.sync.SyncUiState
import com.cola.record.util.DateUtils
import com.cola.record.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DayStat(
    val date: String,
    val sleepHours: Float,
    val napHours: Float,
    val exerciseMinutes: Int,
    val poopCount: Int,
    val moodAvg: Float?,
    val companionMinutes: Int
)

data class PlaceStat(
    val place: String,
    val category: String,
    val visits: Int,
    val avgRating: Float?,
    val totalMinutes: Int,
    val lastDate: String
)

data class WeekStats(
    val days: List<DayStat> = emptyList(),
    val avgFallAsleepMinutes: Float? = null,
    val avgWakeCount: Float? = null,
    val avgQuality: Float? = null,
    val sleepAvgHours: Float? = null,
    val napAvgHours: Float? = null,
    val bathCount: Int = 0,
    val outingCount: Int = 0,
    val dadMinutes: Int = 0,
    val momMinutes: Int = 0,
    val companionAvgScore: Float? = null,
    val phoneFreeRate: Float? = null,
    val kindergartenAvg: Float? = null,
    val gameCount: Int = 0,
    val bookCount: Int = 0,
    val bookMinutes: Int = 0,
    val medTakenCount: Int = 0,
    val mealDays: Int = 0,
    val poopTotal: Int = 0,
    val exerciseTotal: Int = 0
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val colaApp: ColaApp get() = getApplication()
    private val db: AppDatabase get() = colaApp.db

    val syncSettings: SyncSettings get() = colaApp.syncSettings
    val syncManager: SyncManager get() = colaApp.syncManager
    val syncConfig: StateFlow<SyncConfig> get() = colaApp.syncSettings.config
    val syncUi: StateFlow<SyncUiState> get() = colaApp.syncManager.ui
    val syncServerState: StateFlow<SyncStateDto?> get() = colaApp.syncManager.serverState

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    fun setDate(date: String) { _selectedDate.value = date }
    fun shiftDate(days: Long) { _selectedDate.value = DateUtils.shift(_selectedDate.value, days) }
    fun goToday() { _selectedDate.value = DateUtils.today() }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> dateFlow(query: (String) -> Flow<List<T>>): StateFlow<List<T>> =
        _selectedDate.flatMapLatest { query(it) }.state(emptyList())

    val nights = dateFlow { db.nightSleepDao().byDate(it) }
    val naps = dateFlow { db.napDao().byDate(it) }
    val meals = dateFlow { db.mealDao().byDate(it) }
    val poops = dateFlow { db.poopDao().byDate(it) }
    val exercises = dateFlow { db.exerciseDao().byDate(it) }
    val baths = dateFlow { db.bathDao().byDate(it) }
    val outings = dateFlow { db.outingDao().byDate(it) }
    val moods = dateFlow { db.moodDao().byDate(it) }
    val companionships = dateFlow { db.companionshipDao().byDate(it) }
    val kindergartens = dateFlow { db.kindergartenDao().byDate(it) }
    val games = dateFlow { db.gameDao().byDate(it) }
    val books = dateFlow { db.bookDao().byDate(it) }
    val medLogs = dateFlow { db.medLogDao().byDate(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val weekBaths: StateFlow<List<Bath>> = _selectedDate.flatMapLatest { d ->
        val (from, to) = DateUtils.weekRange(d)
        db.bathDao().between(from, to)
    }.state(emptyList())

    val medCategories: StateFlow<List<MedCategory>> = db.medCategoryDao().all().state(emptyList())
    val medicalReports: StateFlow<List<MedicalReport>> = db.medicalReportDao().all().state(emptyList())
    val linkItems: StateFlow<List<LinkItem>> = db.linkItemDao().all().state(emptyList())

    val allOutings: StateFlow<List<Outing>> = db.outingDao().all().state(emptyList())

    /** 地点喜爱度汇总：平均分优先，其次去过的次数 */
    val placeStats: StateFlow<List<PlaceStat>> = allOutings.map { list ->
        list.groupBy { it.place.trim().ifBlank { "未命名地点" } }
            .map { (place, items) ->
                val rated = items.map { it.rating }.filter { it > 0 }
                val category = items.map { it.category }.filter { it.isNotBlank() }
                    .groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }?.key ?: ""
                PlaceStat(
                    place = place,
                    category = category,
                    visits = items.size,
                    avgRating = if (rated.isEmpty()) null else rated.average().toFloat(),
                    totalMinutes = items.sumOf { it.minutes },
                    lastDate = items.maxOf { it.date }
                )
            }
            .sortedWith(
                compareByDescending<PlaceStat> { it.avgRating ?: 0f }
                    .thenByDescending { it.visits }
            )
    }.state(emptyList())

    fun notifyReadOnly() {
        message.value = "本机为只读设备，请在主设备上录入"
    }

    private fun write(block: suspend () -> Unit) {
        if (!colaApp.syncSettings.snapshot().isPrimary) {
            notifyReadOnly()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
                colaApp.syncManager.onLocalChange()
            } catch (e: Exception) {
                message.value = "保存失败：${e.message ?: "未知错误"}"
            }
        }
    }

    // ---------- 夜间睡眠 ----------
    fun saveNight(e: NightSleep) = write {
        if (e.id == 0L) db.nightSleepDao().insert(e) else db.nightSleepDao().update(e)
    }

    fun deleteNight(e: NightSleep) = write { db.nightSleepDao().delete(e) }

    // ---------- 午睡 ----------
    fun saveNap(e: Nap) = write {
        if (e.id == 0L) db.napDao().insert(e) else db.napDao().update(e)
    }

    fun deleteNap(e: Nap) = write { db.napDao().delete(e) }

    // ---------- 三餐 / 点心 ----------
    fun saveMeal(e: Meal) = write {
        if (e.id == 0L) db.mealDao().insert(e) else db.mealDao().update(e)
    }

    fun deleteMeal(e: Meal) = write { db.mealDao().delete(e) }

    // ---------- 排便 ----------
    fun savePoop(e: Poop) = write {
        if (e.id == 0L) db.poopDao().insert(e) else db.poopDao().update(e)
    }

    fun deletePoop(e: Poop) = write { db.poopDao().delete(e) }

    // ---------- 运动 ----------
    fun saveExercise(e: Exercise) = write {
        if (e.id == 0L) db.exerciseDao().insert(e) else db.exerciseDao().update(e)
    }

    fun deleteExercise(e: Exercise) = write { db.exerciseDao().delete(e) }

    // ---------- 洗澡 ----------
    fun saveBath(e: Bath) = write {
        if (e.id == 0L) db.bathDao().insert(e) else db.bathDao().update(e)
    }

    fun deleteBath(e: Bath) = write { db.bathDao().delete(e) }

    // ---------- 外出活动 ----------
    fun saveOuting(e: Outing) = write {
        if (e.id == 0L) db.outingDao().insert(e) else db.outingDao().update(e)
    }

    fun deleteOuting(e: Outing) = write { db.outingDao().delete(e) }

    // ---------- 情绪 ----------
    fun saveMood(e: Mood) = write {
        if (e.id == 0L) db.moodDao().insert(e) else db.moodDao().update(e)
    }

    fun deleteMood(e: Mood) = write { db.moodDao().delete(e) }

    fun setMoodScore(date: String, period: String, score: Int, existing: Mood?) = write {
        if (existing == null) {
            db.moodDao().insert(Mood(date = date, period = period, score = score))
        } else {
            val newScore = if (existing.score == score) 0 else score
            db.moodDao().update(existing.copy(score = newScore))
        }
    }

    // ---------- 父母陪伴 ----------
    fun saveCompanionship(e: Companionship) = write {
        if (e.id == 0L) db.companionshipDao().insert(e) else db.companionshipDao().update(e)
    }

    fun deleteCompanionship(e: Companionship) = write {
        db.companionshipDao().delete(e)
    }

    // ---------- 幼儿园 ----------
    fun saveKindergarten(e: Kindergarten) = write {
        if (e.id == 0L) db.kindergartenDao().insert(e) else db.kindergartenDao().update(e)
    }

    fun deleteKindergarten(e: Kindergarten) = write {
        db.kindergartenDao().delete(e)
    }

    // ---------- 游戏 ----------
    fun saveGame(e: Game) = write {
        if (e.id == 0L) db.gameDao().insert(e) else db.gameDao().update(e)
    }

    fun deleteGame(e: Game) = write { db.gameDao().delete(e) }

    // ---------- 绘本 ----------
    fun saveBook(e: BookRecord) = write {
        if (e.id == 0L) db.bookDao().insert(e) else db.bookDao().update(e)
    }

    fun deleteBook(e: BookRecord) = write { db.bookDao().delete(e) }

    // ---------- 医生报告 ----------
    fun saveReport(e: MedicalReport) = write {
        if (e.id == 0L) db.medicalReportDao().insert(e) else db.medicalReportDao().update(e)
    }

    fun deleteReport(e: MedicalReport) = write {
        db.medicalReportDao().delete(e)
    }

    // ---------- 药物 ----------
    fun saveCategory(e: MedCategory) = write {
        if (e.id == 0L) db.medCategoryDao().insert(e) else db.medCategoryDao().update(e)
    }

    fun deleteCategory(e: MedCategory) = write {
        db.medLogDao().deleteByCategory(e.id)
        db.medCategoryDao().delete(e)
    }

    fun toggleMed(categoryId: Long, date: String, slot: String, taken: Boolean) = write {
        val existing = db.medLogDao().find(categoryId, date, slot)
        if (taken && existing == null) {
            db.medLogDao().insert(
                MedLog(
                    categoryId = categoryId,
                    date = date,
                    slot = slot,
                    takenAt = DateUtils.nowTime()
                )
            )
        } else if (!taken && existing != null) {
            db.medLogDao().delete(existing)
        }
    }

    // ---------- 资料库 ----------
    fun saveLink(e: LinkItem) = write {
        if (e.id == 0L) db.linkItemDao().insert(e.copy(createdAt = System.currentTimeMillis()))
        else db.linkItemDao().update(e)
    }

    fun deleteLink(e: LinkItem) = write { db.linkItemDao().delete(e) }

    // ---------- 备份 ----------
    val pendingImport = MutableStateFlow<ImportPreview?>(null)
    val pendingCloud = MutableStateFlow<CloudDownload?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        busy.value = true
        try {
            val photos = BackupManager.export(getApplication(), db, uri)
            message.value = "备份已导出，包含 $photos 张照片"
        } catch (e: Exception) {
            message.value = "导出失败：${e.message ?: "未知错误"}"
        } finally {
            busy.value = false
        }
    }

    fun prepareImport(uri: Uri) = viewModelScope.launch {
        if (!colaApp.syncSettings.snapshot().isPrimary) {
            notifyReadOnly()
            return@launch
        }
        busy.value = true
        try {
            pendingImport.value?.cachedFile?.let { runCatching { it.delete() } }
            pendingCloud.value = null
            pendingImport.value = BackupManager.preview(getApplication(), uri)
        } catch (e: Exception) {
            message.value = "读取备份失败：${e.message ?: "未知错误"}"
        } finally {
            busy.value = false
        }
    }

    fun confirmImport() {
        val preview = pendingImport.value ?: return
        val cloud = pendingCloud.value
        if (!colaApp.syncSettings.snapshot().isPrimary && cloud == null) {
            notifyReadOnly()
            cancelImport()
            return
        }
        pendingImport.value = null
        pendingCloud.value = null
        viewModelScope.launch {
            busy.value = true
            try {
                if (cloud != null) {
                    colaApp.syncManager.cancelScheduled()
                }
                BackupManager.apply(getApplication(), db, preview)
                if (cloud != null) {
                    colaApp.syncManager.onRestored(cloud.revision)
                    message.value = "已从云端恢复，共 ${preview.recordCount} 条记录"
                } else {
                    colaApp.syncManager.onLocalChange()
                    message.value = "已恢复备份，共 ${preview.recordCount} 条记录"
                }
                runCatching { Coil.imageLoader(getApplication()).memoryCache?.clear() }
            } catch (e: Exception) {
                message.value = "导入失败：${e.message ?: "未知错误"}"
            } finally {
                busy.value = false
            }
        }
    }

    fun cancelImport() {
        pendingImport.value?.cachedFile?.let { runCatching { it.delete() } }
        pendingImport.value = null
        pendingCloud.value = null
    }

    fun clearMessage() { message.value = null }

    fun clearAllData() = viewModelScope.launch {
        if (!colaApp.syncSettings.snapshot().isPrimary) {
            notifyReadOnly()
            return@launch
        }
        busy.value = true
        try {
            colaApp.syncManager.cancelScheduled()
            colaApp.syncSettings.clearDirty()
            withContext(Dispatchers.IO) {
                db.clearAllTables()
                PhotoStore.clearAll(getApplication())
            }
            pendingImport.value?.cachedFile?.let { runCatching { it.delete() } }
            pendingImport.value = null
            pendingCloud.value = null
            message.value = "已清空全部记录和照片"
        } catch (e: Exception) {
            message.value = "清空失败：${e.message ?: "未知错误"}"
        } finally {
            busy.value = false
        }
    }

    // ---------- 云同步 ----------
    fun saveSyncSettings(
        enabled: Boolean,
        baseUrl: String,
        token: String,
        deviceName: String,
        wifiOnly: Boolean,
        isPrimary: Boolean
    ) {
        colaApp.syncSettings.updateConfig(
            enabled, baseUrl, token, deviceName, wifiOnly, isPrimary
        )
        val cfg = colaApp.syncSettings.snapshot()
        if (!cfg.isPrimary) {
            colaApp.syncManager.cancelScheduled()
            message.value = "已保存：本机为只读设备"
            return
        }
        if (cfg.enabled && cfg.configured) {
            // 约束条件（仅 Wi-Fi）可能变化，需替换旧任务，否则旧约束会一直挂起
            colaApp.syncManager.ensureScheduled(immediate = true, replace = true)
            message.value = "同步设置已保存"
        } else {
            colaApp.syncManager.cancelScheduled()
            message.value = if (enabled) "已保存，但还需要填写服务器地址和 Token" else "同步设置已保存"
        }
    }

    fun testSyncConnection() = viewModelScope.launch {
        busy.value = true
        try {
            val state = colaApp.syncManager.testConnection()
            message.value = "连接成功：云端 revision ${state.revision}，来源 " +
                state.deviceName.ifBlank { "暂无数据" }
        } catch (e: SyncException) {
            message.value = e.message ?: "连接失败"
        } catch (e: Exception) {
            message.value = "连接失败：${e.message ?: "未知错误"}"
        } finally {
            busy.value = false
        }
    }

    fun uploadNow(force: Boolean = false) = viewModelScope.launch {
        busy.value = true
        try {
            when (val outcome = colaApp.syncManager.uploadNow(force)) {
                is SyncOutcome.Success ->
                    message.value = "已上传到云端：revision ${outcome.revision}，新传照片 ${outcome.photos} 张"
                is SyncOutcome.Conflict ->
                    message.value = "云端已有来自「${outcome.deviceName}」的数据，请选择恢复或覆盖"
                is SyncOutcome.Retry -> message.value = outcome.message
                is SyncOutcome.Failure -> message.value = outcome.message
                SyncOutcome.Skipped -> Unit
            }
        } finally {
            busy.value = false
        }
    }

    fun downloadCloudBackup() = viewModelScope.launch {
        busy.value = true
        try {
            pendingImport.value?.cachedFile?.let { runCatching { it.delete() } }
            val download = colaApp.syncManager.downloadAndPreview()
            pendingCloud.value = download
            pendingImport.value = download.preview
        } catch (e: SyncException) {
            message.value = "下载失败：${e.message ?: "未知错误"}"
        } catch (e: Exception) {
            message.value = "下载失败：${e.message ?: "未知错误"}"
        } finally {
            busy.value = false
        }
    }

    // ---------- 周统计 ----------
    suspend fun loadWeekStats(end: String, days: Int = 7): WeekStats =
        withContext(Dispatchers.IO) {
            val range = DateUtils.lastNDays(end, days)
            val from = range.first()
            val to = range.last()

            val nights = db.nightSleepDao().allOnce().filter { it.date in from..to }
            val naps = db.napDao().allOnce().filter { it.date in from..to }
            val exercises = db.exerciseDao().allOnce().filter { it.date in from..to }
            val poops = db.poopDao().allOnce().filter { it.date in from..to }
            val baths = db.bathDao().allOnce().filter { it.date in from..to }
            val outings = db.outingDao().allOnce().filter { it.date in from..to }
            val moods = db.moodDao().allOnce().filter { it.date in from..to }
            val companions = db.companionshipDao().allOnce().filter { it.date in from..to }
            val kindergartens = db.kindergartenDao().allOnce().filter { it.date in from..to }
            val games = db.gameDao().allOnce().filter { it.date in from..to }
            val books = db.bookDao().allOnce().filter { it.date in from..to }
            val meals = db.mealDao().allOnce().filter { it.date in from..to }
            val medLogs = db.medLogDao().allOnce().filter { it.date in from..to }

            val dayStats = range.map { d ->
                val dayNights = nights.filter { it.date == d }
                val dayNaps = naps.filter { it.date == d }
                val sleepMin = dayNights.sumOf {
                    DateUtils.durationMinutes(
                        it.sleepTime.ifBlank { it.bedtime },
                        it.morningWakeTime
                    ) ?: 0
                }
                val napMin = dayNaps.sumOf {
                    DateUtils.durationMinutes(it.startTime, it.endTime) ?: 0
                }
                val dayMoods = moods.filter { it.date == d && it.score > 0 }
                DayStat(
                    date = d,
                    sleepHours = sleepMin / 60f,
                    napHours = napMin / 60f,
                    exerciseMinutes = exercises.filter { it.date == d }.sumOf { it.minutes },
                    poopCount = poops.count { it.date == d },
                    moodAvg = if (dayMoods.isEmpty()) null else dayMoods.map { it.score }.average().toFloat(),
                    companionMinutes = companions.filter { it.date == d }.sumOf { it.minutes }
                )
            }

            val fallAsleepValues = nights.mapNotNull { it.fallAsleepMinutes }
            val wakeValues = nights.map { it.wakeCount }
            val qualityValues = nights.map { it.quality }.filter { it > 0 }

            val dadMinutes = companions.filter { it.who.contains("爸爸") || it.who.contains("双方") }
                .sumOf { it.minutes / if (it.who.contains("双方")) 2 else 1 }
            val momMinutes = companions.filter { it.who.contains("妈妈") || it.who.contains("双方") }
                .sumOf { it.minutes / if (it.who.contains("双方")) 2 else 1 }
            val selfScores = companions.map { it.selfScore }.filter { it > 0 }
            val phoneFreeCount = companions.count { it.phoneFree }

            val sleepDays = dayStats.filter { it.sleepHours > 0f }
            val napDays = dayStats.filter { it.napHours > 0f }
            val kindergartenScores = kindergartens.map { it.moodScore }.filter { it > 0 }

            WeekStats(
                days = dayStats,
                avgFallAsleepMinutes = if (fallAsleepValues.isEmpty()) null else fallAsleepValues.average().toFloat(),
                avgWakeCount = if (wakeValues.isEmpty()) null else wakeValues.average().toFloat(),
                avgQuality = if (qualityValues.isEmpty()) null else qualityValues.average().toFloat(),
                sleepAvgHours = if (sleepDays.isEmpty()) null else sleepDays.map { it.sleepHours }.average().toFloat(),
                napAvgHours = if (napDays.isEmpty()) null else napDays.map { it.napHours }.average().toFloat(),
                bathCount = baths.size,
                outingCount = outings.size,
                dadMinutes = dadMinutes,
                momMinutes = momMinutes,
                companionAvgScore = if (selfScores.isEmpty()) null else selfScores.average().toFloat(),
                phoneFreeRate = if (companions.isEmpty()) null else phoneFreeCount.toFloat() / companions.size,
                kindergartenAvg = if (kindergartenScores.isEmpty()) null else kindergartenScores.average().toFloat(),
                gameCount = games.size,
                bookCount = books.size,
                bookMinutes = books.sumOf { it.minutes },
                medTakenCount = medLogs.size,
                mealDays = meals.map { it.date }.distinct().size,
                poopTotal = poops.size,
                exerciseTotal = exercises.sumOf { it.minutes }
            )
        }
}
