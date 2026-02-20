package com.lanbridge.model

data class IncomingTransferUpdate(
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sender: String,
    val progress: Float,
    val status: TransferStatus,
    val savedPath: String? = null,
    val errorMessage: String? = null
)
