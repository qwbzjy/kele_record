package com.cola.record.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SyncException(
    val kind: Kind,
    message: String,
    val serverState: SyncStateDto? = null,
    val httpCode: Int = 0,
    val primaryDeviceName: String = ""
) : Exception(message) {
    enum class Kind {
        CONFIG, NETWORK, UNAUTHORIZED, CONFLICT, TOO_LARGE,
        NOT_FOUND, NOT_PRIMARY, HTTP, INVALID_DATA
    }
}

@Serializable
data class SyncStateDto(
    val revision: Int = 0,
    val sha256: String? = null,
    val dataBytes: Long = 0,
    val receivedAt: Long = 0,
    val clientExportedAt: Long = 0,
    val deviceId: String = "",
    val deviceName: String = "",
    val recordCount: Int = 0,
    val images: List<String> = emptyList(),
    val imageCount: Int = 0,
    val imagesMissing: List<String> = emptyList(),
    val referencedImages: Int = 0,
    val primaryDeviceId: String = "",
    val primaryDeviceName: String = ""
)

@Serializable
data class ImagesResultDto(
    val accepted: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
    val rejected: List<RejectedDto> = emptyList()
)

@Serializable
data class RejectedDto(val name: String = "", val reason: String = "")

@Serializable
private data class ServerErrorDto(
    val detail: String? = null,
    val error: String? = null,
    val server: SyncStateDto? = null,
    val primaryDeviceId: String? = null,
    val primaryDeviceName: String? = null
)

data class DownloadResult(
    val file: File,
    val revision: Int,
    val imagesMissing: Int
)

class SyncApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getState(cfg: SyncConfig): SyncStateDto = withContext(Dispatchers.IO) {
        val request = auth(cfg, endpoint(cfg, "/api/v1/state")).get().build()
        executeState(request)
    }

    suspend fun uploadData(
        cfg: SyncConfig,
        file: File,
        baseRevision: Int?
    ): SyncStateDto = withContext(Dispatchers.IO) {
        val builder = auth(cfg, endpoint(cfg, "/api/v1/data"))
            .header("X-Device-Id", cfg.deviceId)
            .header("X-Device-Name", URLEncoder.encode(cfg.deviceName, "UTF-8"))
            .post(file.asRequestBody(JSON_MEDIA))
        if (baseRevision != null) {
            builder.header("X-Base-Revision", baseRevision.toString())
        }
        executeState(builder.build())
    }

    suspend fun uploadImages(
        cfg: SyncConfig,
        files: List<File>
    ): ImagesResultDto = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext ImagesResultDto()
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        files.forEach { file ->
            multipart.addFormDataPart(
                "files",
                file.name,
                file.asRequestBody("image/jpeg".toMediaType())
            )
        }
        val request = auth(cfg, endpoint(cfg, "/api/v1/images"))
            .header("X-Device-Id", cfg.deviceId)
            .header("X-Device-Name", URLEncoder.encode(cfg.deviceName, "UTF-8"))
            .post(multipart.build())
            .build()
        executeImages(request)
    }

    suspend fun downloadBackup(
        cfg: SyncConfig,
        target: File,
        onProgress: (done: Long, total: Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val request = auth(cfg, endpoint(cfg, "/api/v1/backup.zip")).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw parseError(response.code, response.body?.string().orEmpty())
                }
                val body = response.body ?: throw SyncException(
                    SyncException.Kind.INVALID_DATA, "服务器返回内容为空"
                )
                val total = body.contentLength()
                target.parentFile?.mkdirs()
                var done = 0L
                body.byteStream().use { input ->
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                DownloadResult(
                    file = target,
                    revision = response.header("X-Snapshot-Revision")?.toIntOrNull() ?: 0,
                    imagesMissing = response.header("X-Images-Missing")?.toIntOrNull() ?: 0
                )
            }
        } catch (e: SyncException) {
            target.delete()
            throw e
        } catch (e: IOException) {
            target.delete()
            throw SyncException(SyncException.Kind.NETWORK, "网络错误：${e.message ?: "无法连接服务器"}")
        } catch (e: Exception) {
            target.delete()
            throw SyncException(SyncException.Kind.INVALID_DATA, "下载失败：${e.message ?: "未知错误"}")
        }
    }

    private fun executeState(request: Request): SyncStateDto = try {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw parseError(response.code, text)
            }
            json.decodeFromString(SyncStateDto.serializer(), text)
        }
    } catch (e: SyncException) {
        throw e
    } catch (e: IOException) {
        throw SyncException(SyncException.Kind.NETWORK, "网络错误：${e.message ?: "无法连接服务器"}")
    } catch (e: Exception) {
        throw SyncException(SyncException.Kind.INVALID_DATA, "服务器返回格式不正确")
    }

    private fun executeImages(request: Request): ImagesResultDto = try {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw parseError(response.code, text)
            }
            json.decodeFromString(ImagesResultDto.serializer(), text)
        }
    } catch (e: SyncException) {
        throw e
    } catch (e: IOException) {
        throw SyncException(SyncException.Kind.NETWORK, "网络错误：${e.message ?: "无法连接服务器"}")
    } catch (e: Exception) {
        throw SyncException(SyncException.Kind.INVALID_DATA, "服务器返回格式不正确")
    }

    private fun auth(cfg: SyncConfig, url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${cfg.token}")

    private fun endpoint(cfg: SyncConfig, path: String): HttpUrl {
        if (!cfg.configured) {
            throw SyncException(SyncException.Kind.CONFIG, "请先填写服务器地址和 Token")
        }
        val url = (cfg.baseUrl.trimEnd('/') + path).toHttpUrlOrNull()
            ?: throw SyncException(SyncException.Kind.CONFIG, "服务器地址不合法")
        return url
    }

    private fun parseError(code: Int, body: String): SyncException {
        val parsed = runCatching {
            json.decodeFromString(ServerErrorDto.serializer(), body)
        }.getOrNull()
        val message = parsed?.detail ?: parsed?.error ?: "服务器返回错误（HTTP $code）"
        return when (code) {
            401 -> SyncException(SyncException.Kind.UNAUTHORIZED, parsed?.detail ?: "Token 不正确", httpCode = code)
            403 -> SyncException(
                SyncException.Kind.NOT_PRIMARY,
                parsed?.detail ?: "本机不是主设备，不能上传",
                httpCode = code,
                primaryDeviceName = parsed?.primaryDeviceName ?: ""
            )
            409 -> SyncException(SyncException.Kind.CONFLICT, "云端数据已被其他设备更新", parsed?.server, code)
            413 -> SyncException(SyncException.Kind.TOO_LARGE, "文件过大，服务器拒绝", httpCode = code)
            404 -> SyncException(SyncException.Kind.NOT_FOUND, parsed?.detail ?: "云端还没有备份", httpCode = code)
            else -> SyncException(SyncException.Kind.HTTP, message, httpCode = code)
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
