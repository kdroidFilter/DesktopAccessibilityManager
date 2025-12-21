# Desktop Accessibility Announcer (JNA)

A JVM-only helper that provides a Compose Desktop equivalent of
`LocalAccessibilityManager.announceForAccessibility()` using JNA.

## Modules

- `accessibility`: core API with JNA, no Compose dependency.
- `accessibilitycompose`: Compose-only helpers (CompositionLocal).
- `demo-compose`: Compose Desktop demo using the CompositionLocal.
- `demo-swing`: Swing demo without Compose.

## API

```kotlin
import io.github.kdroidfilter.accessibility.AnnouncementPriority
import io.github.kdroidfilter.accessibility.DesktopAccessibilityManager

DesktopAccessibilityManager.announceForAccessibility(
    "Error: Your changes could not be saved.",
    AnnouncementPriority.ASSERTIVE,
)
```

In Compose, use the `accessibilitycompose` module and the CompositionLocal:

```kotlin
import io.github.kdroidfilter.accessibility.compose.LocalDesktopAccessibilityManager

val accessibility = LocalDesktopAccessibilityManager.current
accessibility.announceForAccessibility("Error: Invalid email address.")
```

In Swing (no Compose dependency):

```kotlin
import io.github.kdroidfilter.accessibility.AnnouncementPriority
import io.github.kdroidfilter.accessibility.DesktopAccessibilityManager

DesktopAccessibilityManager.announceForAccessibility(
    "Saved successfully.",
    AnnouncementPriority.POLITE,
)
```

## Demos

- Compose demo: `./gradlew :demo-compose:run`
- Swing demo: `./gradlew :demo-swing:run`

## Behavior by platform

- macOS: Uses `NSAccessibilityPostNotificationWithUserInfo` with
  `NSAccessibilityAnnouncementRequestedNotification`.
- Windows: Uses the NVDA/JAWS controller APIs when available.

The API returns `true` when a native announcement request was successfully
submitted. It returns `false` if the platform doesn't support the request or
if the native call fails.

## Usage notes

- Announcements are most reliable when your app window is in the foreground.
- On macOS, VoiceOver must be enabled.
- On Windows, a screen reader (NVDA/JAWS) must be running.
- Enable `allowDesktopAccessibilityManagerLogging` to see library logs.
- `AnnouncementPriority.ASSERTIVE` is for urgent messages (errors, blockers).
  It may interrupt or flush current speech output on supported screen readers.
- `AnnouncementPriority.POLITE` is for non-urgent updates. It should be queued
  or spoken after the current speech finishes, when supported.
- Priority mapping on macOS is best-effort and depends on OS version and
  VoiceOver configuration.

## Manual test checklist

macOS (VoiceOver):
1. Enable VoiceOver (System Settings -> Accessibility -> VoiceOver).
2. Run the desktop demo app and focus its window.
3. Click the \"Announce error\" button.
4. Confirm VoiceOver speaks the error message.

Windows (NVDA/JAWS):
1. Start NVDA or JAWS.
2. Run the desktop demo app and focus its window.
3. Click the \"Announce error\" button.
4. Confirm the screen reader speaks the error message.
