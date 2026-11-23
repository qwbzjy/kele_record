package com.cola.record.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.cola.record.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val nightSleeps: List<NightSleep> = emptyList(),
    val naps: List<Nap> = emptyList(),
    val meals: List<Meal> = emptyList(),
    val poops: List<Poop> = emptyList(),
    val medCategories: List<MedCategory> = emptyList(),
    val medLogs: List<MedLog> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val baths: List<Bath> = emptyList(),
    val outings: List<Outing> = emptyList(),
    val moods: List<Mood> = emptyList(),
    val companionships: List<Companionship> = emptyList(),
    val kindergartens: List<Kindergarten> = emptyList(),
    val games: List<Game> = emptyList(),
    val medicalReports: List<MedicalReport> = emptyList(),
    val books: List<BookRecord> = emptyList(),
    val links: List<LinkItem> = emptyList()
) {
    val recordCount: Int
        get() = nightSleeps.size + naps.size + meals.size + poops.size +
            medCategories.size + medLogs.size + exercises.size + baths.size +
            outings.size + moods.size + companionships.size + kindergartens.size +
            games.size + medicalReports.size + books.size + links.size

    fun photoNames(): List<String> = buildList {
        poops.forEach { addAll(it.photos.toPhotoList()) }
        meals.forEach { addAll(it.photos.toPhotoList()) }
        games.forEach { addAll(it.photos.toPhotoList()) }
        medicalReports.forEach { addAll(it.photos.toPhotoList()) }
        books.forEach { addAll(it.photos.toPhotoList()) }
        outings.forEach { addAll(it.photos.toPhotoList()) }
    }.filter { PhotoStore.isValidName(it) }.distinct()
}

data class ImportPreview(
    val cachedFile: File,
    val isZip: Boolean,
    val data: BackupData,
    val photoCount: Int
) {
    val recordCount: Int get() = data.recordCount
}

object BackupManager {
    private const val MAX_JSON_BYTES = 20L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 20L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 20_000

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun buildData(db: AppDatabase): BackupData = withContext(Dispatchers.IO) {
        BackupData(
            exportedAt = System.currentTimeMillis(),
            nightSleeps = db.nightSleepDao().allOnce(),
            naps = db.napDao().allOnce(),
            meals = db.mealDao().allOnce(),
            poops = db.poopDao().allOnce(),
            medCategories = db.medCategoryDao().allOnce(),
            medLogs = db.medLogDao().allOnce(),
            exercises = db.exerciseDao().allOnce(),
            baths = db.bathDao().allOnce(),
            outings = db.outingDao().allOnce(),
            moods = db.moodDao().allOnce(),
            companionships = db.companionshipDao().allOnce(),
            kindergartens = db.kindergartenDao().allOnce(),
            games = db.gameDao().allOnce(),
            medicalReports = db.medicalReportDao().allOnce(),
            books = db.bookDao().allOnce(),
            links = db.linkItemDao().allOnce()
        )
    }

