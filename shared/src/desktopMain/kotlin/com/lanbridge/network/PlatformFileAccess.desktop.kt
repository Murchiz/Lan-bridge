package com.lanbridge.network

import com.lanbridge.model.SelectedFileMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

actual object PlatformFileAccess {
    actual suspend fun readMetadata(fileReference: String): Result<SelectedFileMeta> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(fileReference)
                require(file.exists()) { "Selected file does not exist" }
                val mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                SelectedFileMeta(
                    displayName = file.name,
                    sizeBytes = file.length(),
                    mimeType = mimeType
                )
            }
        }
    }

    actual suspend fun readBytes(fileReference: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(fileReference)
                require(file.exists()) { "Selected file does not exist" }
                file.readBytes()
            }
        }
    }
}
