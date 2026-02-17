package com.lanbridge.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lanbridge.ui.LanBridgeRoot
import com.lanbridge.viewmodel.LanBridgeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel = LanBridgeViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LanBridgeRoot(viewModel = viewModel)
        }
    }

    override fun onDestroy() {
        viewModel.close()
        super.onDestroy()
    }
}
