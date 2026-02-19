package com.lanbridge.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lanbridge.ui.LanBridgeRoot
import com.lanbridge.viewmodel.LanBridgeViewModel
import javax.swing.JFileChooser

fun main() = application {
    val viewModel = LanBridgeViewModel()
    Window(
        onCloseRequest = {
            viewModel.close()
            exitApplication()
        },
        title = "LanBridge"
    ) {
        DisposableEffect(Unit) {
            onDispose { viewModel.close() }
        }

        LanBridgeRoot(
            viewModel = viewModel,
            onPickFileForDevice = { _, onPicked ->
                val chooser = JFileChooser()
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    onPicked(chooser.selectedFile.absolutePath)
                } else {
                    onPicked(null)
                }
            }
        )
    }
}
