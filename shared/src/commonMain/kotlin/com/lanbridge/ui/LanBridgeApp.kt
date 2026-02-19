package com.lanbridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanbridge.model.Device
import com.lanbridge.model.DevicePlatform
import com.lanbridge.model.TransferRecord
import com.lanbridge.model.TransferStatus
import com.lanbridge.network.NetworkConstants
import com.lanbridge.viewmodel.ContentState
import com.lanbridge.viewmodel.LanBridgeTab
import com.lanbridge.viewmodel.LanBridgeViewModel
import com.lanbridge.viewmodel.progressText

@Composable
fun LanBridgeRoot(
    viewModel: LanBridgeViewModel,
    onPickFileForDevice: (Device, (String?) -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorScheme = if (uiState.forceDarkTheme) darkColorSchemeLanBridge() else lightColorSchemeLanBridge()
    MaterialTheme(colorScheme = colorScheme) {
        LanBridgeScreen(viewModel = viewModel, onPickFileForDevice = onPickFileForDevice)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanBridgeScreen(
    viewModel: LanBridgeViewModel,
    onPickFileForDevice: (Device, (String?) -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusBanner) {
        if (uiState.statusBanner.isNotBlank()) {
            snackbarHostState.showSnackbar(uiState.statusBanner)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "LanBridge") },
                actions = {
                    Text(
                        text = "Phase 3",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            BottomAppBar {
                NavigationBarItem(
                    selected = uiState.selectedTab == LanBridgeTab.Devices,
                    onClick = { viewModel.selectTab(LanBridgeTab.Devices) },
                    icon = { Icon(Icons.Filled.Devices, contentDescription = "Devices") },
                    label = { Text("Devices") }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == LanBridgeTab.Transfers,
                    onClick = { viewModel.selectTab(LanBridgeTab.Transfers) },
                    icon = { Icon(Icons.Filled.History, contentDescription = "Transfers") },
                    label = { Text("Transfers") }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == LanBridgeTab.Settings,
                    onClick = { viewModel.selectTab(LanBridgeTab.Settings) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (uiState.selectedTab) {
                LanBridgeTab.Devices -> DevicesTab(
                    state = uiState.devicesState,
                    devices = uiState.devices,
                    onStateChange = viewModel::setDevicesState,
                    onActionMessage = viewModel::pushMessage,
                    onAddManualClick = { showManualDialog = true },
                    onSendToDevice = { device ->
                        onPickFileForDevice(device) { fileReference ->
                            if (fileReference != null) {
                                viewModel.sendFileToDevice(device, fileReference)
                            } else {
                                viewModel.pushMessage("File selection canceled")
                            }
                        }
                    }
                )

                LanBridgeTab.Transfers -> TransfersTab(
                    state = uiState.transfersState,
                    transfers = uiState.transfers,
                    onStateChange = viewModel::setTransfersState,
                    onActionMessage = viewModel::pushMessage
                )

                LanBridgeTab.Settings -> SettingsTab(
                    state = uiState.settingsState,
                    deviceName = uiState.deviceName,
                    saveLocation = uiState.saveLocation,
                    autoAcceptTransfers = uiState.autoAcceptTransfers,
                    forceDarkTheme = uiState.forceDarkTheme,
                    onStateChange = viewModel::setSettingsState,
                    onDeviceNameChanged = viewModel::updateDeviceName,
                    onAutoAcceptToggle = viewModel::toggleAutoAccept,
                    onThemeToggle = viewModel::toggleDarkTheme,
                    onActionMessage = viewModel::pushMessage
                )
            }
        }
    }

    if (showManualDialog) {
        ManualDeviceDialog(
            onDismiss = { showManualDialog = false },
            onAdd = { ip, port ->
                viewModel.addManualDevice(ipAddress = ip, portText = port)
                showManualDialog = false
            }
        )
    }
}

@Composable
private fun DevicesTab(
    state: ContentState,
    devices: List<Device>,
    onStateChange: (ContentState) -> Unit,
    onActionMessage: (String) -> Unit,
    onAddManualClick: () -> Unit,
    onSendToDevice: (Device) -> Unit
) {
    ContentStateSwitcher(current = state, onStateChange = onStateChange)
    when (state) {
        ContentState.Loading -> LoadingState("Scanning local network...")
        ContentState.Empty -> {
            EmptyState("No nearby devices yet")
            OutlinedButton(onClick = onAddManualClick) { Text("Add manually") }
        }
        ContentState.Error -> ErrorState("Unable to scan Wi-Fi. Check connection.")
        ContentState.Populated -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.id }) { device ->
                DeviceCard(device = device, onSendClick = {
                    onActionMessage("Choose a file for ${device.name}")
                    onSendToDevice(device)
                })
            }
            item {
                OutlinedButton(onClick = onAddManualClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Add manually")
                }
            }
        }
    }
}

@Composable
private fun ManualDeviceDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(NetworkConstants.TransferServerFallbackPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add device manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = ipAddress, onValueChange = { ipAddress = it }, label = { Text("IP address") })
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(ipAddress, port) }) {
                Text("Add")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TransfersTab(
    state: ContentState,
    transfers: List<TransferRecord>,
    onStateChange: (ContentState) -> Unit,
    onActionMessage: (String) -> Unit
) {
    ContentStateSwitcher(current = state, onStateChange = onStateChange)
    when (state) {
        ContentState.Loading -> LoadingState("Loading transfer history...")
        ContentState.Empty -> EmptyState("No transfers yet")
        ContentState.Error -> ErrorState("Failed to load transfers")
        ContentState.Populated -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transfers, key = { it.id }) { transfer ->
                TransferCard(transfer = transfer, onActionMessage = onActionMessage)
            }
        }
    }
}

