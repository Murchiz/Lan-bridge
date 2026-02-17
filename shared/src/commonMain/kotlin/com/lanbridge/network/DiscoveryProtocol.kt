package com.lanbridge.network

import com.lanbridge.model.DevicePlatform
import kotlinx.serialization.Serializable

@Serializable
data class DiscoveryAnnouncement(
    val id: String,
    val name: String,
    val platform: DevicePlatform,
    val serverPort: Int,
    val version: Int = 1
)
