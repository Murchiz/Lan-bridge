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
    val errorMessage: String? = null
)

enum class TransferDirection {
    SENDING,
    RECEIVING
}

enum class TransferStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}
