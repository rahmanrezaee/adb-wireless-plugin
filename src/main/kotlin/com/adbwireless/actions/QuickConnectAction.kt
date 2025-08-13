package com.adbwireless.actions

import com.adbwireless.services.SettingsService
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Enhanced Quick Connect action with modern notifications and better UX
 */
class QuickConnectAction : AnAction(
    "Quick Connect to Wireless Device",
    "Open ADB Wireless Manager and connect to the most recent device",
    AllIcons.Actions.Lightning
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get the ADB Wireless tool window
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ADB Wireless")

        if (toolWindow != null) {
            // Show the tool window first
            toolWindow.show {
                handleQuickConnect(project)
            }
        } else {
            showErrorNotification(
                "ADB Wireless tool window not found. Please ensure the plugin is properly installed.",
                project
            )
        }
    }

    private fun handleQuickConnect(project: com.intellij.openapi.project.Project) {
        val settings = SettingsService.getInstance()
        val recentDevice = settings.getMostRecentDevice()

        if (recentDevice != null) {
            val timeAgo = formatLastConnected(recentDevice.lastConnected)

            showSuccessNotification(
                "ADB Wireless Manager",
                "Tool window opened. Most recent device: ${recentDevice.name} (${recentDevice.ip}) - Last used $timeAgo",
                project
            )

            // Show informative dialog
            Messages.showInfoMessage(
                project,
                """
                Most Recent Device Found:
                
                Device: ${recentDevice.name}
                IP Address: ${recentDevice.ip}:${recentDevice.defaultPort}
                Last Connected: $timeAgo
                
                The device is loaded in the Connect tab. 
                Switch to the Devices tab to see all saved devices.
                """.trimIndent(),
                "ADB Wireless Quick Connect"
            )

        } else {
            showInfoNotification(
                "No Saved Devices",
                "No devices found. Use the Connect tab to add and save your first device.",
                project
            )

            Messages.showInfoMessage(
                project,
                """
                No saved devices found.
                
                To get started:
                1. Go to the 'Connect' tab
                2. Enter your device name and IP address
                3. Complete the pairing process
                4. Click 'Save Device' for future quick access
                
                💡 Tip: Enable 'Wireless debugging' on your Android device first
                (Settings → Developer Options → Wireless debugging)
                """.trimIndent(),
                "ADB Wireless - Getting Started"
            )
        }
    }

    /**
     * Format timestamp to human-readable string with enhanced precision
     */
    private fun formatLastConnected(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 30_000 -> "just now"
            diff < 60_000 -> "less than a minute ago"
            diff < 3_600_000 -> "${diff / 60_000} minutes ago"
            diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
            diff < 604_800_000 -> "${diff / 86_400_000} days ago"
            else -> "${diff / 604_800_000} weeks ago"
        }
    }

    /**
     * Show success notification
     */
    private fun showSuccessNotification(title: String, content: String, project: com.intellij.openapi.project.Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ADB Wireless Manager")
            .createNotification(title, content, NotificationType.INFORMATION)
            .setIcon(AllIcons.General.InspectionsOK)
            .notify(project)
    }

    /**
     * Show info notification
     */
    private fun showInfoNotification(title: String, content: String, project: com.intellij.openapi.project.Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ADB Wireless Manager")
            .createNotification(title, content, NotificationType.INFORMATION)
            .setIcon(AllIcons.General.Information)
            .notify(project)
    }

    /**
     * Show error notification
     */
    private fun showErrorNotification(content: String, project: com.intellij.openapi.project.Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ADB Wireless Manager")
            .createNotification("ADB Wireless Error", content, NotificationType.ERROR)
            .setIcon(AllIcons.General.Error)
            .notify(project)
    }

    /**
     * Action is always enabled when a project is open
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}