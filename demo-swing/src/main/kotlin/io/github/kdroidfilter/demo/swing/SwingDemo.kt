package io.github.kdroidfilter.demo.swing

import io.github.kdroidfilter.accessibility.AnnouncementPriority
import io.github.kdroidfilter.accessibility.DesktopAccessibilityManager
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Accessibility demo (Swing)").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        }
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(16, 16, 16, 16)
        }
        val errorButton = JButton("Announce error").apply {
            alignmentX = Component.CENTER_ALIGNMENT
            addActionListener {
                DesktopAccessibilityManager.announceForAccessibility(
                    "Error: Something went wrong while saving your changes.",
                )
            }
        }
        val successButton = JButton("Announce success").apply {
            alignmentX = Component.CENTER_ALIGNMENT
            addActionListener {
                DesktopAccessibilityManager.announceForAccessibility(
                    "Saved successfully.",
                    AnnouncementPriority.POLITE,
                )
            }
        }
        panel.add(errorButton)
        panel.add(Box.createVerticalStrut(8))
        panel.add(successButton)
        frame.contentPane.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}
