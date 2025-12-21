package io.github.kdroidfilter.demo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.accessibility.AnnouncementPriority
import io.github.kdroidfilter.accessibility.compose.LocalDesktopAccessibilityManager
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val accessibility = LocalDesktopAccessibilityManager.current
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = {
                accessibility.announceForAccessibility(
                    "Error: Something went wrong while saving your changes.",
                )
            }) {
                Text("Announce error")
            }
            Button(onClick = {
                accessibility.announceForAccessibility(
                    "Saved successfully.",
                    AnnouncementPriority.POLITE,
                )
            }) {
                Text("Announce success")
            }
        }
    }
}
