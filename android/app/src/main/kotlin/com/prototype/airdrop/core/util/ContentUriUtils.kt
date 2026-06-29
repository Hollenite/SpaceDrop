package com.prototype.airdrop.core.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

object ContentUriUtils {

    fun resolveDisplayName(context: Context, uri: Uri, mimeType: String?): String {
        val fromProvider = queryDisplayName(context, uri)
        val baseName = fromProvider ?: uri.lastPathSegment ?: "file"
        return ensureExtension(baseName, mimeType ?: context.contentResolver.getType(uri))
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        cursor.getString(index)?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun ensureExtension(name: String, mimeType: String?): String {
        if (name.contains('.')) return sanitizeFilename(name)
        val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val withExt = if (!extension.isNullOrBlank()) "$name.$extension" else name
        return sanitizeFilename(withExt)
    }

    private fun sanitizeFilename(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast(':')
        return cleaned.ifBlank { "file" }
    }
}
