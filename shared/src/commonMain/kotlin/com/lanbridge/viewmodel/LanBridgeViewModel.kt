package com.lanbridge.viewmodel

import com.lanbridge.model.Device
import com.lanbridge.model.TransferDirection
import com.lanbridge.model.TransferRecord
import com.lanbridge.model.TransferStatus
import com.lanbridge.network.DeviceDiscoveryManager
import com.lanbridge.network.FileTransferManager
import com.lanbridge.network.NetworkConstants
import com.lanbridge.network.PlatformFileAccess
import com.lanbridge.network.defaultDeviceName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

class LanBridgeViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val discoveryManager = DeviceDiscoveryManager(serverPort = 8294)
    private val transferManager = FileTransferManager()

    private val _uiState = MutableStateFlow(
        LanBridgeUiState(
            devices = emptyList(),
            transfers = emptyList(),
            selectedTab = LanBridgeTab.Devices,
            statusBanner = "Ready on local network",
            deviceName = defaultDeviceName(),
            transfersState = ContentState.Empty
        )
    )
    val uiState: StateFlow<LanBridgeUiState> = _uiState.asStateFlow()

    init {
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
                status = TransferStatus.IN_PROGRESS,
                peerName = device.name
            )
            _uiState.update {
                it.copy(
                    transfers = listOf(queuedRecord) + it.transfers,
                    transfersState = ContentState.Populated,
                    selectedTab = LanBridgeTab.Transfers
                )
            }

            val result = transferManager.sendFile(device = device, fileReference = fileReference) { progress ->
                updateTransferProgress(transferId, progress)
            }

            result.fold(
                onSuccess = {
                    updateTransferStatus(transferId = transferId, status = TransferStatus.COMPLETED)
                    pushMessage("Sent ${fileMeta.displayName} to ${device.name}")
                },
                onFailure = { throwable ->
                    updateTransferStatus(
                        transferId = transferId,
                        status = TransferStatus.FAILED,
                        error = throwable.message ?: "Transfer failed"
                    )
                    pushMessage("Transfer failed: ${throwable.message ?: "Unknown error"}")
                }
            )
        }
    }

    private fun updateTransferProgress(transferId: String, progress: Float) {
        val sanitized = progress.coerceIn(0f, 1f)
        _uiState.update { state ->
            state.copy(
                transfers = state.transfers.map { record ->
                    if (record.id == transferId) {
                        record.copy(progress = sanitized)
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

    private fun restartDiscovery() {
        scope.launch {
            discoveryManager.stop()
            discoveryManager.start(deviceName = _uiState.value.deviceName)
            pushMessage("Discovery restarted")
        }
    }

    fun close() {
        transferManager.close()
        discoveryManager.close()
        scope.cancel()
    }
}

data class LanBridgeUiState(
    val selectedTab: LanBridgeTab,
    val devices: List<Device>,
    val transfers: List<TransferRecord>,
    val devicesState: ContentState = ContentState.Loading,
    val transfersState: ContentState = ContentState.Populated,
    val settingsState: ContentState = ContentState.Populated,
    val statusBanner: String,
    val deviceName: String,
    val saveLocation: String = "~/LanBridge",
    val autoAcceptTransfers: Boolean = true,
    val forceDarkTheme: Boolean = false
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
