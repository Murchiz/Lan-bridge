package com.lanbridge.network

import com.lanbridge.model.DevicePlatform
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

actual fun createUdpSocket(port: Int, broadcast: Boolean): UdpSocket {
    val socket = DatagramSocket(port)
    socket.broadcast = broadcast
    return JvmUdpSocket(socket)
}

actual fun resolveBroadcastAddresses(): List<String> {
    val addresses = mutableSetOf<String>()
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return listOf("255.255.255.255")
    while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        if (!networkInterface.isUp || networkInterface.isLoopback) {
            continue
        }

        networkInterface.interfaceAddresses
            .mapNotNull { it.broadcast }
            .filterIsInstance<Inet4Address>()
            .forEach { addresses.add(it.hostAddress ?: return@forEach) }
    }

    if (addresses.isEmpty()) {
        addresses.add("255.255.255.255")
    }
    return addresses.toList()
}

actual fun getLocalIpAddresses(): Set<String> {
    val addresses = mutableSetOf<String>()
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptySet()
    while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        if (!networkInterface.isUp || networkInterface.isLoopback) {
            continue
        }

        val inetAddresses = networkInterface.inetAddresses
        while (inetAddresses.hasMoreElements()) {
            val address = inetAddresses.nextElement()
            if (address is Inet4Address && !address.isLoopbackAddress) {
                addresses.add(address.hostAddress ?: continue)
            }
        }
    }
    return addresses
}

actual fun defaultDeviceName(): String = java.net.InetAddress.getLocalHost().hostName ?: "Desktop"

actual fun platformFromSystem(): DevicePlatform {
    val osName = System.getProperty("os.name")?.lowercase().orEmpty()
    return when {
        osName.contains("win") -> DevicePlatform.WINDOWS
        osName.contains("linux") -> DevicePlatform.LINUX
        else -> DevicePlatform.UNKNOWN
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private class JvmUdpSocket(
    private val socket: DatagramSocket
) : UdpSocket {
    override fun send(payload: ByteArray, host: String, port: Int) {
        val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(host), port)
        socket.send(packet)
    }

    override fun receive(maxPacketSize: Int, timeoutMillis: Int): UdpPacket? {
        return try {
            socket.soTimeout = timeoutMillis
            val buffer = ByteArray(maxPacketSize)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            UdpPacket(
                payload = packet.data.copyOf(packet.length),
                sourceIp = packet.address.hostAddress
            )
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    override fun close() {
        socket.close()
    }
}
