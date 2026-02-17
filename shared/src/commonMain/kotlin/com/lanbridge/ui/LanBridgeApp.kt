package com.lanbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
fun LanBridgeRoot(viewModel: LanBridgeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colorScheme = if (uiState.forceDarkTheme) darkColorSchemeLanBridge() else lightColorSchemeLanBridge()
    MaterialTheme(colorScheme = colorScheme) {
        LanBridgeScreen(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanBridgeScreen(viewModel: LanBridgeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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
                        text = "Phase 2",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedTab == LanBridgeTab.Devices) {
                FloatingActionButton(onClick = { showManualDialog = true }) {
                    Icon(Icons.Filled.Send, contentDescription = "Add manually")
                }
            }
        },
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
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "LAN status: ${uiState.statusBanner}", style = MaterialTheme.typography.bodyMedium)

                when (uiState.selectedTab) {
                    LanBridgeTab.Devices -> DevicesTab(
                        state = uiState.devicesState,
                        devices = uiState.devices,
                        onStateChange = viewModel::setDevicesState,
                        onActionMessage = viewModel::pushMessage,
                        onAddManualClick = { showManualDialog = true }
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
                        onAutoAcceptToggle = {
                            viewModel.toggleAutoAccept()
                            scope.launch { snackbarHostState.showSnackbar("Auto-accept toggled") }
                        },
                        onThemeToggle = {
                            viewModel.toggleDarkTheme()
                            scope.launch { snackbarHostState.showSnackbar("Theme toggled") }
                        },
                        onActionMessage = viewModel::pushMessage
                    )
                }
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
    onAddManualClick: () -> Unit
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
                DeviceCard(device = device, onSendClick = { onActionMessage("Send to ${device.name} coming soon") })
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
                    Text("LanBridge v0.2.0", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: Device, onSendClick: () -> Unit) {
    val icon = when (device.platform) {
        DevicePlatform.ANDROID -> Icons.Filled.PhoneAndroid
        DevicePlatform.WINDOWS,
        DevicePlatform.LINUX,
        DevicePlatform.UNKNOWN -> Icons.Filled.Computer
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = device.platform.name)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = device.name, fontWeight = FontWeight.Bold)
                        Text(text = "${device.ipAddress}:${device.serverPort}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Badge { Text(if (device.isManual) "manual" else device.platform.name.lowercase()) }
            }
            Button(onClick = onSendClick) {
                Text("Send File")
            }
        }
    }
}

@Composable
private fun TransferCard(transfer: TransferRecord, onActionMessage: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = transfer.fileName, fontWeight = FontWeight.Bold)
            Text(text = "${formatSize(transfer.fileSizeBytes)} • ${transfer.peerName}")
            androidx.compose.material3.LinearProgressIndicator(progress = { transfer.progress })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TransferStatusChip(status = transfer.status)
                OutlinedButton(onClick = { onActionMessage("Clicked ${transfer.fileName}") }) {
                    Text("Details")
                }
            }
            transfer.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TransferStatusChip(status: TransferStatus) {
    val label: String
    val icon: ImageVector
    when (status) {
        TransferStatus.QUEUED -> {
            label = "Queued"
            icon = Icons.Filled.History
        }
        TransferStatus.IN_PROGRESS -> {
            label = "In Progress"
            icon = Icons.Filled.Send
        }
        TransferStatus.COMPLETED -> {
            label = "Completed"
            icon = Icons.Filled.CheckCircle
        }
        TransferStatus.FAILED -> {
            label = "Failed"
            icon = Icons.Filled.Error
        }
        TransferStatus.CANCELLED -> {
            label = "Cancelled"
            icon = Icons.Filled.Error
        }
    }
    AssistChip(onClick = {}, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = label) })
}

@Composable
private fun ContentStateSwitcher(current: ContentState, onStateChange: (ContentState) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StateButton("Loading", current == ContentState.Loading) { onStateChange(ContentState.Loading) }
        StateButton("Empty", current == ContentState.Empty) { onStateChange(ContentState.Empty) }
        StateButton("Error", current == ContentState.Error) { onStateChange(ContentState.Error) }
        StateButton("Data", current == ContentState.Populated) { onStateChange(ContentState.Populated) }
    }
}

@Composable
private fun StateButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
    }
}

@Composable
private fun LoadingState(message: String) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Text(message)
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

private fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.2f MB".format(bytes / mb)
        bytes >= kb -> "%.2f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
