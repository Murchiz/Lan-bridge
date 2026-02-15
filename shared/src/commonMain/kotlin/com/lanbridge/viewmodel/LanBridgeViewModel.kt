package com.lanbridge.viewmodel

import com.lanbridge.model.Device
import com.lanbridge.model.DevicePlatform
import com.lanbridge.model.TransferDirection
import com.lanbridge.model.TransferRecord
import com.lanbridge.model.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LanBridgeViewModel {
    private val _uiState = MutableStateFlow(
        LanBridgeUiState(
            devices = sampleDevices(),
            transfers = sampleTransfers(),
            selectedTab = LanBridgeTab.Devices,
            statusBanner = "Ready on local network"
        )
    )
    val uiState: StateFlow<LanBridgeUiState> = _uiState.asStateFlow()

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
        _uiState.value = _uiState.value.copy(deviceName = name)
    }

    fun toggleAutoAccept() {
        _uiState.value = _uiState.value.copy(autoAcceptTransfers = !_uiState.value.autoAcceptTransfers)
    }

    fun toggleDarkTheme() {
        _uiState.value = _uiState.value.copy(forceDarkTheme = !_uiState.value.forceDarkTheme)
    }

    fun resetMockData() {
        _uiState.value = _uiState.value.copy(
            devices = sampleDevices(),
            transfers = sampleTransfers(),
            statusBanner = "Mock data restored"
        )
    }

    private fun sampleDevices(): List<Device> {
        val now = System.currentTimeMillis()
        return listOf(
            Device(
                id = "dev-android-1",
                name = "Pixel 8 Pro",
                ipAddress = "192.168.1.42",
                platform = DevicePlatform.ANDROID,
                serverPort = 8294,
                lastSeenEpochMillis = now
            ),
            Device(
                id = "dev-win-1",
                name = "Office Desktop",
                ipAddress = "192.168.1.18",
                platform = DevicePlatform.WINDOWS,
                serverPort = 8294,
                lastSeenEpochMillis = now
            ),
            Device(
                id = "dev-linux-1",
                name = "Ubuntu Laptop",
                ipAddress = "192.168.1.77",
                platform = DevicePlatform.LINUX,
                serverPort = 8294,
                lastSeenEpochMillis = now
            )
        )
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
    val devicesState: ContentState = ContentState.Populated,
    val transfersState: ContentState = ContentState.Populated,
    val settingsState: ContentState = ContentState.Populated,
    val statusBanner: String,
    val deviceName: String = "My Device",
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
