package io.github.kdroidfilter.accessibility.compose

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.kdroidfilter.accessibility.DesktopAccessibilityManager

/**
 * CompositionLocal entry point for desktop accessibility announcements.
 */
val LocalDesktopAccessibilityManager = staticCompositionLocalOf { DesktopAccessibilityManager }
