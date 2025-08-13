package com.adbwireless.dialogs

import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.QRCodeService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.*

/**
 * Dialog for re-pairing an existing device
 */
class PairDeviceDialog(
    private val project: Project,
    private val device: Device,
    private val onPairingComplete: (Boolean) -> Unit
) : DialogWrapper(project) {

    private val adbService = project.service<ADBService>()
    private val qrCodeService = project.service<QRCodeService>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Manual pairing components
    private val pairingPortField = JBTextField().apply {
        emptyText.text = "Pairing port"
        columns = 8
    }
    private val pairingCodeField = JBTextField().apply {
        emptyText.text = "6-digit code"
        columns = 8
    }

    // QR code components
    private val qrCodeField = JBTextArea(3, 30).apply {
        emptyText.text = "Paste QR code content here..."
        lineWrap = true
        wrapStyleWord = true
    }

    private val pairingMethodTabs = JBTabbedPane()
    private val statusLabel = JLabel(" ")
    private var isOperationInProgress = false

    init {
        title = "Re-pair Device: ${device.name}"
        init()
        setupUI()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("Device Information") {
                row("Device:") {
                    label("${device.name} (${device.ip})")
                        .apply {
                            component.font = component.font.deriveFont(Font.BOLD)
                        }
                }
                row {
                    text("Re-pairing will establish a new trusted connection with this device.")
                }
            }

            group("Pairing Method") {
                row {
                    cell(pairingMethodTabs)
                        .align(Align.FILL)
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
            preferredSize = Dimension(450, 350)
            border = JBUI.Borders.empty(16)
        }
    }

    private fun setupUI() {
        // Setup manual pairing panel
        val manualPanel = panel {
            group("Manual Re-pairing") {
                row("Pairing Port:") {
                    cell(pairingPortField)
                        .comment("Temporary port from 'Pair device with pairing code'")
                }
                row("Pairing Code:") {
                    cell(pairingCodeField)
                        .comment("6-digit code displayed on your Android device")
                }
                row {
                    button("Re-pair Device") {
                        repairDeviceManually()
                    }.apply {
                        component.icon = AllIcons.General.Web
                    }
                }
            }
        }

        // Setup QR code panel
        val qrPanel = panel {
            group("QR Code Re-pairing") {
                row("QR Code Content:") {
                    scrollCell(qrCodeField)
                        .align(Align.FILL)
                        .comment("Scan or copy the QR code content from your device")
                }
                row {
                    button("Re-pair with QR Code") {
                        repairDeviceWithQR()
                    }.apply {
                        component.icon = AllIcons.General.Web
                    }
                }
            }
        }

        // Add tabs
        pairingMethodTabs.addTab("Manual", AllIcons.General.Settings, manualPanel)
        pairingMethodTabs.addTab("QR Code", AllIcons.Actions.Preview, qrPanel)
    }

    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }

    private fun repairDeviceManually() {
        val pairingPort = pairingPortField.text.trim()
        val pairingCode = pairingCodeField.text.trim()

        if (!validateManualInput(pairingPort, pairingCode)) return

        setOperationInProgress(true, "Re-pairing device...")

        scope.launch {
            try {
                val result = adbService.pairDevice(device.ip, pairingPort, pairingCode)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        updateStatus("✅ Re-pairing successful!", false)
                        onPairingComplete(true)
                        close(OK_EXIT_CODE)
                    } else {
                        updateStatus("❌ Re-pairing failed: ${result.error.ifEmpty { result.output }}", true)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    updateStatus("❌ Error during re-pairing: ${e.message}", true)
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun repairDeviceWithQR() {
        val qrContent = qrCodeField.text.trim()

        if (qrContent.isEmpty()) {
            updateStatus("❌ Please paste the QR code content", true)
            return
        }

        setOperationInProgress(true, "Re-pairing with QR code...")

        scope.launch {
            try {
                val qrData = parseQRCodeContent(qrContent)
                if (qrData == null) {
                    SwingUtilities.invokeLater {
                        updateStatus("❌ Invalid QR code format", true)
                    }
                    return@launch
                }

                val result = adbService.pairDevice(qrData.ip, qrData.port, qrData.password)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        updateStatus("✅ QR re-pairing successful!", false)
                        onPairingComplete(true)
                        close(OK_EXIT_CODE)
                    } else {
                        updateStatus("❌ QR re-pairing failed: ${result.error.ifEmpty { result.output }}", true)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    updateStatus("❌ Error during QR re-pairing: ${e.message}", true)
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun validateManualInput(port: String, code: String): Boolean {
        when {
            port.isEmpty() -> {
                updateStatus("❌ Please enter the pairing port", true)
                return false
            }
            code.isEmpty() -> {
                updateStatus("❌ Please enter the pairing code", true)
                return false
            }
            code.length != 6 || !code.all { it.isDigit() } -> {
                updateStatus("❌ Pairing code must be exactly 6 digits", true)
                return false
            }
        }
        return true
    }

    private fun parseQRCodeContent(qrContent: String): QRData? {
        val qrData = qrCodeService.parseQRCodeContent(qrContent)
        return qrData?.let { QRData(it.ip, it.port, it.password) }
    }

    private fun setOperationInProgress(inProgress: Boolean, message: String = "") {
        isOperationInProgress = inProgress
        SwingUtilities.invokeLater {
            if (inProgress) {
                updateStatus("🔄 $message", false)
            }
        }
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

    private data class QRData(
        val ip: String,
        val port: String,
        val password: String
    )

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}