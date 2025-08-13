package com.adbwireless.actions

import com.adbwireless.dialogs.GenerateQRCodeDialog
import com.adbwireless.services.SettingsService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Action to generate QR code for wireless debugging
 */
class GenerateQRCodeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val settingsService = SettingsService.getInstance()
        val mostRecentDevice = settingsService.getMostRecentDevice()

        if (mostRecentDevice != null) {
            // Generate QR code for the most recent device
            val dialog = GenerateQRCodeDialog(project, mostRecentDevice.ip, mostRecentDevice.defaultPort)
            dialog.show()
        } else {
            // No saved devices, ask user to enter IP
            val ipAddress = Messages.showInputDialog(
                project,
                "Enter the IP address of your Android device:",
                "Generate QR Code",
                Messages.getQuestionIcon(),
                "",
                null
            )

            if (ipAddress != null && ipAddress.isNotBlank()) {
                val dialog = GenerateQRCodeDialog(project, ipAddress.trim(), "5555")
                dialog.show()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
