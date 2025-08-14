package com.adbwireless.dialogs

import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import javax.swing.Action
import kotlinx.coroutines.*
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.AbstractAction

/**
 * Edit device dialog with re-pair option
 */
class EditDeviceDialog(
    private val project: Project,
    private val device: Device,
    private val onDeviceUpdated: (Device) -> Unit
) : DialogWrapper(project) {

    private val adbService = project.service<ADBService>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val deviceNameField = JBTextField(device.name)
    private val deviceIpField = JBTextField(device.ip)
    private val devicePortField = JBTextField(device.port)

    init {
        title = "Edit Device: ${device.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("Device Information") {
                row("Device Name:") {
                    cell(deviceNameField)
                        .align(AlignX.FILL)
                        .focused()
                }
                row("IP Address:") {
                    cell(deviceIpField)
                        .align(AlignX.FILL)
                }
                row("Port:") {
                    cell(devicePortField)
                        .comment("Usually 5555 for wireless ADB")
                }
            }

            group("Re-pairing") {
                row {
                    button("Re-pair Device") {
                        repairDevice()
                    }.apply {
                        component.toolTipText = "Re-pair this device if connection issues occur"
                    }
                }
                row {
                    text("Use re-pairing if you get connection errors or 'unauthorized' messages.")
                }
            }
        }.apply {
            preferredSize = Dimension(400, 200)
            border = JBUI.Borders.empty(16)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("Save Changes") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    saveChanges()
                }
            },
            cancelAction
        )
    }

    private fun saveChanges() {
        val name = deviceNameField.text.trim()
        val ip = deviceIpField.text.trim()
        val port = devicePortField.text.trim()

        if (name.isEmpty() || ip.isEmpty() || port.isEmpty()) {
            Messages.showErrorDialog(project, "Please fill in all fields", "Validation Error")
            return
        }

        // Remove old device and add updated one
        val settingsService = SettingsService.getInstance()
        settingsService.removeDevice(device.ip)

        val updatedDevice = Device(name, ip, port)
        settingsService.saveDevice(updatedDevice)

        onDeviceUpdated(updatedDevice)
        super.doOKAction()
    }

    private fun repairDevice() {
        val ip = deviceIpField.text.trim()

        if (ip.isEmpty()) {
            Messages.showErrorDialog(project, "Please enter IP address first", "Validation Error")
            return
        }

        // Create a simple re-pair dialog with both port and code fields
        val repairDialog = RepairDeviceDialog(project, ip) { port, code ->
            scope.launch {
                try {
                    val result = adbService.pairDevice(ip, port, code)

                    SwingUtilities.invokeLater {
                        if (result.success) {
                            Messages.showInfoMessage(
                                project,
                                "Device re-paired successfully! You can now connect to it.",
                                "Re-pairing Successful"
                            )
                        } else {
                            val errorMsg = when {
                                result.error.contains("failed to authenticate") ->
                                    "Wrong pairing code. Please check and try again."
                                result.error.contains("connection refused") ->
                                    "Connection refused. Check IP address and pairing port."
                                result.error.contains("already paired") ->
                                    "Device already paired! No need to re-pair."
                                else ->
                                    "Re-pairing failed: ${result.error.ifEmpty { result.output }}\n\nFull output: ${result.fullOutput}"
                            }
                            Messages.showErrorDialog(project, errorMsg, "Re-pairing Failed")
                        }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Error during re-pairing: ${e.message}",
                            "Re-pairing Error"
                        )
                    }
                }
            }
        }
        repairDialog.show()
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}