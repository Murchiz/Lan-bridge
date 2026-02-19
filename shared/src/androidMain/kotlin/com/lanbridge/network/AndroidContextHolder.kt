package com.lanbridge.network

import android.content.Context

object AndroidContextHolder {
    @Volatile
    var appContext: Context? = null
}
