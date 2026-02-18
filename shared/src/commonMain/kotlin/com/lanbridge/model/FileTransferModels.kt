package com.lanbridge.model

import kotlinx.serialization.Serializable

@Serializable
data class TransferMetadata(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

@Serializable
data class TransferResponse(
    val status: String,
    val savedPath: String? = null,
    val message: String? = null
)

data class SelectedFileMeta(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String
)
