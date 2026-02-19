package com.lanbridge.network

import com.lanbridge.model.SelectedFileMeta

expect object PlatformFileAccess {
    suspend fun readMetadata(fileReference: String): Result<SelectedFileMeta>
    suspend fun readBytes(fileReference: String): Result<ByteArray>
}
