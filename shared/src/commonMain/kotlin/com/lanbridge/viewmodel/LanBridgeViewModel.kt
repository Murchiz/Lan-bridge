package com.lanbridge.viewmodel

import com.lanbridge.model.Device
import com.lanbridge.model.TransferDirection
import com.lanbridge.model.TransferRecord
import com.lanbridge.model.TransferStatus
import com.lanbridge.network.DeviceDiscoveryManager
import com.lanbridge.network.NetworkConstants
import com.lanbridge.network.defaultDeviceName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LanBridgeViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val discoveryManager = DeviceDiscoveryManager(serverPort = 8294)

    private val _uiState = MutableStateFlow(
        LanBridgeUiState(
            devices = emptyList(),
            transfers = sampleTransfers(),
            selectedTab = LanBridgeTab.Devices,
            statusBanner = "Ready on local network",
            deviceName = defaultDeviceName()
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

    private fun restartDiscovery() {
        scope.launch {
            discoveryManager.stop()
            discoveryManager.start(deviceName = _uiState.value.deviceName)
            pushMessage("Discovery restarted")
        }
    }

    fun close() {
        discoveryManager.close()
        scope.cancel()
    }

    private fun sampleTransfers(): List<TransferRecord> {
        return listOf(
            TransferRecord(
                id = "tx-1",
                fileName = "trip-photo.jpg",
                fileSizeBytes = 1_048_576,
                direction = TransferDirection.SENDING,
                progress = 1f,
                status = TransferStatus.COMPLETED,
                peerName = "Office Desktop"
            ),
            TransferRecord(
                id = "tx-2",
                fileName = "presentation.pdf",
                fileSizeBytes = 18_432_000,
                direction = TransferDirection.RECEIVING,
                progress = 0.62f,
                status = TransferStatus.IN_PROGRESS,
                peerName = "Ubuntu Laptop"
            ),
            TransferRecord(
                id = "tx-3",
                fileName = "archive.zip",
                fileSizeBytes = 734_003_200,
                direction = TransferDirection.SENDING,
                progress = 0.28f,
                status = TransferStatus.FAILED,
                peerName = "Pixel 8 Pro",
                errorMessage = "Connection timed out"
            )
        )
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
