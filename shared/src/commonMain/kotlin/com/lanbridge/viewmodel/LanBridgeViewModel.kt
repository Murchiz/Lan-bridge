package com.lanbridge.viewmodel

import com.lanbridge.model.Device
import com.lanbridge.model.IncomingTransferUpdate
import com.lanbridge.model.TransferDirection
import com.lanbridge.model.TransferRecord
import com.lanbridge.model.TransferStatus
import com.lanbridge.network.DeviceDiscoveryManager
import com.lanbridge.network.FileTransferManager
import com.lanbridge.network.NetworkConstants
import com.lanbridge.network.PlatformFileAccess
import com.lanbridge.network.PlatformTransferHistoryStore
import com.lanbridge.network.TransferServerManager
import com.lanbridge.network.defaultDeviceName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.TimeSource

class LanBridgeViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val discoveryManager = DeviceDiscoveryManager(serverPort = 8294)
    private val transferManager = FileTransferManager()
    private val transferServerManager = TransferServerManager()
    private val historyJson = Json { ignoreUnknownKeys = true }

    private var queueWorker: Job? = null
    private var activeTransferJob: Job? = null

    private val pendingQueue = ArrayDeque<PendingTransfer>()

    private val _uiState = MutableStateFlow(
        LanBridgeUiState(
            devices = emptyList(),
            transfers = emptyList(),
            selectedTab = LanBridgeTab.Devices,
            statusBanner = "Ready on local network",
            deviceName = defaultDeviceName(),
            saveLocation = PlatformFileAccess.defaultSaveDirectory(),
            transfersState = ContentState.Empty
        )
    )
    val uiState: StateFlow<LanBridgeUiState> = _uiState.asStateFlow()

    init {
        loadHistory()

        transferServerManager.start(NetworkConstants.TransferServerFallbackPort)
            .onFailure { pushMessage("Server start failed: ${it.message}") }
            .onSuccess { pushMessage("Receiving on port ${NetworkConstants.TransferServerFallbackPort}") }

        discoveryManager.start(deviceName = _uiState.value.deviceName)

        scope.launch {
            discoveryManager.devices.collect { devices ->
                _uiState.update {
                    it.copy(
                        devices = devices,
                        devicesState = if (devices.isEmpty()) ContentState.Empty else ContentState.Populated
                    )
                }
            }
        }

        scope.launch {
            discoveryManager.errorEvents.collect { error ->
                if (!error.isNullOrBlank()) {
                    pushMessage(error)
                    discoveryManager.clearError()
                }
            }
        }

        scope.launch {
            transferServerManager.incomingTransfers.collect { update ->
                applyIncomingTransfer(update)
            }
        }

        scope.launch {
            transferServerManager.errors.collect { error ->
                pushMessage(error)
            }
        }
    }

    fun selectTab(tab: LanBridgeTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun setDevicesState(contentState: ContentState) {
        _uiState.value = _uiState.value.copy(devicesState = contentState)
    }

    fun setTransfersState(contentState: ContentState) {
        _uiState.value = _uiState.value.copy(transfersState = contentState)
    }

    fun setSettingsState(contentState: ContentState) {
        _uiState.value = _uiState.value.copy(settingsState = contentState)
    }

    fun pushMessage(message: String) {
        _uiState.value = _uiState.value.copy(statusBanner = message)
    }

    fun updateDeviceName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            pushMessage("Device name cannot be empty")
            return
        }
        _uiState.value = _uiState.value.copy(deviceName = trimmed)
        restartDiscovery()
    }

    fun toggleAutoAccept() {
        _uiState.value = _uiState.value.copy(autoAcceptTransfers = !_uiState.value.autoAcceptTransfers)
    }

    fun toggleDarkTheme() {
        _uiState.value = _uiState.value.copy(forceDarkTheme = !_uiState.value.forceDarkTheme)
    }

    fun addManualDevice(ipAddress: String, portText: String) {
        scope.launch {
            val port = portText.toIntOrNull() ?: NetworkConstants.TransferServerFallbackPort
            discoveryManager.addManualDevice(ip = ipAddress, port = port)
            pushMessage("Added manual device $ipAddress:$port")
        }
    }

    fun sendFileToDevice(device: Device, fileReference: String) {
        scope.launch {
            val metadata = PlatformFileAccess.readMetadata(fileReference)
            if (metadata.isFailure) {
                pushMessage(metadata.exceptionOrNull()?.message ?: "Could not read selected file")
                return@launch
            }

            val fileMeta = metadata.getOrThrow()
            val transferId = "tx-${Random.nextLong().toString(16)}"
            val queuedRecord = TransferRecord(
                id = transferId,
                fileName = fileMeta.displayName,
                fileSizeBytes = fileMeta.sizeBytes,
                direction = TransferDirection.SENDING,
                progress = 0f,
                status = TransferStatus.QUEUED,
                peerName = device.name,
                createdAtEpochMillis = currentTimeMillis(),
                sourceFileReference = fileReference,
                targetIpAddress = device.ipAddress,
                targetPort = device.serverPort
            )
            pendingQueue.addLast(PendingTransfer(transferId = transferId, device = device, fileReference = fileReference))
            _uiState.update {
                it.copy(
                    transfers = listOf(queuedRecord) + it.transfers,
                    transfersState = ContentState.Populated,
                    selectedTab = LanBridgeTab.Transfers
                )
            }
            persistHistory()
            processQueue()
        }
    }

    fun retryTransfer(transferId: String) {
        val transfer = _uiState.value.transfers.firstOrNull { it.id == transferId } ?: return
        if (transfer.direction != TransferDirection.SENDING || transfer.sourceFileReference.isNullOrBlank()) {
            pushMessage("Retry is only available for sent files from this device")
            return
        }

        val device = _uiState.value.devices.firstOrNull {
            it.ipAddress == transfer.targetIpAddress && it.serverPort == transfer.targetPort
        }

        if (device == null) {
            pushMessage("Target device is no longer available for retry")
            return
        }

        sendFileToDevice(device, transfer.sourceFileReference)
    }

    fun cancelTransfer(transferId: String) {
        pendingQueue.removeAll { it.transferId == transferId }
        if (_uiState.value.activeTransferId == transferId) {
            activeTransferJob?.cancel()
        }
        updateTransferStatus(transferId = transferId, status = TransferStatus.CANCELLED, error = "Cancelled by user")
        pushMessage("Transfer cancelled")
        persistHistory()
    }

    fun clearTransferHistory() {
        pendingQueue.clear()
        activeTransferJob?.cancel()
        _uiState.update {
            it.copy(
                transfers = emptyList(),
                activeTransferId = null,
                transfersState = ContentState.Empty
            )
        }
        persistHistory()
        pushMessage("Transfer history cleared")
    }

    fun openTransferFile(transferId: String) {
        val path = _uiState.value.transfers.firstOrNull { it.id == transferId }?.savedPath
        if (path.isNullOrBlank()) {
            pushMessage("No saved file path for this transfer")
            return
        }
        val result = PlatformFileAccess.openFile(path)
        if (result.isFailure) {
            pushMessage(result.exceptionOrNull()?.message ?: "Unable to open file")
        }
    }

    fun openTransferFolder(transferId: String) {
        val path = _uiState.value.transfers.firstOrNull { it.id == transferId }?.savedPath
        if (path.isNullOrBlank()) {
            pushMessage("No saved file path for this transfer")
            return
        }
        val parent = path.substringBeforeLast('/', path).substringBeforeLast('\\', path)
        val result = PlatformFileAccess.openFolder(parent)
        if (result.isFailure) {
            pushMessage(result.exceptionOrNull()?.message ?: "Unable to open folder")
        }
    }

    private fun processQueue() {
        if (queueWorker?.isActive == true) {
            return
        }
        queueWorker = scope.launch {
            while (pendingQueue.isNotEmpty()) {
                val next = pendingQueue.removeFirst()
                runSendingTransfer(next)
            }
            _uiState.update { it.copy(activeTransferId = null) }
            persistHistory()
        }
    }

    private suspend fun runSendingTransfer(pending: PendingTransfer) {
        val transfer = _uiState.value.transfers.firstOrNull { it.id == pending.transferId } ?: return
        updateTransferStatus(pending.transferId, TransferStatus.IN_PROGRESS)
        _uiState.update { it.copy(activeTransferId = pending.transferId) }

        val metadataResult = PlatformFileAccess.readMetadata(pending.fileReference)
        if (metadataResult.isFailure) {
            updateTransferStatus(
                transferId = pending.transferId,
                status = TransferStatus.FAILED,
                error = metadataResult.exceptionOrNull()?.message ?: "Could not read selected file"
            )
            persistHistory()
            return
        }

        val meta = metadataResult.getOrThrow()
        val startMark = TimeSource.Monotonic.markNow()

        activeTransferJob = scope.launch {
            val result = transferManager.sendFile(
                device = pending.device,
                fileReference = pending.fileReference
            ) { progress ->
                val elapsedSeconds = max(0.001, startMark.elapsedNow().inWholeMilliseconds / 1000.0)
                val speed = (meta.sizeBytes * progress / elapsedSeconds).toLong().coerceAtLeast(0)
                updateTransferProgress(pending.transferId, progress, speed)
            }

            result.fold(
                onSuccess = {
                    updateTransferStatus(transferId = pending.transferId, status = TransferStatus.COMPLETED)
                    pushMessage("Sent ${meta.displayName} to ${pending.device.name}")
                },
                onFailure = { throwable ->
                    val cancelled = throwable is kotlinx.coroutines.CancellationException
                    val status = if (cancelled) TransferStatus.CANCELLED else TransferStatus.FAILED
                    val message = if (cancelled) "Cancelled by user" else throwable.message ?: "Transfer failed"
                    updateTransferStatus(
                        transferId = pending.transferId,
                        status = status,
                        error = message
                    )
                    pushMessage("Transfer ${status.name.lowercase()}: $message")
                }
            )
            persistHistory()
        }
        activeTransferJob?.join()
        activeTransferJob = null
    }

    private fun applyIncomingTransfer(update: IncomingTransferUpdate) {
        val existing = _uiState.value.transfers.any { it.id == update.id }
        if (!existing) {
            val newRecord = TransferRecord(
                id = update.id,
                fileName = update.fileName,
                fileSizeBytes = update.fileSizeBytes,
                direction = TransferDirection.RECEIVING,
                progress = update.progress,
                status = update.status,
                peerName = update.sender,
                createdAtEpochMillis = currentTimeMillis(),
                savedPath = update.savedPath,
                errorMessage = update.errorMessage
            )
            _uiState.update {
                it.copy(
                    transfers = listOf(newRecord) + it.transfers,
                    transfersState = ContentState.Populated,
                    selectedTab = LanBridgeTab.Transfers
                )
            }
        } else {
            _uiState.update { state ->
                state.copy(
                    transfers = state.transfers.map { record ->
                        if (record.id == update.id) {
                            record.copy(
                                progress = update.progress,
                                status = update.status,
                                savedPath = update.savedPath ?: record.savedPath,
                                errorMessage = update.errorMessage
                            )
                        } else {
                            record
                        }
                    }
                )
            }
        }

        when (update.status) {
            TransferStatus.COMPLETED -> pushMessage("Received ${update.fileName} from ${update.sender}")
            TransferStatus.FAILED -> pushMessage("Receive failed: ${update.errorMessage ?: "Unknown error"}")
            else -> Unit
        }
        persistHistory()
    }

    private fun updateTransferProgress(transferId: String, progress: Float, speedBytesPerSecond: Long) {
        val sanitized = progress.coerceIn(0f, 1f)
        _uiState.update { state ->
            state.copy(
                transfers = state.transfers.map { record ->
                    if (record.id == transferId) {
                        record.copy(progress = sanitized, speedBytesPerSecond = speedBytesPerSecond)
                    } else {
                        record
                    }
                }
            )
        }
    }

    private fun updateTransferStatus(transferId: String, status: TransferStatus, error: String? = null) {
        _uiState.update { state ->
            state.copy(
                transfers = state.transfers.map { record ->
                    if (record.id == transferId) {
                        record.copy(
                            status = status,
                            progress = if (status == TransferStatus.COMPLETED) 1f else record.progress,
                            errorMessage = error
                        )
                    } else {
                        record
                    }
                }
            )
        }
    }

    private fun loadHistory() {
        scope.launch {
            val loaded = PlatformTransferHistoryStore.load()
            if (loaded.isFailure) {
                pushMessage("History load failed: ${loaded.exceptionOrNull()?.message}")
                return@launch
            }
            val text = loaded.getOrNull().orEmpty()
            if (text.isBlank()) {
                return@launch
            }
            runCatching {
                historyJson.decodeFromString<List<TransferRecord>>(text)
            }.onSuccess { records ->
                _uiState.update {
                    it.copy(
                        transfers = records,
                        transfersState = if (records.isEmpty()) ContentState.Empty else ContentState.Populated
                    )
                }
            }.onFailure {
                pushMessage("History parse failed: ${it.message}")
            }
        }
    }

    private fun persistHistory() {
        scope.launch {
            val payload = historyJson.encodeToString(_uiState.value.transfers.take(200))
            PlatformTransferHistoryStore.save(payload)
                .onFailure { pushMessage("History save failed: ${it.message}") }
        }
    }

    private fun restartDiscovery() {
        scope.launch {
            discoveryManager.stop()
            discoveryManager.start(deviceName = _uiState.value.deviceName)
            pushMessage("Discovery restarted")
        }
    }

    fun close() {
        transferServerManager.stop()
        transferManager.close()
        discoveryManager.close()
        scope.cancel()
    }
}

