package com.adbwireless.dialogs

import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.adbwireless.services.QRCodeService
import com.adbwireless.dialogs.GenerateQRCodeDialog
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
 * Dialog for adding new devices with QR code or manual pairing
 */
class AddDeviceDialog(
    private val project: Project,
    private val onDeviceAdded: (Device) -> Unit
) : DialogWrapper(project) {

    private val adbService = project.service<ADBService>()
    private val settingsService = SettingsService.getInstance()
    private val qrCodeService = project.service<QRCodeService>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI Components
    private val deviceNameField = JBTextField().apply {
        emptyText.text = "Enter device name (e.g., Galaxy S23)"
    }

    private val deviceIpField = JBTextField().apply {
        emptyText.text = "192.168.1.xxx"
    }

    // Manual pairing components
    private val manualPairingPanel = JPanel()
    private val pairingPortField = JBTextField().apply {
        emptyText.text = "Pairing port"
        columns = 8
    }
    private val pairingCodeField = JBTextField().apply {
        emptyText.text = "6-digit code"
        columns = 8
    }

    // QR code components
    private val qrCodePanel = JPanel()
    private val qrCodeField = JBTextArea(3, 30).apply {
        emptyText.text = "Paste QR code content here..."
        lineWrap = true
        wrapStyleWord = true
    }

    private val pairingMethodTabs = JBTabbedPane()
    private val statusLabel = JLabel(" ")
    private var isOperationInProgress = false

    init {
        title = "Add New Wireless Device"
        init()
        setupUI()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("Device Information") {
                row("Device Name:") {
                    cell(deviceNameField)
                        .align(AlignX.FILL)
                        .comment("Give your device a memorable name")
                }
                row("IP Address:") {
                    cell(deviceIpField)
                        .align(AlignX.FILL)
                        .comment("From Android Settings → Developer Options → Wireless debugging")
                }
                row {
                    button("Generate QR Code for This Device") {
                        generateQRCodeForDevice()
                    }.apply {
                        component.icon = AllIcons.Actions.Preview
                    }
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
            preferredSize = Dimension(500, 400)
            border = JBUI.Borders.empty(16)
        }
    }

    private fun setupUI() {
        // Setup manual pairing panel
        manualPairingPanel.layout = BorderLayout()
        val manualPanel = panel {
            group("Manual Pairing") {
                row("Pairing Port:") {
                    cell(pairingPortField)
                        .comment("Temporary port from 'Pair device with pairing code'")
                }
                row("Pairing Code:") {
                    cell(pairingCodeField)
                        .comment("6-digit code displayed on your Android device")
                }
                row {
                    button("Pair Device") {
                        pairDeviceManually()
                    }.apply {
                        component.icon = AllIcons.General.Web
                    }
                }
            }

            group("Instructions") {
                row {
                    text("""
                        1. Go to Settings → Developer Options → Wireless debugging
                        2. Tap "Pair device with pairing code"
                        3. Enter the port and code shown on your device
                        4. Click "Pair Device"
                    """.trimIndent())
                }
            }
        }
        manualPairingPanel.add(manualPanel, BorderLayout.CENTER)

        // Setup QR code panel
        qrCodePanel.layout = BorderLayout()
        val qrPanel = panel {
            group("QR Code Pairing") {
                row("QR Code Content:") {
                    scrollCell(qrCodeField)
                        .align(Align.FILL)
                        .comment("Scan or copy the QR code content from your device")
                }
                row {
                    button("Pair with QR Code") {
                        pairDeviceWithQR()
                    }.apply {
                        component.icon = AllIcons.General.Web
                    }
                    button("Scan QR Code") {
                        scanQRCode()
                    }.apply {
                        component.icon = AllIcons.Actions.Preview
                    }
                }
            }

            group("Instructions") {
                row {
                    text("""
                        1. Go to Settings → Developer Options → Wireless debugging
                        2. Tap "Pair device with QR code"
                        3. Either scan the QR code or copy its content
                        4. Paste the content above and click "Pair with QR Code"
                    """.trimIndent())
                }
            }
        }
        qrCodePanel.add(qrPanel, BorderLayout.CENTER)

        // Add tabs
        pairingMethodTabs.addTab("Manual Pairing", AllIcons.General.Settings, manualPairingPanel)
        pairingMethodTabs.addTab("QR Code", AllIcons.Actions.Preview, qrCodePanel)
    }

    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }

    private fun pairDeviceManually() {
        val deviceName = deviceNameField.text.trim()
        val deviceIp = deviceIpField.text.trim()
        val pairingPort = pairingPortField.text.trim()
        val pairingCode = pairingCodeField.text.trim()

        if (!validateManualInput(deviceName, deviceIp, pairingPort, pairingCode)) return

        setOperationInProgress(true, "Pairing with device manually...")

        scope.launch {
            try {
                val result = adbService.pairDevice(deviceIp, pairingPort, pairingCode)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        updateStatus("✅ Pairing successful! You can now connect to this device.", false)

                        // Save device and close dialog
                        val device = Device(deviceName, deviceIp, "5555") // Default connection port
                        settingsService.saveDevice(device)
                        onDeviceAdded(device)
                        close(OK_EXIT_CODE)

                    } else {
                        updateStatus("❌ Pairing failed: ${result.error.ifEmpty { result.output }}", true)
                        showManualPairingTroubleshooting()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    updateStatus("❌ Error during pairing: ${e.message}", true)
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun pairDeviceWithQR() {
        val deviceName = deviceNameField.text.trim()
        val deviceIp = deviceIpField.text.trim()
        val qrContent = qrCodeField.text.trim()

        if (!validateQRInput(deviceName, deviceIp, qrContent)) return

        setOperationInProgress(true, "Pairing with QR code...")

        scope.launch {
            try {
                // Parse QR code content
                val qrData = parseQRCodeContent(qrContent)
                if (qrData == null) {
                    SwingUtilities.invokeLater {
                        updateStatus("❌ Invalid QR code format. Please check the content.", true)
                    }
                    return@launch
                }

                val result = adbService.pairDevice(qrData.ip, qrData.port, qrData.password)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        updateStatus("✅ QR pairing successful! You can now connect to this device.", false)

                        // Save device and close dialog
                        val device = Device(deviceName, deviceIp, "5555") // Default connection port
                        settingsService.saveDevice(device)
                        onDeviceAdded(device)
                        close(OK_EXIT_CODE)

                    } else {
                        updateStatus("❌ QR pairing failed: ${result.error.ifEmpty { result.output }}", true)
                        showQRPairingTroubleshooting()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    updateStatus("❌ Error during QR pairing: ${e.message}", true)
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun scanQRCode() {
        // Placeholder for QR code scanning functionality
        // In a real implementation, you might use a webcam or screen capture
        Messages.showInfoMessage(
            project,
            """
            QR Code Scanning Options:
            
            1. Use your phone's camera to scan the QR code
            2. Take a screenshot and use an online QR decoder
            3. Copy the QR code content manually from the debug settings
            
            The QR code contains connection information in this format:
            WIFI:T:ADB;S:devicename;P:password;H:ip:port;;
            """.trimIndent(),
            "QR Code Scanning Help"
        )
    }

    private fun generateQRCodeForDevice() {
        val deviceIp = deviceIpField.text.trim()
        if (deviceIp.isEmpty()) {
            updateStatus("❌ Please enter the device IP address first", true)
            return
        }

        val dialog = GenerateQRCodeDialog(project, deviceIp, "5555")
        dialog.show()
    }

    private fun validateManualInput(name: String, ip: String, port: String, code: String): Boolean {
        when {
            name.isEmpty() -> {
                updateStatus("❌ Please enter a device name", true)
                return false
            }
            ip.isEmpty() -> {
                updateStatus("❌ Please enter the device IP address", true)
                return false
            }
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

    private fun validateQRInput(name: String, ip: String, qrContent: String): Boolean {
        when {
            name.isEmpty() -> {
                updateStatus("❌ Please enter a device name", true)
                return false
            }
            ip.isEmpty() -> {
                updateStatus("❌ Please enter the device IP address", true)
                return false
            }
            qrContent.isEmpty() -> {
                updateStatus("❌ Please paste the QR code content", true)
                return false
            }
        }
        return true
    }

    private fun parseQRCodeContent(qrContent: String): QRData? {
        val qrData = qrCodeService.parseQRCodeContent(qrContent)
        return qrData?.let { QRData(it.ip, it.port, it.password) }
    }

    private fun showManualPairingTroubleshooting() {
        SwingUtilities.invokeLater {
            Messages.showInfoMessage(
                project,
                """
                Manual Pairing Troubleshooting:
                
                • Ensure both devices are on the same WiFi network
                • Verify the pairing code is correct and not expired
                • Check that you're using the pairing port (not connection port)
                • Try generating a new pairing code on your device
                • Make sure Wireless debugging is enabled
                """.trimIndent(),
                "Pairing Help"
            )
        }
    }

    private fun showQRPairingTroubleshooting() {
        SwingUtilities.invokeLater {
            Messages.showInfoMessage(
                project,
                """
                QR Code Pairing Troubleshooting:
                
                • Ensure the QR code content is complete and unmodified
                • Verify both devices are on the same WiFi network
                • Check that Wireless debugging is enabled on your device
                • Make sure you copied the entire QR code text
                • Try using manual pairing instead
                """.trimIndent(),
                "QR Pairing Help"
            )
        }
    }

    private fun setOperationInProgress(inProgress: Boolean, message: String = "") {
        isOperationInProgress = inProgress
        SwingUtilities.invokeLater {
            isOKActionEnabled = !inProgress

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

    /**
     * Data class for parsed QR code information
     */
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