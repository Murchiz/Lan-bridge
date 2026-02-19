package com.lanbridge.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.lanbridge.network.AndroidContextHolder
import com.lanbridge.ui.LanBridgeRoot
import com.lanbridge.viewmodel.LanBridgeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel = LanBridgeViewModel()
    private var pendingPickerCallback: ((String?) -> Unit)? = null
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.appContext = applicationContext

        openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val callback = pendingPickerCallback
            pendingPickerCallback = null
            callback?.invoke(uri?.toString())
        }

        setContent {
            LanBridgeRoot(
                viewModel = viewModel,
                onPickFileForDevice = { _, onPicked ->
                    pendingPickerCallback = onPicked
                    openDocumentLauncher.launch(arrayOf("*/*"))
                }
            )
        }
    }

    override fun onDestroy() {
        viewModel.close()
        super.onDestroy()
    }
}
