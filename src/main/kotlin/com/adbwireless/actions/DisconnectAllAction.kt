package com.adbwireless.actions

import com.adbwireless.services.ADBService
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*

/**
 * Action to disconnect all ADB devices directly from the menu
 */
class DisconnectAllAction : AnAction(
    "Disconnect All ADB Devices",
    "Disconnect all currently connected ADB devices",
    AllIcons.Actions.Suspend
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val adbService = project.service<ADBService>()

        // Confirm action
        val result = Messages.showYesNoDialog(
            project,
            "Disconnect all connected ADB devices?\n\nThis will terminate all active debugging sessions.",
            "Confirm Disconnect All",
            "Disconnect All",
            "Cancel",
            AllIcons.General.QuestionDialog
        )

        if (result == Messages.YES) {
            scope.launch {
                try {
                    val commandResult = adbService.disconnectAll()

                    if (commandResult.success) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("ADB Wireless Manager")
                            .createNotification(
                                "ADB Devices Disconnected",
                                "All ADB devices have been disconnected successfully.",
                                NotificationType.INFORMATION
                            )
                            .setIcon(AllIcons.General.InspectionsOK)
                            .notify(project)
                    } else {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("ADB Wireless Manager")
                            .createNotification(
                                "Disconnect Failed",
                                "Failed to disconnect devices: ${commandResult.error}",
                                NotificationType.ERROR
                            )
                            .setIcon(AllIcons.General.Error)
                            .notify(project)
                    }
                } catch (e: Exception) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("ADB Wireless Manager")
                        .createNotification(
                            "Disconnect Error",
                            "Error disconnecting devices: ${e.message}",
                            NotificationType.ERROR
                        )
                        .setIcon(AllIcons.General.Error)
                        .notify(project)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}