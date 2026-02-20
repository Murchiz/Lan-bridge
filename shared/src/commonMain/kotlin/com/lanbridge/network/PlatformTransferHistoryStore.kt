package com.lanbridge.network

expect object PlatformTransferHistoryStore {
    suspend fun load(): Result<String?>
    suspend fun save(content: String): Result<Unit>
}