private data class PendingTransfer(
    val transferId: String,
    val device: Device,
    val fileReference: String
)

data class LanBridgeUiState(
    val selectedTab: LanBridgeTab,
    val devices: List<Device>,
    val transfers: List<TransferRecord>,
    val devicesState: ContentState = ContentState.Loading,
    val transfersState: ContentState = ContentState.Populated,
    val settingsState: ContentState = ContentState.Populated,
    val statusBanner: String,
    val deviceName: String,
    val saveLocation: String,
    val autoAcceptTransfers: Boolean = true,
    val forceDarkTheme: Boolean = false,
    val activeTransferId: String? = null
)

enum class LanBridgeTab {
    Devices,
    Transfers,
    Settings
}

enum class ContentState {
    Loading,
    Empty,
    Error,
    Populated
}

fun TransferRecord.progressText(): String = "${(progress * 100).roundToInt()}%"

fun TransferRecord.speedText(): String {
    if (speedBytesPerSecond <= 0) return "0 KB/s"
    val scaled = (speedBytesPerSecond / 10_485.76).roundToInt()
    val whole = scaled / 100
    val fraction = scaled % 100
    return "$whole.${fraction.toString().padStart(2, '0')} MB/s"
}

private fun currentTimeMillis(): Long = kotlin.system.getTimeMillis()
