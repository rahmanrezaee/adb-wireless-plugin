package com.adbwireless.dialogs

import com.adbwireless.models.Device
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

/**
 * Dialog for editing device information
 */
class EditDeviceDialog(
    private val project: Project,
    private val device: Device,
    private val onDeviceUpdated: (Device) -> Unit
) : DialogWrapper(project) {

    // UI Components
    private val deviceNameField = JBTextField(device.name)
    private val deviceIpField = JBTextField(device.ip)
    private val connectionPortField = JBTextField(device.defaultPort)
    private val statusLabel = JLabel(" ")

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
                        .comment("Give your device a memorable name")
                        .focused()
                }
                row("IP Address:") {
                    cell(deviceIpField)
                        .align(AlignX.FILL)
                        .comment("Device IP address for connection")
                }
                row("Connection Port:") {
                    cell(connectionPortField)
                        .comment("Usually 5555, but may change after device restart")
                }
            }

            group("Status") {
                row {
                    cell(statusLabel)
                        .apply {
                            component.font = component.font.deriveFont(Font.ITALIC, 12f)
                        }
                }
            }
        }.apply {
            preferredSize = Dimension(400, 200)
            border = JBUI.Borders.empty(16)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction)
    }

    override fun doOKAction() {
        if (validateInput()) {
            val updatedDevice = Device(
                name = deviceNameField.text.trim(),
                ip = deviceIpField.text.trim(),
                defaultPort = connectionPortField.text.trim().ifEmpty { "5555" },
                lastConnected = device.lastConnected
            )
            onDeviceUpdated(updatedDevice)
            super.doOKAction()
        }
    }

    private fun validateInput(): Boolean {
        val name = deviceNameField.text.trim()
        val ip = deviceIpField.text.trim()
        val port = connectionPortField.text.trim()

        when {
            name.isEmpty() -> {
                updateStatus("❌ Please enter a device name", true)
                deviceNameField.requestFocus()
                return false
            }
            ip.isEmpty() -> {
                updateStatus("❌ Please enter the device IP address", true)
                deviceIpField.requestFocus()
                return false
            }
            port.isNotEmpty() && !port.all { it.isDigit() } -> {
                updateStatus("❌ Port must be a valid number", true)
                connectionPortField.requestFocus()
                return false
            }
            port.isNotEmpty() && (port.toIntOrNull() ?: 0) !in 1..65535 -> {
                updateStatus("❌ Port must be between 1 and 65535", true)
                connectionPortField.requestFocus()
                return false
            }
        }

        updateStatus("✅ Device information is valid", false)
        return true
    }

    private fun updateStatus(message: String, isError: Boolean = false) {
        SwingUtilities.invokeLater {
            statusLabel.text = message
            statusLabel.foreground = if (isError) {
                com.intellij.ui.JBColor.RED
            } else {
                com.intellij.ui.JBColor.GRAY
            }
        }
    }
}