package com.lanbridge.network

interface UdpSocket : AutoCloseable {
    fun send(payload: ByteArray, host: String, port: Int)
    fun receive(maxPacketSize: Int, timeoutMillis: Int): UdpPacket?
}

data class UdpPacket(
    val payload: ByteArray,
    val sourceIp: String
)

expect fun createUdpSocket(port: Int, broadcast: Boolean): UdpSocket

expect fun resolveBroadcastAddresses(): List<String>

expect fun getLocalIpAddresses(): Set<String>

expect fun defaultDeviceName(): String

expect fun platformFromSystem(): com.lanbridge.model.DevicePlatform

expect fun currentTimeMillis(): Long
