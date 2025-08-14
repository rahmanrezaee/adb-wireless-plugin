package com.adbwireless.toolWindow

import com.adbwireless.dialogs.EditDeviceDialog
import com.adbwireless.dialogs.PairDeviceDialog
import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.*

/**
 * Enhanced ADB Wireless Manager Tool Window with better error handling
 */
class ADBWirelessToolWindow(private val project: Project) {

    private val adbService = project.service<ADBService>()
    private val settingsService = SettingsService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI Components
    private val deviceListModel = DefaultListModel<Device>()
    private val deviceList = JBList(deviceListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = DeviceListRenderer()
    }

    private val statusLabel = JLabel("Ready")
    private val outputArea = JBTextArea().apply {
        isEditable = false
        rows = 10
        font = Font("Monospaced", Font.PLAIN, 11)
        text = "ADB Wireless Manager - Enhanced Version\n"
    }

    // Button references for enabling/disabling
    private lateinit var connectButton: JButton
    private lateinit var disconnectButton: JButton
    private lateinit var pairButton: JButton

    init {
        loadDevices()
        checkAdbStatus()
    }

    fun getContent(): JComponent {
        return panel {
            group("Device Management") {
                row {
                    pairButton = button("Pair New Device") {
                        showPairDeviceDialog()
                    }.apply {
                        component.icon = AllIcons.General.Web
                        component.preferredSize = Dimension(140, 30)
                    }.component

                    button("Edit Device") {
                        editSelectedDevice()
                    }.apply {
                        component.icon = AllIcons.Actions.Edit
                    }

                    button("Remove Device") {
                        removeSelectedDevice()
                    }.apply {
                        component.icon = AllIcons.General.Remove
                    }

                    button("Refresh List") {
                        loadDevices()
                    }.apply {
                        component.icon = AllIcons.Actions.Refresh
                    }
                }

                row {
                    scrollCell(deviceList)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            group("Connection Actions") {
                row {
                    connectButton = button("Connect") {
                        connectToSelectedDevice()
                    }.apply {
                        component.icon = AllIcons.Actions.Execute
                        component.preferredSize = Dimension(100, 30)
                    }.component

                    disconnectButton = button("Disconnect") {
                        disconnectFromSelectedDevice()
                    }.apply {
                        component.icon = AllIcons.Actions.Suspend
                        component.preferredSize = Dimension(100, 30)
                    }.component

                    button("List Connected") {
                        listConnectedDevices()
                    }.apply {
                        component.icon = AllIcons.Actions.Show
                    }

                    button("Restart ADB") {
                        restartAdbServer()
                    }.apply {
                        component.icon = AllIcons.Actions.Restart
                        component.toolTipText = "Restart ADB server to fix connection issues"
                    }
                }
            }

            group("Debug Output") {
                row {
                    scrollCell(outputArea)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()

                row {
                    button("Clear Output") {
                        clearOutput()
                    }

                    button("Copy Output") {
                        copyOutputToClipboard()
                    }
                }
            }

            group("Status") {
                row {
                    cell(statusLabel).apply {
                        component.foreground = Color.BLUE
                    }
                }
            }
        }.apply {
            border = JBUI.Borders.empty(8)
        }
    }

    private fun showPairDeviceDialog() {
        val dialog = PairDeviceDialog(project) { device ->
            loadDevices()
            addOutput("✅ Device '${device.name}' paired and added successfully")
            addOutput("💡 Tip: You can now connect to this device")

            // Try to select the newly added device
            selectDeviceByIp(device.ip)
        }
        dialog.show()
    }

    private fun editSelectedDevice() {
        val selectedDevice = deviceList.selectedValue
        if (selectedDevice == null) {
            Messages.showWarningDialog(project, "Please select a device to edit", "No Device Selected")
            return
        }

        val dialog = EditDeviceDialog(project, selectedDevice) { updatedDevice ->
            loadDevices()
            addOutput("✅ Device '${updatedDevice.name}' updated successfully")
            selectDeviceByIp(updatedDevice.ip)
        }
        dialog.show()
    }

    private fun removeSelectedDevice() {
        val selectedDevice = deviceList.selectedValue
        if (selectedDevice == null) {
            Messages.showWarningDialog(project, "Please select a device to remove", "No Device Selected")
            return
        }

        val result = Messages.showYesNoDialog(
            project,
            "Remove device '${selectedDevice.name}' (${selectedDevice.ip})?",
            "Confirm Removal",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            settingsService.removeDevice(selectedDevice.ip)
            loadDevices()
            addOutput("🗑️ Device '${selectedDevice.name}' removed from list")
        }
    }

    private fun connectToSelectedDevice() {
        val device = deviceList.selectedValue
        if (device == null) {
            Messages.showWarningDialog(project, "Please select a device to connect to", "No Device Selected")
            return
        }

        setButtonsEnabled(false)
        updateStatus("Connecting to ${device.name}...")
        addOutput("🔗 Connecting to ${device.getConnectAddress()}...")

        scope.launch {
            try {
                // First, clear any existing connections to this device
                val clearResult = adbService.clearDeviceConnections(device.ip)
                if (clearResult.success && clearResult.output.isNotEmpty()) {
                    SwingUtilities.invokeLater {
                        addOutput("🧹 ${clearResult.output}")
                    }
                }

                // Brief delay to ensure clean state
                delay(1000)

                // Now attempt connection
                val result = adbService.connectDevice(device)

                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ Successfully connected to ${device.name}!")
                        addOutput("📱 Device ready for debugging")
                        updateStatus("Connected to ${device.name}")

                        // Auto-refresh device list to show connection status
                        listConnectedDevices()
                    } else {
                        addOutput("❌ Failed to connect to ${device.name}")
                        addOutput("📄 Output: ${result.output}")
                        if (result.error.isNotEmpty()) {
                            addOutput("⚠️ Error: ${result.error}")
                        }
                        updateStatus("Connection failed")

                        // Provide helpful error suggestions
                        when {
                            result.output.contains("failed to connect") -> {
                                addOutput("💡 Suggestions:")
                                addOutput("   • Check if device port is correct (usually 5555)")
                                addOutput("   • Verify device is on same Wi-Fi network")
                                addOutput("   • Try re-pairing the device if connection was lost")
                            }
                            result.output.contains("Connection refused") -> {
                                addOutput("💡 Suggestion: Device may not be accepting connections")
                                addOutput("   • Enable 'Wireless debugging' on your Android device")
                                addOutput("   • Check if another ADB instance is connected")
                            }
                        }
                    }
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("💥 Exception during connection: ${e.message}")
                    updateStatus("Connection error")
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun disconnectFromSelectedDevice() {
        val device = deviceList.selectedValue
        if (device == null) {
            Messages.showWarningDialog(project, "Please select a device to disconnect", "No Device Selected")
            return
        }

        setButtonsEnabled(false)
        updateStatus("Disconnecting from ${device.name}...")
        addOutput("🔌 Disconnecting from ${device.getConnectAddress()}...")

        scope.launch {
            try {
                val result = adbService.disconnectDevice(device)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ Disconnected from ${device.name}")
                        updateStatus("Disconnected")

                        // Refresh to show updated connection status
                        listConnectedDevices()
                    } else {
                        addOutput("❌ Failed to disconnect from ${device.name}")
                        if (result.error.isNotEmpty()) {
                            addOutput("⚠️ Error: ${result.error}")
                        }
                        updateStatus("Disconnect failed")
                    }
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("💥 Exception during disconnect: ${e.message}")
                    updateStatus("Disconnect error")
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun listConnectedDevices() {
        updateStatus("Checking connected devices...")
        addOutput("📋 Listing all connected devices...")

        scope.launch {
            try {
                val result = adbService.listDevices()
                SwingUtilities.invokeLater {
                    addOutput("📱 Connected devices:")
                    if (result.output.isNotEmpty()) {
                        addOutput(result.output)
                    } else {
                        addOutput("   (No devices connected)")
                    }
                    updateStatus("Device list updated")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Error listing devices: ${e.message}")
                    updateStatus("List failed")
                }
            }
        }
    }

    private fun restartAdbServer() {
        setButtonsEnabled(false)
        updateStatus("Restarting ADB server...")
        addOutput("🔄 Restarting ADB server to fix potential issues...")

        scope.launch {
            try {
                val result = adbService.restartAdbServer()
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ ADB server restarted successfully")
                        addOutput("💡 You can now try connecting to devices")
                        updateStatus("ADB server restarted")
                    } else {
                        addOutput("❌ Failed to restart ADB server")
                        addOutput("⚠️ Error: ${result.error}")
                        updateStatus("Restart failed")
                    }
                    setButtonsEnabled(true)

                    // Re-check ADB status after restart
                    checkAdbStatus()
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("💥 Exception during restart: ${e.message}")
                    updateStatus("Restart error")
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun checkAdbStatus() {
        scope.launch {
            try {
                val result = adbService.checkAdbAvailability()
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ ADB is available and ready")
                        updateStatus("ADB Ready")
                    } else {
                        addOutput("❌ ADB not available: ${result.error}")
                        addOutput("💡 Make sure Android SDK platform-tools are installed")
                        updateStatus("ADB Error")
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Error checking ADB: ${e.message}")
                    updateStatus("ADB Error")
                }
            }
        }
    }

    private fun loadDevices() {
        SwingUtilities.invokeLater {
            deviceListModel.clear()
            settingsService.getSavedDevices().forEach { device ->
                deviceListModel.addElement(device)
            }

            if (deviceListModel.isEmpty) {
                addOutput("📝 No saved devices. Use 'Pair New Device' to add one.")
            } else {
                addOutput("📱 Loaded ${deviceListModel.size} saved device(s)")
            }
        }
    }

    private fun selectDeviceByIp(ip: String) {
        for (i in 0 until deviceListModel.size()) {
            if (deviceListModel.getElementAt(i).ip == ip) {
                deviceList.selectedIndex = i
                break
            }
        }
    }

    private fun addOutput(message: String) {
        SwingUtilities.invokeLater {
            val timestamp = java.time.LocalTime.now().toString().substring(0, 8)
            outputArea.append("[$timestamp] $message\n")
            outputArea.caretPosition = outputArea.document.length
        }
    }

    private fun clearOutput() {
        outputArea.text = "Output cleared.\n"
        addOutput("🧹 Debug output cleared")
    }

    private fun copyOutputToClipboard() {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(outputArea.text)
        clipboard.setContents(stringSelection, null)
        addOutput("📋 Output copied to clipboard")
    }

    private fun updateStatus(message: String) {
        SwingUtilities.invokeLater {
            statusLabel.text = message
            statusLabel.foreground = when {
                message.contains("error", ignoreCase = true) ||
                        message.contains("failed", ignoreCase = true) -> Color.RED
                message.contains("success", ignoreCase = true) ||
                        message.contains("ready", ignoreCase = true) ||
                        message.contains("connected", ignoreCase = true) -> Color.GREEN
                else -> Color.BLUE
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        SwingUtilities.invokeLater {
            connectButton.isEnabled = enabled
            disconnectButton.isEnabled = enabled
            pairButton.isEnabled = enabled
        }
    }

    /**
     * Enhanced device list renderer with connection status
     */
    private class DeviceListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            if (value is Device) {
                text = "${value.name} (${value.ip}:${value.port})"
                icon = AllIcons.General.Web

                // Visual indication for different states
                if (!isSelected) {
                    foreground = Color.BLACK
                }
            }

            return this
        }
    }
}