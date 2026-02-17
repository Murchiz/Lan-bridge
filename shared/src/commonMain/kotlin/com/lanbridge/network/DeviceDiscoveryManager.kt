package com.lanbridge.network

import com.lanbridge.model.Device
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class DeviceDiscoveryManager(
    private val serverPort: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val devicesMutex = Mutex()
    private val discoveredDevices = mutableMapOf<String, Device>()
    private val manualDevices = mutableMapOf<String, Device>()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _errorEvents = MutableStateFlow<String?>(null)
    val errorEvents: StateFlow<String?> = _errorEvents.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null

    private val selfId: String = "device-${Random.nextLong().toString(16)}"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun start(deviceName: String) {
        if (broadcastJob != null || listenJob != null) {
            return
        }

        broadcastJob = scope.launch {
            val packetPayload = {
                json.encodeToString(
                    DiscoveryAnnouncement(
                        id = selfId,
                        name = deviceName,
                        platform = platformFromSystem(),
                        serverPort = serverPort,
                        version = 1
                    )
                ).encodeToByteArray()
            }

            runCatching {
                createUdpSocket(port = 0, broadcast = true).use { socket ->
                    while (isActive) {
                        val payload = packetPayload()
                        val broadcastAddresses = resolveBroadcastAddresses().ifEmpty {
                            listOf("255.255.255.255")
                        }
                        broadcastAddresses.forEach { targetAddress ->
                            socket.send(payload = payload, host = targetAddress, port = NetworkConstants.DiscoveryPort)
                        }
                        delay(NetworkConstants.BroadcastIntervalMillis)
                    }
                }
            }.onFailure {
                _errorEvents.value = "Broadcast failed: ${it.message ?: "Unknown error"}"
            }
        }

        listenJob = scope.launch {
            val localAddresses = getLocalIpAddresses()
            runCatching {
                createUdpSocket(port = NetworkConstants.DiscoveryPort, broadcast = true).use { socket ->
                    while (isActive) {
                        val packet = socket.receive(maxPacketSize = 4096, timeoutMillis = 1_000) ?: continue
                        val announcement = runCatching {
                            json.decodeFromString<DiscoveryAnnouncement>(packet.payload.decodeToString())
                        }.getOrNull() ?: continue

                        if (announcement.id == selfId || packet.sourceIp in localAddresses) {
                            continue
                        }

                        val now = currentTimeMillis()
                        val device = Device(
                            id = announcement.id,
                            name = announcement.name,
                            ipAddress = packet.sourceIp,
                            platform = announcement.platform,
                            serverPort = announcement.serverPort,
                            lastSeenEpochMillis = now
                        )
                        devicesMutex.withLock {
                            discoveredDevices[device.id] = device
                            publishLocked()
                        }
                    }
                }
            }.onFailure {
                _errorEvents.value = "Listening failed: ${it.message ?: "Unknown error"}"
            }
        }

        cleanupJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val now = currentTimeMillis()
                devicesMutex.withLock {
                    discoveredDevices.entries.removeAll { (_, device) ->
                        now - device.lastSeenEpochMillis > NetworkConstants.DeviceTimeoutMillis
                    }
                    publishLocked()
                }
            }
        }
    }

    suspend fun stop() {
        broadcastJob?.cancelAndJoin()
        listenJob?.cancelAndJoin()
        cleanupJob?.cancelAndJoin()
        broadcastJob = null
        listenJob = null
        cleanupJob = null
    }


    fun close() {
        scope.cancel()
    }

    fun clearError() {
        _errorEvents.value = null
    }

    suspend fun addManualDevice(ip: String, port: Int) {
        val safeIp = ip.trim()
        if (safeIp.isBlank()) {
            _errorEvents.value = "Please enter a valid IP address"
            return
        }
        if (port !in 1..65_535) {
            _errorEvents.value = "Please enter a valid port"
            return
        }

        devicesMutex.withLock {
            val key = "manual-$safeIp-$port"
            manualDevices[key] = Device(
                id = key,
                name = "Manual device",
                ipAddress = safeIp,
                platform = com.lanbridge.model.DevicePlatform.UNKNOWN,
                serverPort = port,
                lastSeenEpochMillis = currentTimeMillis(),
                isManual = true
            )
            publishLocked()
        }
    }

    private fun publishLocked() {
        _devices.value = (discoveredDevices.values + manualDevices.values)
            .sortedBy { it.name.lowercase() }
    }

}
