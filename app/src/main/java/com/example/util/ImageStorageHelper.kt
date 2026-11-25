package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageStorageHelper {

    fun createCameraCaptureUri(context: Context, prefix: String): Pair<Uri, File> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    /**
     * Safely decodes, downscales (max 1024px to keep multimodal AI payloads well under 20MB),
     * and persists a picked or captured image inside app-private filesDir.
     */
    fun persistCompressedImage(
        context: Context,
        sourceUri: Uri,
        subDir: String,
        filePrefix: String
    ): Pair<String, Bitmap>? {
        val bitmap = decodeScaledBitmapFromUri(context, sourceUri, maxDimension = 1024) ?: return null
        val savedPath = saveBitmap(context, bitmap, subDir, filePrefix) ?: return null
        return savedPath to bitmap
    }

    fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        subDir: String,
        filePrefix: String
    ): String? {
        return try {
            val dir = File(context.filesDir, subDir).apply { mkdirs() }
            val outFile = File(dir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun decodeScaledBitmapFromPath(path: String?, maxDimension: Int = 768): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeScaledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1024): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        if (largest > maxDim && maxDim > 0) {
            while ((largest / inSampleSize) > maxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
