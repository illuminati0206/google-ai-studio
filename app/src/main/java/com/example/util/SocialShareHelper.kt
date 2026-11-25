package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object SocialShareHelper {

    fun shareOutfitLook(
        context: Context,
        outfitTitle: String,
        occasion: String,
        stylingNotes: String,
        bitmap: Bitmap? = null,
        imagePath: String? = null
    ) {
        val shareText = buildString {
            appendLine("Atelier Lookbook — $outfitTitle")
            appendLine("Occasion: $occasion")
            if (stylingNotes.isNotBlank()) {
                appendLine()
                appendLine(stylingNotes)
            }
            appendLine()
            append("Styled with Atelier")
        }

        val shareFile: File? = when {
            bitmap != null -> {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, "atelier_look_${System.currentTimeMillis()}.png")
                runCatching {
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
                    file
                }.getOrNull()
            }
            !imagePath.isNullOrBlank() && File(imagePath).exists() -> File(imagePath)
            else -> null
        }

        val intent = if (shareFile != null) {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Atelier Look: $outfitTitle")
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Atelier Look: $outfitTitle")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
        }

        context.startActivity(Intent.createChooser(intent, "Share Atelier Look"))
    }
}
