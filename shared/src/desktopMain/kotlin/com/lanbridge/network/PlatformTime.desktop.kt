package com.lanbridge.network

actual object PlatformTime {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
