package io.github.kdroidfilter.demo.compose

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.kdroidfilter.accessibility.tools.allowDesktopAccessibilityManagerLogging

fun main() = application {
    allowDesktopAccessibilityManagerLogging = true
    Window(
        onCloseRequest = ::exitApplication,
        title = "demo",
    ) {
        App()
    }
}
