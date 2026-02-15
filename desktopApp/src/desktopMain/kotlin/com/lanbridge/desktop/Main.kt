package com.lanbridge.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lanbridge.ui.LanBridgeRoot
import com.lanbridge.viewmodel.LanBridgeViewModel

fun main() = application {
    val viewModel = LanBridgeViewModel()
    Window(
        onCloseRequest = ::exitApplication,
        title = "LanBridge"
    ) {
        LanBridgeRoot(viewModel = viewModel)
    }
}