    fun encode(data: BackupData): ByteArray =
        json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)

    /** 导出完整备份 zip（data.json + images/），返回写入的照片数量 */
    suspend fun export(context: Context, db: AppDatabase, uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val data = buildData(db)
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("无法写入所选位置")
            var photoCount = 0
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(encode(data))
                zip.closeEntry()

                data.photoNames().forEach { name ->
                    val file = PhotoStore.file(context, name)
                    if (file.exists() && file.length() > 0) {
                        zip.putNextEntry(ZipEntry("images/$name"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        photoCount++
                    }
                }
            }
            photoCount
        }

    /** 解析备份（不修改数据库），用于预览确认 */
    suspend fun preview(context: Context, uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "import").apply { mkdirs() }
        val cached = File(cacheDir, "backup-import-${System.currentTimeMillis()}.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cached).use { out -> input.copyTo(out) }
        } ?: throw IOException("无法读取所选文件")
        previewFile(cached)
    }

    /** 直接解析本地备份文件（云端下载恢复时复用） */
    suspend fun previewFile(cached: File): ImportPreview = withContext(Dispatchers.IO) {
        if (!cached.exists() || cached.length() == 0L) {
            throw IOException("备份文件为空")
        }
        val isZip = cached.inputStream().use { stream ->
            val a = stream.read()
            val b = stream.read()
            a == 0x50 && b == 0x4B
        }

        if (isZip) {
            var photoCount = 0
            var jsonBytes: ByteArray? = null
            var entryCount = 0
            var totalBytes = 0L
            ZipFile(cached).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entryCount > MAX_ENTRIES) throw IOException("备份条目过多，已拒绝")
                    when {
                        entry.name == "data.json" -> {
                            zip.getInputStream(entry).use { input ->
                                jsonBytes = readLimited(input, MAX_JSON_BYTES)
                            }
                        }
                        entry.name.startsWith("images/") && !entry.isDirectory -> {
                            photoCount++
                            if (entry.size > 0) {
                                if (entry.size > MAX_ENTRY_BYTES) throw IOException("备份中存在过大的图片")
                                totalBytes += entry.size
                                if (totalBytes > MAX_TOTAL_BYTES) throw IOException("备份内容过大，已拒绝")
                            }
                        }
                    }
                }
            }
            val bytes = jsonBytes ?: throw IOException("备份文件缺少 data.json")
            val data = decode(bytes.toString(Charsets.UTF_8))
            ImportPreview(cached, true, data, photoCount)
        } else {
            if (cached.length() > MAX_JSON_BYTES) throw IOException("备份 JSON 过大，已拒绝")
            val data = decode(cached.readText(Charsets.UTF_8))
            ImportPreview(cached, false, data, 0)
        }
    }

    fun decode(text: String): BackupData {
        val data = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (e: Exception) {
            throw IOException("备份格式不正确：${e.message}")
        }
        if (data.version > 1) throw IOException("备份版本过新（v${data.version}），请升级 App")
        return data
    }

    /**
     * 覆盖式恢复，分两步：
     * 1. 把照片解压到 staging 目录
     * 2. 数据库在一个事务里清空+写入（中断不会留下空库）
     * 3. 数据库提交成功后再把 staging 替换为 images 目录
     */
    suspend fun apply(context: Context, db: AppDatabase, preview: ImportPreview) =
        withContext(Dispatchers.IO) {
            val staging = File(
                context.filesDir,
                "restore_images_tmp-${System.currentTimeMillis()}"
            )
            if (preview.isZip) {
                staging.deleteRecursively()
                staging.mkdirs()
                try {
                    extractImages(preview.cachedFile, staging)
                } catch (e: Exception) {
                    staging.deleteRecursively()
                    throw IOException("照片解压失败：${e.message}")
                }
            }

            try {
                db.withTransaction {
                    clearAll(db)
                    insertAll(db, preview.data)
                }
            } catch (e: Exception) {
                staging.deleteRecursively()
                throw e
            }

            if (preview.isZip) {
                replaceImagesDir(context, staging)
            }

            runCatching { preview.cachedFile.delete() }
        }

    private suspend fun clearAll(db: AppDatabase) {
        db.medLogDao().clear()
        db.medCategoryDao().clear()
        db.nightSleepDao().clear()
        db.napDao().clear()
        db.mealDao().clear()
        db.poopDao().clear()
        db.exerciseDao().clear()
        db.bathDao().clear()
        db.outingDao().clear()
        db.moodDao().clear()
        db.companionshipDao().clear()
        db.kindergartenDao().clear()
        db.gameDao().clear()
        db.medicalReportDao().clear()
        db.bookDao().clear()
        db.linkItemDao().clear()
    }

    private suspend fun insertAll(db: AppDatabase, data: BackupData) {
        db.medCategoryDao().insertAll(data.medCategories)
        db.medLogDao().insertAll(data.medLogs)
        db.nightSleepDao().insertAll(data.nightSleeps)
        db.napDao().insertAll(data.naps)
        db.mealDao().insertAll(data.meals)
        db.poopDao().insertAll(data.poops)
        db.exerciseDao().insertAll(data.exercises)
        db.bathDao().insertAll(data.baths)
        db.outingDao().insertAll(data.outings)
        db.moodDao().insertAll(data.moods)
        db.companionshipDao().insertAll(data.companionships)
        db.kindergartenDao().insertAll(data.kindergartens)
        db.gameDao().insertAll(data.games)
        db.medicalReportDao().insertAll(data.medicalReports)
        db.bookDao().insertAll(data.books)
        db.linkItemDao().insertAll(data.links)
    }

    private fun extractImages(zipFile: File, staging: File) {
        var entryCount = 0
        var totalBytes = 0L
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > MAX_ENTRIES) throw IOException("备份条目过多，已拒绝")
                if (!entry.name.startsWith("images/") || entry.isDirectory) continue
                val name = entry.name.removePrefix("images/").substringAfterLast('/')
                if (!PhotoStore.isValidName(name)) continue
                val target = File(staging, name)
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(target).use { out ->
                        val written = copyLimited(input, out, MAX_ENTRY_BYTES)
                        totalBytes += written
                        if (totalBytes > MAX_TOTAL_BYTES) throw IOException("备份内容过大，已拒绝")
                    }
                }
            }
        }
    }

    private fun replaceImagesDir(context: Context, staging: File) {
        val imagesDir = PhotoStore.dir(context)
        val oldDir = File(context.filesDir, "images_old-${System.currentTimeMillis()}")

        if (imagesDir.exists() && !imagesDir.renameTo(oldDir)) {
            PhotoStore.clearAll(context)
            staging.copyRecursively(imagesDir, overwrite = true)
            staging.deleteRecursively()
            oldDir.deleteRecursively()
            return
        }
        if (!staging.renameTo(imagesDir)) {
            imagesDir.mkdirs()
            staging.copyRecursively(imagesDir, overwrite = true)
            staging.deleteRecursively()
        }
        oldDir.deleteRecursively()
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("备份文件过大，已拒绝")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun copyLimited(input: InputStream, out: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("单张照片过大，已拒绝")
            out.write(buffer, 0, n)
        }
        return total
    }
}
