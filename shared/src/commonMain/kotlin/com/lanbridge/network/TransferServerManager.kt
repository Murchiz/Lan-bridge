package com.lanbridge.network

import com.lanbridge.model.IncomingTransferUpdate
import com.lanbridge.model.TransferMetadata
import com.lanbridge.model.TransferResponse
import com.lanbridge.model.TransferStatus
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlin.random.Random

class TransferServerManager {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private val _incomingTransfers = MutableSharedFlow<IncomingTransferUpdate>(extraBufferCapacity = 64)
    val incomingTransfers = _incomingTransfers.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors = _errors.asSharedFlow()

    fun start(port: Int): Result<Unit> {
        return runCatching {
            if (server != null) {
                return@runCatching
            }
            server = embeddedServer(factory = io.ktor.server.cio.CIO, port = port) {
                transferRouting()
            }.also { it.start(wait = false) }
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
        server = null
    }

    private fun Application.transferRouting() {
        routing {
            post(NetworkConstants.TransferEndpoint) {
                val transferId = "rx-${Random.nextLong().toString(16)}"
                val sender = call.request.origin.remoteHost

                try {
                    val multipart = call.receiveMultipart()
                    var metadata: TransferMetadata? = null
                    var payload: ByteArray? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is io.ktor.http.content.PartData.FormItem -> {
                                if (part.name == "metadata") {
                                    metadata = json.decodeFromString(TransferMetadata.serializer(), part.value)
                                }
                            }
                            is io.ktor.http.content.PartData.FileItem -> {
                                if (part.name == "file") {
                                    payload = part.provider().readRemaining().readBytes()
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    val finalMetadata = metadata
                    val finalPayload = payload
                    if (finalMetadata == null || finalPayload == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            TransferResponse(status = "error", message = "metadata or file part missing")
                        )
                        return@post
                    }

                    _incomingTransfers.tryEmit(
                        IncomingTransferUpdate(
                            id = transferId,
                            fileName = finalMetadata.fileName,
                            fileSizeBytes = finalMetadata.fileSize,
                            sender = sender,
                            progress = 0f,
                            status = TransferStatus.IN_PROGRESS
                        )
                    )

                    val saveResult = PlatformFileAccess.saveIncomingFile(
                        fileName = finalMetadata.fileName,
                        data = finalPayload
                    )

                    saveResult.fold(
                        onSuccess = { path ->
                            _incomingTransfers.tryEmit(
                                IncomingTransferUpdate(
                                    id = transferId,
                                    fileName = finalMetadata.fileName,
                                    fileSizeBytes = finalMetadata.fileSize,
                                    sender = sender,
                                    progress = 1f,
                                    status = TransferStatus.COMPLETED,
                                    savedPath = path
                                )
                            )
                            call.respond(HttpStatusCode.OK, TransferResponse(status = "success", savedPath = path))
                        },
                        onFailure = { throwable ->
                            val message = throwable.message ?: "Failed to save incoming file"
                            _incomingTransfers.tryEmit(
                                IncomingTransferUpdate(
                                    id = transferId,
                                    fileName = finalMetadata.fileName,
                                    fileSizeBytes = finalMetadata.fileSize,
                                    sender = sender,
                                    progress = 0f,
                                    status = TransferStatus.FAILED,
                                    errorMessage = message
                                )
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                TransferResponse(status = "error", message = message)
                            )
                        }
                    )
                } catch (throwable: Throwable) {
                    val message = throwable.message ?: "Incoming transfer failed"
                    _errors.tryEmit("Transfer server error on ${call.request.uri}: $message")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        TransferResponse(status = "error", message = message)
                    )
                }
            }
        }
    }
}
