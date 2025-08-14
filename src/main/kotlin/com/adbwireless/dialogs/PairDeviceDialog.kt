package com.adbwireless.dialogs

import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.AbstractAction
import javax.swing.Action

/**
 * Enhanced Pair Device Dialog with debug output
 */
class PairDeviceDialog(
    private val project: Project,
    private val onDeviceAdded: (Device) -> Unit
) : DialogWrapper(project) {

    private val adbService = project.service<ADBService>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val deviceNameField = JBTextField("My Phone")
    private val deviceIpField = JBTextField("172.16.1.105")
    private val pairingPortField = JBTextField("39819")
    private val pairingCodeField = JBTextField("176213")

    private val debugOutput = JBTextArea().apply {
        isEditable = false
        rows = 8
        font = Font("Monospaced", Font.PLAIN, 10)
        text = "Pairing debug output will appear here...\n"
    }

    init {
        title = "Pair New Device"
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
                        .comment("From Android wireless debugging settings")
                }
            }

            group("Pairing Information") {
                row("Pairing Port:") {
                    cell(pairingPortField)
                        .comment("Port from 'Pair device with pairing code' (NOT the main port)")
                }
                row("Pairing Code:") {
                    cell(pairingCodeField)
                        .comment("6-digit code shown on your Android device")
                }
            }

            group("Debug Output") {
                row {
                    scrollCell(debugOutput)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            group("Instructions") {
                row {
                    text("""
                        📱 On Android: Settings → Developer Options → Wireless debugging
                        1. Tap "Pair device with pairing code"
                        2. Enter the PORT and CODE shown above
                        3. Click "Pair Device" below
                    """.trimIndent())
                }
            }
        }.apply {
            preferredSize = Dimension(500, 500)
            border = JBUI.Borders.empty(16)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("Pair Device") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    pairDevice()
                }
            },
            cancelAction
        )
    }

    private fun pairDevice() {
        val name = deviceNameField.text.trim()
        val ip = deviceIpField.text.trim()
        val port = pairingPortField.text.trim()
        val code = pairingCodeField.text.trim()

        addDebug("🚀 Starting pairing process...")
        addDebug("Device: $name")
        addDebug("IP: $ip")
        addDebug("Port: $port")
        addDebug("Code: $code")

        // Validate inputs
        if (name.isEmpty() || ip.isEmpty() || port.isEmpty() || code.isEmpty()) {
            addDebug("❌ Validation failed - empty fields")
            Messages.showErrorDialog(project, "Please fill in all fields", "Validation Error")
            return
        }

        if (code.length != 6 || !code.all { it.isDigit() }) {
            addDebug("❌ Validation failed - invalid pairing code")
            Messages.showErrorDialog(project, "Pairing code must be exactly 6 digits", "Validation Error")
            return
        }

        addDebug("✅ Validation passed")
        addDebug("📡 Executing: adb pair $ip:$port $code")

        // Start pairing process
        isOKActionEnabled = false

        scope.launch {
            try {
                val result = adbService.pairDevice(ip, port, code)

                SwingUtilities.invokeLater {
                    addDebug("📊 Pairing result:")
                    addDebug("  Success: ${result.success}")
                    addDebug("  Output: '${result.output}'")
                    addDebug("  Error: '${result.error}'")
                    addDebug("  Full Output: '${result.fullOutput}'")

                    if (result.success) {
                        addDebug("🎉 PAIRING SUCCESSFUL!")

                        // Save device
                        val device = Device(name, ip, "5555") // Default connection port
                        SettingsService.getInstance().saveDevice(device)

                        Messages.showInfoMessage(
                            project,
                            "Device '$name' paired successfully!\nYou can now connect to it.",
                            "Pairing Successful"
                        )

                        onDeviceAdded(device)
                        close(OK_EXIT_CODE)

                    } else {
                        addDebug("❌ PAIRING FAILED!")

                        val errorMsg = when {
                            result.error.contains("failed to authenticate") ||
                                    result.output.contains("failed to authenticate") ->
                                "Wrong pairing code. Please check and try again."

                            result.error.contains("connection refused") ||
                                    result.output.contains("connection refused") ->
                                "Connection refused. Check IP address and pairing port."

                            result.error.contains("timeout") ||
                                    result.output.contains("timeout") ->
                                "Connection timeout. Check network connection."

                            result.error.contains("already paired") ||
                                    result.output.contains("already paired") ->
                                "Device already paired! You can try connecting directly."

                            else ->
                                "Pairing failed. Check debug output for details."
                        }

                        addDebug("Error analysis: $errorMsg")
                        Messages.showErrorDialog(project, errorMsg, "Pairing Failed")
                        isOKActionEnabled = true
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addDebug("💥 EXCEPTION: ${e.message}")
                    e.printStackTrace()
                    Messages.showErrorDialog(
                        project,
                        "Error during pairing: ${e.message}",
                        "Pairing Error"
                    )
                    isOKActionEnabled = true
                }
            }
        }
    }

    private fun addDebug(message: String) {
        SwingUtilities.invokeLater {
            val timestamp = java.time.LocalTime.now().toString().substring(0, 8)
            debugOutput.append("[$timestamp] $message\n")
            debugOutput.caretPosition = debugOutput.document.length
        }
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}