package com.cola.record.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PhotoStore {
    private const val MAX_DIM = 1600
    private const val QUALITY = 85
    private val nameRegex = Regex("^[A-Za-z0-9_-]{1,80}\\.(jpg|jpeg|png|webp)$")

    fun isValidName(name: String): Boolean = nameRegex.matches(name)

    fun dir(context: Context): File =
        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > MAX_DIM * 2 || bounds.outHeight / sample > MAX_DIM * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@withContext null

            val rotated = rotateByExif(context, uri, bitmap)
            val scaled = scaleDown(rotated, MAX_DIM)
            val name = UUID.randomUUID().toString() + ".jpg"
            FileOutputStream(file(context, name)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            if (scaled !== rotated) scaled.recycle()
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context, name: String) {
        runCatching { file(context, name).delete() }
    }

    fun clearAll(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    fun totalBytes(context: Context): Long {
        return runCatching {
            dir(context).listFiles()?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    private fun rotateByExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: return bitmap

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
