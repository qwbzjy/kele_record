package com.cola.record.data.sync

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SyncConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val token: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val wifiOnly: Boolean = true,
    val isPrimary: Boolean = true,
    val generation: Int = 0,
    val lastRevision: Int? = null,
    val lastSuccessAt: Long = 0L,
    val lastError: String? = null,
    val authError: Boolean = false,
    val conflict: Boolean = false,
    val conflictDevice: String = "",
    val conflictRevision: Int = 0
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
    val dirty: Boolean get() = generation > 0
}

class SyncSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("cola_sync", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<SyncConfig> = _config.asStateFlow()

    fun snapshot(): SyncConfig = _config.value

    private fun load(): SyncConfig {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        val lastRevision = prefs.getInt(KEY_LAST_REVISION, -1).let { if (it >= 0) it else null }
        return SyncConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
            token = prefs.getString(KEY_TOKEN, "") ?: "",
            deviceId = deviceId,
            deviceName = prefs.getString(KEY_DEVICE_NAME, null)
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL,
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
            isPrimary = prefs.getBoolean(KEY_IS_PRIMARY, true),
            generation = prefs.getInt(KEY_GENERATION, 0),
            lastRevision = lastRevision,
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0L),
            lastError = prefs.getString(KEY_LAST_ERROR, null),
            authError = prefs.getBoolean(KEY_AUTH_ERROR, false),
            conflict = prefs.getBoolean(KEY_CONFLICT, false),
            conflictDevice = prefs.getString(KEY_CONFLICT_DEVICE, "") ?: "",
            conflictRevision = prefs.getInt(KEY_CONFLICT_REVISION, 0)
        )
    }

    @Synchronized
    fun updateConfig(
        enabled: Boolean,
        baseUrl: String,
        token: String,
        deviceName: String,
        wifiOnly: Boolean,
        isPrimary: Boolean
    ) {
        val old = _config.value
        val newUrl = normalizeBaseUrl(baseUrl)
        val serverChanged = newUrl != old.baseUrl
        val newName = deviceName.trim().ifBlank { Build.MODEL }
        val newToken = token.trim()
        val updated = old.copy(
            enabled = enabled,
            baseUrl = newUrl,
            token = newToken,
            deviceName = newName,
            wifiOnly = wifiOnly,
            isPrimary = isPrimary,
            lastRevision = if (serverChanged) null else old.lastRevision,
            conflict = if (serverChanged) false else old.conflict,
            conflictDevice = if (serverChanged) "" else old.conflictDevice,
            conflictRevision = if (serverChanged) 0 else old.conflictRevision,
            authError = if (newToken != old.token || serverChanged) false else old.authError,
            lastError = null
        )
        emit(updated)
    }

    @Synchronized
    fun setPrimary(isPrimary: Boolean) {
        emit(_config.value.copy(isPrimary = isPrimary))
    }

    @Synchronized
    fun markDirty(): Int {
        val newGen = _config.value.generation + 1
        emit(_config.value.copy(generation = newGen, lastError = null))
        return newGen
    }

    /** 上传成功：仅当期间没有新改动时才清除 dirty；返回是否还有未上传改动 */
    @Synchronized
    fun markSynced(revision: Int, startGeneration: Int): Boolean {
        val cur = _config.value
        val clear = cur.generation == startGeneration
        val updated = cur.copy(
            lastRevision = revision,
            lastSuccessAt = System.currentTimeMillis(),
            generation = if (clear) 0 else cur.generation,
            lastError = null,
            authError = false,
            conflict = false,
            conflictDevice = "",
            conflictRevision = 0
        )
        emit(updated)
        return !clear
    }

    @Synchronized
    fun clearDirty() {
        emit(_config.value.copy(generation = 0))
    }

    @Synchronized
    fun markRestored(revision: Int) {
        emit(
            _config.value.copy(
                lastRevision = revision,
                lastSuccessAt = System.currentTimeMillis(),
                generation = 0,
                lastError = null,
                authError = false,
                conflict = false,
                conflictDevice = "",
                conflictRevision = 0
            )
        )
    }

    @Synchronized
    fun markConflict(deviceName: String, revision: Int) {
        emit(
            _config.value.copy(
                conflict = true,
                conflictDevice = deviceName,
                conflictRevision = revision,
                lastError = "云端已有其他设备的更新"
            )
        )
    }

    @Synchronized
    fun markError(message: String, authError: Boolean = false) {
        emit(
            _config.value.copy(
                lastError = message,
                authError = authError
            )
        )
    }

    @Synchronized
    fun clearConflict() {
        emit(
            _config.value.copy(
                conflict = false,
                conflictDevice = "",
                conflictRevision = 0
            )
        )
    }

    @Synchronized
    fun clearAuthError() {
        emit(_config.value.copy(authError = false, lastError = null))
    }

    private fun emit(value: SyncConfig) {
        _config.value = value
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putString(KEY_BASE_URL, value.baseUrl)
            .putString(KEY_TOKEN, value.token)
            .putString(KEY_DEVICE_NAME, value.deviceName)
            .putBoolean(KEY_WIFI_ONLY, value.wifiOnly)
            .putBoolean(KEY_IS_PRIMARY, value.isPrimary)
            .putInt(KEY_GENERATION, value.generation)
            .putInt(KEY_LAST_REVISION, value.lastRevision ?: -1)
            .putLong(KEY_LAST_SUCCESS, value.lastSuccessAt)
            .putString(KEY_LAST_ERROR, value.lastError)
            .putBoolean(KEY_AUTH_ERROR, value.authError)
            .putBoolean(KEY_CONFLICT, value.conflict)
            .putString(KEY_CONFLICT_DEVICE, value.conflictDevice)
            .putInt(KEY_CONFLICT_REVISION, value.conflictRevision)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_IS_PRIMARY = "is_primary"
        private const val KEY_GENERATION = "generation"
        private const val KEY_LAST_REVISION = "last_revision"
        private const val KEY_LAST_SUCCESS = "last_success_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_AUTH_ERROR = "auth_error"
        private const val KEY_CONFLICT = "conflict"
        private const val KEY_CONFLICT_DEVICE = "conflict_device"
        private const val KEY_CONFLICT_REVISION = "conflict_revision"

        fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return ""
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            return value.trimEnd('/')
        }
    }
}
