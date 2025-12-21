package io.github.kdroidfilter.accessibility

import io.github.kdroidfilter.accessibility.platform.PlatformAnnouncer
import io.github.kdroidfilter.accessibility.platform.PlatformAnnouncerFactory

/**
 * Priority hint for screen reader announcements.
 */
enum class AnnouncementPriority {
    POLITE,
    ASSERTIVE,
}

/**
 * Announces short messages to assistive technologies.
 */
interface AccessibilityAnnouncer {
    /**
     * Requests a screen reader announcement.
     *
     * @param message the text to announce
     * @param priority hint for how urgently to deliver the message
     * @return true if the native request was submitted, false otherwise
     */
    fun announceForAccessibility(
        message: String,
        priority: AnnouncementPriority = AnnouncementPriority.ASSERTIVE,
    ): Boolean
}

/**
 * Desktop accessibility announcer backed by JNA for macOS and Windows.
 *
 * Returns false if the platform does not support announcements or if the
 * native call fails.
 */
object DesktopAccessibilityManager : AccessibilityAnnouncer {
    private val announcer: PlatformAnnouncer = PlatformAnnouncerFactory.create()

    /**
     * True when this platform provides a native announcer implementation.
     */
    val isSupported: Boolean
        get() = announcer.isSupported

    override fun announceForAccessibility(message: String, priority: AnnouncementPriority): Boolean {
        val sanitized = message.trim()
        if (sanitized.isEmpty()) {
            return false
        }
        return announcer.announce(sanitized, priority)
    }
}
