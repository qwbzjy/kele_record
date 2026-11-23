package com.cola.record.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cola.record.data.AppDatabase
import com.cola.record.data.BackupManager
import com.cola.record.data.ImportPreview
import com.cola.record.util.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data class Uploading(val phase: String, val done: Int = 0, val total: Int = 0) : SyncUiState
    data class Downloading(val done: Long, val total: Long) : SyncUiState
    data class Success(val at: Long, val revision: Int, val photos: Int) : SyncUiState
    data class Conflict(val deviceName: String, val revision: Int) : SyncUiState
    data class Error(val message: String, val auth: Boolean = false) : SyncUiState
}

sealed interface SyncOutcome {
    data class Success(val revision: Int, val photos: Int) : SyncOutcome
    data object Skipped : SyncOutcome
    data class Conflict(
        val deviceName: String,
        val revision: Int,
        val state: SyncStateDto
    ) : SyncOutcome

    data class Retry(val message: String) : SyncOutcome
    data class Failure(val message: String, val auth: Boolean = false) : SyncOutcome
}

data class CloudDownload(
    val preview: ImportPreview,
    val revision: Int,
    val deviceName: String,
    val receivedAt: Long,
    val imagesMissing: Int
)

class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SyncSettings,
    private val api: SyncApi = SyncApi()
) {
    private val mutex = Mutex()

    private val _ui = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val ui: StateFlow<SyncUiState> = _ui.asStateFlow()

    private val _serverState = MutableStateFlow<SyncStateDto?>(null)
    val serverState: StateFlow<SyncStateDto?> = _serverState.asStateFlow()

    /** 每次本地写入后调用：记录脏标记；开启同步时 30 秒防抖后自动上传 */
    fun onLocalChange() {
        val cfg = settings.snapshot()
        if (!cfg.isPrimary) return
        settings.markDirty()
        if (cfg.enabled && cfg.configured && !cfg.conflict && !cfg.authError) {
            SyncScheduler.enqueue(context, cfg.wifiOnly, 30)
        }
    }

    fun ensureScheduled(immediate: Boolean = false, replace: Boolean = false) {
        val cfg = settings.snapshot()
        if (cfg.isPrimary && cfg.enabled && cfg.configured && cfg.dirty &&
            !cfg.conflict && !cfg.authError
        ) {
            SyncScheduler.enqueue(
                context,
                cfg.wifiOnly,
                if (immediate) 0 else 30,
                replace = replace
            )
        }
    }

    fun cancelScheduled() {
        SyncScheduler.cancel(context)
    }

    fun onRestored(revision: Int) {
        SyncScheduler.cancel(context)
        settings.markRestored(revision)
        _ui.value = SyncUiState.Idle
    }

    suspend fun testConnection(): SyncStateDto = mutex.withLock {
        val cfg = settings.snapshot()
        try {
            val state = api.getState(cfg)
            _serverState.value = state
            settings.clearAuthError()
            if (state.primaryDeviceId.isNotBlank() && state.primaryDeviceId != cfg.deviceId) {
                settings.setPrimary(false)
                SyncScheduler.cancel(context)
            }
            state
        } catch (e: SyncException) {
            settings.markError(e.message ?: "连接失败", e.kind == SyncException.Kind.UNAUTHORIZED)
            throw e
        }
    }

    suspend fun autoUpload(): SyncOutcome = mutex.withLock {
        upload(force = false, auto = true)
    }

    suspend fun uploadNow(force: Boolean = false): SyncOutcome = mutex.withLock {
        upload(force = force, auto = false)
    }

    suspend fun downloadAndPreview(): CloudDownload = mutex.withLock {
        val cfg = settings.snapshot()
        if (!cfg.configured) {
            throw SyncException(SyncException.Kind.CONFIG, "请先填写服务器地址和 Token")
        }
        if (!isNetworkAvailable()) {
            throw SyncException(SyncException.Kind.NETWORK, "网络不可用")
        }

        _ui.value = SyncUiState.Downloading(0, 0)
        var target: File? = null
        try {
            val state = api.getState(cfg)
            _serverState.value = state
            if (state.revision == 0) {
                throw SyncException(SyncException.Kind.NOT_FOUND, "云端还没有备份数据")
            }
            val file = File(
                context.cacheDir,
                "sync/cloud-r${state.revision}-${System.currentTimeMillis()}.zip"
            )
            target = file
            val result = api.downloadBackup(cfg, file) { done, total ->
                _ui.value = SyncUiState.Downloading(done, total)
            }
            val preview = BackupManager.previewFile(result.file)
            _ui.value = SyncUiState.Idle
            CloudDownload(
                preview = preview,
                revision = if (result.revision > 0) result.revision else state.revision,
                deviceName = state.deviceName.ifBlank { "其他设备" },
                receivedAt = state.receivedAt,
                imagesMissing = result.imagesMissing
            )
        } catch (e: SyncException) {
            target?.delete()
            _ui.value = SyncUiState.Error(e.message ?: "下载失败", e.kind == SyncException.Kind.UNAUTHORIZED)
            throw e
        } catch (e: Exception) {
            target?.delete()
            _ui.value = SyncUiState.Error(e.message ?: "下载失败")
            throw e
        }
    }

    private suspend fun upload(force: Boolean, auto: Boolean): SyncOutcome {
        val cfg = settings.snapshot()
        if (!cfg.configured) {
            return if (auto) SyncOutcome.Skipped
            else SyncOutcome.Failure("请先填写服务器地址和 Token")
        }
        if (!cfg.isPrimary) {
            return SyncOutcome.Failure("本机为只读设备，仅支持查看和从云端同步")
        }
        if (auto && !cfg.enabled) return SyncOutcome.Skipped
        if (auto && cfg.authError) {
            return SyncOutcome.Failure(cfg.lastError ?: "Token 不正确，自动上传已暂停", auth = true)
        }
        if (auto && cfg.conflict) return SyncOutcome.Skipped
        if (auto && cfg.wifiOnly && !isUnmeteredNetwork()) {
            _ui.value = SyncUiState.Error("等待 Wi-Fi")
            return SyncOutcome.Retry("等待 Wi-Fi")
        }
        if (!isNetworkAvailable()) {
            _ui.value = SyncUiState.Error("网络不可用，等待重试")
            return SyncOutcome.Retry("网络不可用")
        }

        val startGeneration = cfg.generation
        val data = try {
            withContext(Dispatchers.IO) { BackupManager.buildData(db) }
        } catch (e: Exception) {
            settings.markError(e.message ?: "读取本地数据失败")
            _ui.value = SyncUiState.Error(e.message ?: "读取本地数据失败")
            return SyncOutcome.Failure(e.message ?: "读取本地数据失败")
        }

        if (data.recordCount == 0 && !force) {
            return if (auto) SyncOutcome.Skipped
            else SyncOutcome.Failure("本机没有可上传的记录")
        }
        if (!force && cfg.conflict) {
            return SyncOutcome.Failure("云端数据冲突，请先选择「从云端恢复」或「以本机覆盖云端」")
        }

        var snapshotFile: File? = null
        return try {
            _ui.value = SyncUiState.Uploading("检查云端…")
            val state = api.getState(cfg)
            _serverState.value = state

            if (!force) {
                val hasServerData = state.revision > 0
                val base = cfg.lastRevision
                if (hasServerData && (base == null || state.revision != base)) {
                    settings.markConflict(
                        state.deviceName.ifBlank { "其他设备" },
                        state.revision
                    )
                    _ui.value = SyncUiState.Conflict(
                        state.deviceName.ifBlank { "其他设备" },
                        state.revision
                    )
                    return SyncOutcome.Conflict(
                        state.deviceName.ifBlank { "其他设备" },
                        state.revision,
                        state
                    )
                }
            }

            val pendingPhotos = withContext(Dispatchers.IO) {
                data.photoNames()
                    .map { PhotoStore.file(context, it) }
                    .filter { it.exists() && it.length() > 0 }
            }
            val serverImages = state.images.toSet()
            val missing = pendingPhotos.filter { it.name !in serverImages }
            var uploaded = 0
            if (missing.isNotEmpty()) {
                missing.chunked(10).forEach { batch ->
                    _ui.value = SyncUiState.Uploading(
                        "上传照片 $uploaded/${missing.size}",
                        uploaded,
                        missing.size
                    )
                    val result = api.uploadImages(cfg, batch)
                    uploaded += result.accepted.size + result.skipped.size
                }
            }

            val snapshot = File(
                context.cacheDir,
                "sync/data-${System.currentTimeMillis()}.json"
            )
            snapshot.parentFile?.mkdirs()
            snapshot.writeBytes(BackupManager.encode(data))
            snapshotFile = snapshot

            _ui.value = SyncUiState.Uploading("上传记录…")
            val baseRevision = when {
                force -> state.revision
                state.revision == 0 -> 0
                else -> cfg.lastRevision ?: state.revision
            }
            val result = api.uploadData(cfg, snapshot, baseRevision)
            _serverState.value = result
            val stillDirty = settings.markSynced(result.revision, startGeneration)
            _ui.value = SyncUiState.Success(System.currentTimeMillis(), result.revision, uploaded)

            if (stillDirty) {
                if (auto) {
                    SyncScheduler.enqueue(context, cfg.wifiOnly, 5)
                } else {
                    _ui.value = SyncUiState.Error("已上传，但期间又有新记录，稍后会继续同步")
                }
            }
            SyncOutcome.Success(result.revision, uploaded)
        } catch (e: SyncException) {
            when (e.kind) {
                SyncException.Kind.NOT_PRIMARY -> {
                    settings.setPrimary(false)
                    SyncScheduler.cancel(context)
                    val name = e.primaryDeviceName.ifBlank { "另一台手机" }
                    val msg = "云端主设备是「$name」，本机已切换为只读"
                    settings.markError(msg)
                    _ui.value = SyncUiState.Error(msg)
                    SyncOutcome.Failure(msg)
                }
                SyncException.Kind.UNAUTHORIZED -> {
                    settings.markError(e.message ?: "Token 不正确", authError = true)
                    _ui.value = SyncUiState.Error(e.message ?: "Token 不正确", auth = true)
                    SyncOutcome.Failure(e.message ?: "Token 不正确", auth = true)
                }
                SyncException.Kind.CONFLICT -> {
                    val server = e.serverState
                    if (server != null) {
                        val device = server.deviceName.ifBlank { "其他设备" }
                        settings.markConflict(device, server.revision)
                        _ui.value = SyncUiState.Conflict(device, server.revision)
                        SyncOutcome.Conflict(device, server.revision, server)
                    } else {
                        settings.markError(e.message ?: "云端数据冲突")
                        _ui.value = SyncUiState.Error(e.message ?: "云端数据冲突")
                        SyncOutcome.Failure(e.message ?: "云端数据冲突")
                    }
                }
                SyncException.Kind.NETWORK -> {
                    settings.markError(e.message ?: "网络错误")
                    _ui.value = SyncUiState.Error(e.message ?: "网络错误")
                    SyncOutcome.Retry(e.message ?: "网络错误")
                }
                SyncException.Kind.HTTP -> {
                    settings.markError(e.message ?: "服务器错误")
                    _ui.value = SyncUiState.Error(e.message ?: "服务器错误")
                    if (e.httpCode >= 500) {
                        SyncOutcome.Retry(e.message ?: "服务器错误")
                    } else {
                        SyncOutcome.Failure(e.message ?: "服务器错误")
                    }
                }
                else -> {
                    settings.markError(e.message ?: "同步失败")
                    _ui.value = SyncUiState.Error(e.message ?: "同步失败")
                    SyncOutcome.Failure(e.message ?: "同步失败")
                }
            }
        } catch (e: Exception) {
            settings.markError(e.message ?: "同步失败")
            _ui.value = SyncUiState.Error(e.message ?: "同步失败")
            SyncOutcome.Failure(e.message ?: "同步失败")
        } finally {
            snapshotFile?.delete()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
