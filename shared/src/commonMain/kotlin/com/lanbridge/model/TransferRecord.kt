package com.lanbridge.model

import kotlinx.serialization.Serializable

@Serializable
data class TransferRecord(
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val direction: TransferDirection,
    val progress: Float,
    val status: TransferStatus,
    val peerName: String,
    val createdAtEpochMillis: Long,
    val speedBytesPerSecond: Long = 0,
    val savedPath: String? = null,
    val sourceFileReference: String? = null,
    val targetIpAddress: String? = null,
    val targetPort: Int? = null,
    val errorMessage: String? = null
)

@Serializable
enum class TransferDirection {
    SENDING,
    RECEIVING
}

@Serializable
enum class TransferStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}
