package com.lanbridge.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object PlatformTransferHistoryStore {
    private val historyFile = File(
        File(System.getProperty("user.home"), ".lanbridge"),
        "transfer_history.json"
    )

    actual suspend fun load(): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!historyFile.exists()) {
                null
            } else {
                historyFile.readText()
            }
        }
    }

    actual suspend fun save(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            historyFile.parentFile?.mkdirs()
            historyFile.writeText(content)
        }
    }
}
