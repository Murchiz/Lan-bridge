package com.lanbridge.network

import com.lanbridge.model.Device
import com.lanbridge.model.TransferMetadata
import com.lanbridge.model.TransferResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileTransferManager {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }

    suspend fun sendFile(
        device: Device,
        fileReference: String,
        onProgress: (Float) -> Unit
    ): Result<TransferResponse> {
        return runCatching {
            val metadata = PlatformFileAccess.readMetadata(fileReference).getOrThrow()
            val fileBytes = PlatformFileAccess.readBytes(fileReference).getOrThrow()
            onProgress(0.1f)

            val metadataJson = json.encodeToString(
                TransferMetadata(
                    fileName = metadata.displayName,
                    fileSize = metadata.sizeBytes,
                    mimeType = metadata.mimeType
                )
            )

            val response = client.post("http://${device.ipAddress}:${device.serverPort}${NetworkConstants.TransferEndpoint}") {
                contentType(ContentType.MultiPart.FormData)
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("metadata", metadataJson, Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            })
                            append("file", fileBytes, Headers.build {
                                append(HttpHeaders.ContentType, metadata.mimeType)
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(
                                        ContentDisposition.Parameters.FileName,
                                        metadata.displayName
                                    ).toString()
                                )
                            })
                        }
                    )
                )
            }

            onProgress(1f)
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) {
                throw IllegalStateException("Transfer failed (${response.status.value}): $body")
            }
            json.decodeFromString<TransferResponse>(body)
        }
    }

    fun close() {
        client.close()
    }
}
