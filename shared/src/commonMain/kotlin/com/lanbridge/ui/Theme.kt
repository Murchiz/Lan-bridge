package com.lanbridge.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

fun lightColorSchemeLanBridge() = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF006A67),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondary = androidx.compose.ui.graphics.Color(0xFF4B635F),
    background = androidx.compose.ui.graphics.Color(0xFFF4FBF9),
    surface = androidx.compose.ui.graphics.Color(0xFFF4FBF9)
)

fun darkColorSchemeLanBridge() = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF4FDAD4),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003736),
    secondary = androidx.compose.ui.graphics.Color(0xFFB2CCC6),
    background = androidx.compose.ui.graphics.Color(0xFF0F1514),
    surface = androidx.compose.ui.graphics.Color(0xFF0F1514)
)
