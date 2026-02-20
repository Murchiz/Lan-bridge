package com.lanbridge.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object PlatformTransferHistoryStore {
    private const val FILE_NAME = "transfer_history.json"

    actual suspend fun load(): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val context = AndroidContextHolder.appContext
                ?: error("Android context not initialized")
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                null
            } else {
                file.readText()
            }
        }
    }

    actual suspend fun save(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val context = AndroidContextHolder.appContext
                ?: error("Android context not initialized")
            val file = File(context.filesDir, FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }
}
