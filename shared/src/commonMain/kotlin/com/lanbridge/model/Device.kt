package com.lanbridge.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val name: String,
    val ipAddress: String,
    val platform: DevicePlatform,
    val serverPort: Int,
    val lastSeenEpochMillis: Long,
    val isManual: Boolean = false
)

@Serializable
enum class DevicePlatform {
    ANDROID,
    WINDOWS,
    LINUX,
    UNKNOWN
}
