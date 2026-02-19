package com.lanbridge.network

import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.lanbridge.model.SelectedFileMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    actual suspend fun saveIncomingFile(fileName: String, data: ByteArray): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(defaultSaveDirectory())
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val output = uniqueDestination(dir, fileName)
                output.outputStream().use { stream ->
                    stream.write(data)
                    stream.flush()
                }
                output.absolutePath
            }
        }
    }

    actual fun defaultSaveDirectory(): String {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "LanBridge").absolutePath
    }

    private fun uniqueDestination(dir: File, name: String): File {
        val base = File(dir, name)
        if (!base.exists()) {
            return base
        }

        val dotIndex = name.lastIndexOf('.')
        val stem = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        while (true) {
            val candidate = File(dir, "$stem-$counter$ext")
            if (!candidate.exists()) {
                return candidate
            }
            counter += 1
        }
    }
}