@Composable
private fun SettingsTab(
    state: ContentState,
    deviceName: String,
    saveLocation: String,
    autoAcceptTransfers: Boolean,
    forceDarkTheme: Boolean,
    onStateChange: (ContentState) -> Unit,
    onDeviceNameChanged: (String) -> Unit,
    onAutoAcceptToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    onActionMessage: (String) -> Unit
) {
    ContentStateSwitcher(current = state, onStateChange = onStateChange)
    when (state) {
        ContentState.Loading -> LoadingState("Loading settings...")
        ContentState.Empty -> EmptyState("No settings available")
        ContentState.Error -> ErrorState("Settings failed to load")
        ContentState.Populated -> {
            var editableName by remember(deviceName) { mutableStateOf(deviceName) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editableName,
                        onValueChange = { editableName = it },
                        label = { Text("Device name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onDeviceNameChanged(editableName)
                            onActionMessage("Device name updated")
                        }) {
                            Text("Save name")
                        }
                        OutlinedButton(onClick = { onActionMessage("Save location picker coming soon") }) {
                            Text("Change folder")
                        }
                    }
                    Text(text = "Save location: $saveLocation", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Auto-accept transfers")
                        Switch(checked = autoAcceptTransfers, onCheckedChange = { onAutoAcceptToggle() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Force dark theme")
                        Switch(checked = forceDarkTheme, onCheckedChange = { onThemeToggle() })
                    }
                    HorizontalDivider()
                    Text("LanBridge v0.3.0", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: Device, onSendClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(platformIcon(device.platform), contentDescription = null)
                Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, label = { Text(device.platform.name.lowercase()) })
            }
            Text("${device.ipAddress}:${device.serverPort}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onSendClick) {
                Icon(Icons.Filled.Send, contentDescription = null)
                Text("Send file", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun TransferCard(transfer: TransferRecord, onActionMessage: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(transfer.fileName, style = MaterialTheme.typography.titleMedium)
            Text("${transfer.peerName} • ${transfer.progressText()}")
            LinearProgressIndicator(progress = { transfer.progress }, modifier = Modifier.fillMaxWidth())
            Text("${transfer.fileSizeBytes} bytes")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(statusIcon(transfer.status), contentDescription = null)
                Text(transfer.status.name.lowercase())
            }
            if (!transfer.errorMessage.isNullOrBlank()) {
                Text(transfer.errorMessage, color = MaterialTheme.colorScheme.error)
            }
            if (transfer.status == TransferStatus.FAILED) {
                OutlinedButton(onClick = { onActionMessage("Retry support is coming in Phase 5") }) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun ContentStateSwitcher(current: ContentState, onStateChange: (ContentState) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("State:")
        listOf(ContentState.Loading, ContentState.Empty, ContentState.Error, ContentState.Populated).forEach { state ->
            OutlinedButton(onClick = { onStateChange(state) }) {
                Text(state.name.lowercase())
            }
        }
        Text("Current ${current.name.lowercase()}")
    }
}

@Composable
private fun LoadingState(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator()
        Text(message)
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(message, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ErrorState(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Error, contentDescription = null)
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

private fun platformIcon(platform: DevicePlatform): ImageVector {
    return when (platform) {
        DevicePlatform.ANDROID -> Icons.Filled.PhoneAndroid
        DevicePlatform.WINDOWS,
        DevicePlatform.LINUX,
        DevicePlatform.UNKNOWN -> Icons.Filled.Computer
    }
}

private fun statusIcon(status: TransferStatus): ImageVector {
    return when (status) {
        TransferStatus.COMPLETED -> Icons.Filled.CheckCircle
        TransferStatus.FAILED,
        TransferStatus.CANCELLED -> Icons.Filled.Error
        TransferStatus.QUEUED,
        TransferStatus.IN_PROGRESS -> Icons.Filled.History
    }
}
