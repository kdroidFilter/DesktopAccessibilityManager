package io.github.kdroidfilter.accessibility.platform

import com.sun.jna.Platform
import io.github.kdroidfilter.accessibility.AnnouncementPriority

internal interface PlatformAnnouncer {
    val isSupported: Boolean
    fun announce(message: String, priority: AnnouncementPriority): Boolean
}

internal object PlatformAnnouncerFactory {
    fun create(): PlatformAnnouncer {
        return when {
            Platform.isMac() -> createMac()
            Platform.isWindows() -> createWindows()
            else -> NoOpAnnouncer
        }
    }

    private fun createMac(): PlatformAnnouncer =
        runCatching { MacAccessibilityAnnouncer() }.getOrElse { NoOpAnnouncer }

    private fun createWindows(): PlatformAnnouncer =
        runCatching { WindowsAccessibilityAnnouncer() }.getOrElse { NoOpAnnouncer }
}

internal object NoOpAnnouncer : PlatformAnnouncer {
    override val isSupported: Boolean = false

    override fun announce(message: String, priority: AnnouncementPriority): Boolean = false
}
