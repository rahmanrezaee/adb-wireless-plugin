package com.adbwireless.dialogs

import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

/**
 * Fixed Modern Device Dialog
 */
class UnifiedDeviceDialog(
    private val project: Project,
    private val existingDevice: Device? = null,
    private val onDeviceChanged: (Device) -> Unit
) : DialogWrapper(project) {

    private val adbService = project.service<ADBService>()
    private val settingsService = SettingsService.getInstance()

    private val isEditMode = existingDevice != null

    // UI Components with better sizing
    private val deviceNameField = JBTextField().apply {
        preferredSize = Dimension(400, 32)
        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
    }

    private val deviceIpField = JBTextField().apply {
        preferredSize = Dimension(400, 32)
        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
    }

    private val connectionPortField = JBTextField("5555").apply {
        preferredSize = Dimension(120, 32)
        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
    }

    private val pairingPortField = JBTextField().apply {
        preferredSize = Dimension(120, 32)
        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
    }

    private val pairingCodeField = JBTextField().apply {
        preferredSize = Dimension(120, 32)
        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
    }

    private val debugOutput = JBTextArea().apply {
        isEditable = false
        rows = 8
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        text = if (isEditMode) "Device management output...\n" else "Pairing debug output...\n"
        background = JBColor.namedColor("Editor.background", UIUtil.getTextFieldBackground())
    }

    // Dynamic UI components
    private lateinit var pairStatusLabel: JLabel

    // Action buttons
    private lateinit var pairDeviceAction: AbstractAction
    private lateinit var connectAction: AbstractAction
    private lateinit var saveAction: AbstractAction

    // State tracking
    private var devicePaired = false
    private var deviceConnected = false

    init {
        title = if (isEditMode) "Manage Device: ${existingDevice?.name}" else "Add New Device"

        // Initialize fields if editing
        existingDevice?.let { device ->
            deviceNameField.text = device.name
            deviceIpField.text = device.ip
            connectionPortField.text = device.port

            // Check if device is paired/connected
            checkDeviceStatus(device)
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            // Device Information Section
            group("Device Information") {
                row("Device Name:") {
                    cell(deviceNameField)
                        .align(AlignX.FILL)
                        .apply {
                            if (!isEditMode) focused()
                        }
                        .comment("Enter a friendly name for your device")
                }.layout(RowLayout.PARENT_GRID)

                row("IP Address:") {
                    cell(deviceIpField)
                        .align(AlignX.FILL)
                        .apply {
                            if (isEditMode) focused()
                        }
                        .comment("Device IP from Android Wireless Debugging settings")
                }.layout(RowLayout.PARENT_GRID)

                row("Connection Port:") {
                    cell(connectionPortField)
                        .comment("Usually 5555 for wireless ADB connections")
                }.layout(RowLayout.PARENT_GRID)
            }

            // Spacing between sections
            separator()

            // Pairing Section
            group("Device Pairing") {
                row {
                    pairStatusLabel = label(if (devicePaired) "✅ Device is paired and ready" else "⚠️ Device needs pairing").component.apply {
                        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.BOLD)
                        foreground = if (devicePaired) JBColor.GREEN else JBColor.ORANGE
                    }
                }

                row("Pairing Port:") {
                    cell(pairingPortField)
                        .comment("Port from 'Pair device with pairing code' dialog (NOT the connection port)")
                        .enabled(!devicePaired)
                }.layout(RowLayout.PARENT_GRID)

                row("Pairing Code:") {
                    cell(pairingCodeField)
                        .comment("6-digit code displayed on your Android device")
                        .enabled(!devicePaired)
                }.layout(RowLayout.PARENT_GRID)

                // Show Re-pair button if already paired
                if (devicePaired) {
                    row {
                        button("Re-pair Device") {
                            repairDevice()
                        }.apply {
                            component.toolTipText = "Re-pair this device if you're experiencing connection issues"
                        }
                    }
                }
            }

            // Spacing
            separator()

            // Debug Output Section
            group("Debug & Activity Log") {
                row {
                    scrollCell(debugOutput)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            // Spacing
            separator()

            // Instructions Section
            group("Setup Instructions") {
                row {
                    text(getPairInstructions()).apply {
                        component.foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                    }
                }
            }
        }.apply {
            preferredSize = Dimension(600, 700)
        }
    }

    override fun createActions(): Array<Action> {
        // Create actions with proper initial states
        pairDeviceAction = object : AbstractAction("Pair Device") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                pairDevice()
            }
        }.apply {
            isEnabled = !devicePaired
        }

        connectAction = object : AbstractAction(getConnectButtonText()) {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                handleConnectionAction()
            }
        }.apply {
            isEnabled = devicePaired
        }

        saveAction = object : AbstractAction("Save Device") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                saveDevice()
            }
        }.apply {
            isEnabled = deviceConnected || isEditMode
        }

        return if (devicePaired) {
            arrayOf(connectAction, saveAction, cancelAction)
        } else {
            arrayOf(pairDeviceAction, connectAction, saveAction, cancelAction)
        }
    }

    private fun checkDeviceStatus(device: Device) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Checking Device Status", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val devicesResult = adbService.listDevices()
                    val isConnected = devicesResult.success &&
                            devicesResult.output.contains("${device.ip}:${device.port}") &&
                            devicesResult.output.contains("device")

                    ApplicationManager.getApplication().invokeLater {
                        deviceConnected = isConnected
                        devicePaired = true // Assume paired if it's a saved device

                        updateUI()
                        addDebug("📊 Device status checked:")
                        addDebug("  Paired: $devicePaired")
                        addDebug("  Connected: $deviceConnected")
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        addDebug("❌ Error checking device status: ${e.message}")
                    }
                }
            }
        })
    }

    private fun updateUI() {
        SwingUtilities.invokeLater {
            // Update pair status
            pairStatusLabel.text = if (devicePaired) "✅ Device is paired and ready" else "⚠️ Device needs pairing"
            pairStatusLabel.foreground = if (devicePaired) JBColor.GREEN else JBColor.ORANGE

            // Enable/disable pair fields
            pairingPortField.isEnabled = !devicePaired
            pairingCodeField.isEnabled = !devicePaired

            // Update button states
            pairDeviceAction.isEnabled = !devicePaired
            connectAction.isEnabled = devicePaired
            connectAction.putValue(Action.NAME, getConnectButtonText())
            saveAction.isEnabled = deviceConnected || isEditMode

            // Clear pair fields if paired
            if (devicePaired) {
                pairingPortField.text = ""
                pairingCodeField.text = ""
            }
        }
    }

    private fun getConnectButtonText(): String {
        return when {
            deviceConnected -> "Disconnect"
            devicePaired -> "Connect"
            else -> "Connect (Pair First)"
        }
    }

    private fun getPairInstructions(): String {
        return if (devicePaired) {
            """
            ✅ Your device is successfully paired!
            
            📱 Next Steps:
            • Click "Connect" to establish ADB connection
            • Click "Save Device" to add to your device list
            
            🔧 Troubleshooting:
            • If connection fails, click "Re-pair Device"
            • Ensure both devices are on the same WiFi network
            """.trimIndent()
        } else {
            """
            📱 To pair your Android device:
            
            1️⃣ Open Settings → Developer Options → Wireless Debugging
            2️⃣ Tap "Pair device with pairing code"
            3️⃣ Enter the PORT and CODE shown in the pairing dialog
            4️⃣ Click "Pair Device" below
            
            💡 Make sure both devices are on the same WiFi network
            """.trimIndent()
        }
    }

    private fun pairDevice() {
        val name = deviceNameField.text.trim()
        val ip = deviceIpField.text.trim()
        val port = pairingPortField.text.trim()
        val code = pairingCodeField.text.trim()

        // Validate inputs
        if (name.isEmpty() || ip.isEmpty() || port.isEmpty() || code.isEmpty()) {
            Messages.showErrorDialog(project, "Please fill in all required fields", "Validation Error")
            return
        }

        if (code.length != 6 || !code.all { it.isDigit() }) {
            Messages.showErrorDialog(project, "Pairing code must be exactly 6 digits", "Invalid Code")
            return
        }

        addDebug("🚀 Starting pairing process...")
        addDebug("📡 Executing: adb pair $ip:$port $code")

        pairDeviceAction.isEnabled = false

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Pairing Device", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Pairing with device..."
                    val result = adbService.pairDevice(ip, port, code)

                    ApplicationManager.getApplication().invokeLater {
                        addDebug("📊 Pairing completed:")
                        addDebug("  Success: ${result.success}")
                        addDebug("  Output: '${result.output}'")
                        if (result.error.isNotEmpty()) {
                            addDebug("  Error: '${result.error}'")
                        }

                        if (result.success) {
                            addDebug("🎉 PAIRING SUCCESSFUL!")
                            addDebug("💡 You can now connect to your device")
                            devicePaired = true
                            updateUI()

                            Messages.showInfoMessage(project, "Device paired successfully!\nYou can now connect to it.", "Pairing Success")
                        } else {
                            addDebug("❌ PAIRING FAILED!")
                            val errorMsg = when {
                                result.error.contains("failed to authenticate") -> "Wrong pairing code"
                                result.error.contains("connection refused") -> "Check IP address and pairing port"
                                result.error.contains("cannot connect to daemon") -> "ADB server issue (should auto-start)"
                                else -> "Pairing failed - check debug output for details"
                            }
                            addDebug("🔍 Error: $errorMsg")
                            Messages.showErrorDialog(project, errorMsg, "Pairing Failed")
                            pairDeviceAction.isEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        addDebug("💥 Exception: ${e.message}")
                        Messages.showErrorDialog(project, "Error: ${e.message}", "Pairing Error")
                        pairDeviceAction.isEnabled = true
                    }
                }
            }
        })
    }

    private fun repairDevice() {
        val ip = deviceIpField.text.trim()
        if (ip.isEmpty()) {
            Messages.showErrorDialog(project, "Please enter IP address first", "Validation Error")
            return
        }

        devicePaired = false
        deviceConnected = false
        updateUI()

        addDebug("🔄 Re-pairing mode activated")
        addDebug("💡 Enter new pairing port and code, then click 'Pair Device'")
    }

    private fun handleConnectionAction() {
        val name = deviceNameField.text.trim()
        val ip = deviceIpField.text.trim()
        val connectionPort = connectionPortField.text.trim()

        if (name.isEmpty() || ip.isEmpty() || connectionPort.isEmpty()) {
            Messages.showErrorDialog(project, "Please fill in device name, IP, and connection port", "Validation Error")
            return
        }

        if (!devicePaired) {
            Messages.showWarningDialog(project, "Please pair the device first", "Device Not Paired")
            return
        }

        val device = Device(name, ip, connectionPort)
        connectAction.isEnabled = false

        if (deviceConnected) {
            // Disconnect
            addDebug("🔌 Disconnecting from ${device.getConnectAddress()}...")

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Disconnecting", false) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val result = adbService.disconnectDevice(device)
                        ApplicationManager.getApplication().invokeLater {
                            if (result.success) {
                                addDebug("✅ Disconnected successfully")
                                deviceConnected = false
                                updateUI()
                            } else {
                                addDebug("❌ Disconnect failed: ${result.error}")
                                Messages.showErrorDialog(project, "Disconnect failed: ${result.error}", "Error")
                            }
                            connectAction.isEnabled = true
                        }
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            addDebug("💥 Disconnect error: ${e.message}")
                            Messages.showErrorDialog(project, "Error: ${e.message}", "Disconnect Error")
                            connectAction.isEnabled = true
                        }
                    }
                }
            })
        } else {
            // Connect
            addDebug("🔗 Connecting to ${device.getConnectAddress()}...")

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Connecting", false) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val result = adbService.connectDevice(device)
                        ApplicationManager.getApplication().invokeLater {
                            if (result.success) {
                                addDebug("✅ Connected successfully")
                                addDebug("💡 You can now save this device to your list")
                                deviceConnected = true
                                updateUI()
                            } else {
                                addDebug("❌ Connect failed: ${result.error}")
                                val errorMsg = when {
                                    result.output.contains("failed to connect") -> "Check connection port and network"
                                    result.output.contains("Connection refused") -> "Device not accepting connections"
                                    else -> result.error
                                }
                                Messages.showErrorDialog(project, "Connect failed: $errorMsg", "Error")
                            }
                            connectAction.isEnabled = true
                        }
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            addDebug("💥 Connect error: ${e.message}")
                            Messages.showErrorDialog(project, "Error: ${e.message}", "Connect Error")
                            connectAction.isEnabled = true
                        }
                    }
                }
            })
        }
    }

    private fun saveDevice() {
        val name = deviceNameField.text.trim()
        val ip = deviceIpField.text.trim()
        val connectionPort = connectionPortField.text.trim()

        if (name.isEmpty() || ip.isEmpty() || connectionPort.isEmpty()) {
            Messages.showErrorDialog(project, "Please fill in device name, IP, and connection port", "Validation Error")
            return
        }

        // For new devices, require connection first (unless editing)
        if (!isEditMode && !deviceConnected) {
            Messages.showWarningDialog(project, "Please connect to the device first", "Device Not Connected")
            return
        }

        // Remove old device if editing
        existingDevice?.let { old ->
            settingsService.removeDevice(old.ip)
        }

        // Save new/updated device
        val device = Device(name, ip, connectionPort)
        settingsService.saveDevice(device)

        addDebug("💾 Device saved: ${device.name} (${device.getConnectAddress()})")

        Messages.showInfoMessage(project, "Device '${device.name}' saved successfully!", "Device Saved")

        onDeviceChanged(device)
        close(OK_EXIT_CODE)
    }

    private fun addDebug(message: String) {
        SwingUtilities.invokeLater {
            val timestamp = java.time.LocalTime.now().toString().substring(0, 8)
            debugOutput.append("[$timestamp] $message\n")
            debugOutput.caretPosition = debugOutput.document.length
        }
    }
}