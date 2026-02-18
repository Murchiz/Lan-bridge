package com.lanbridge.network

import android.net.Uri
import android.provider.OpenableColumns
import com.lanbridge.model.SelectedFileMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object PlatformFileAccess {
    actual suspend fun readMetadata(fileReference: String): Result<SelectedFileMeta> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val context = AndroidContextHolder.appContext
                    ?: error("Android context not initialized")
                val uri = Uri.parse(fileReference)
                val contentResolver = context.contentResolver

                var name = "selected_file"
                var size = -1L
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) {
                            name = cursor.getString(nameIndex) ?: name
                        }
                        if (sizeIndex >= 0) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }

                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                SelectedFileMeta(displayName = name, sizeBytes = if (size < 0) 0 else size, mimeType = mime)
            }
        }
    }

    actual suspend fun readBytes(fileReference: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val context = AndroidContextHolder.appContext
                    ?: error("Android context not initialized")
                val uri = Uri.parse(fileReference)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: error("Unable to open selected file")
            }
        }
    }
}
