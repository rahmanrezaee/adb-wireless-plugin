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
 * Simple ADB Wireless Manager Tool Window
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
        rows = 8
        font = Font("Monospaced", Font.PLAIN, 11)
        text = "ADB Wireless Manager - Simple Version\n"
    }

    init {
        loadDevices()
        checkAdbStatus()
    }

    fun getContent(): JComponent {
        return panel {
            group("Devices") {
                row {
                    button("Pair New Device") {
                        showPairDeviceDialog()
                    }.apply {
                        component.icon = AllIcons.General.Web
                        component.preferredSize = Dimension(140, 30)
                    }
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
                    button("Refresh") {
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

            group("Actions") {
                row {
                    button("Connect") {
                        connectToSelectedDevice()
                    }.apply {
                        component.icon = AllIcons.Actions.Execute
                        component.preferredSize = Dimension(100, 30)
                    }
                    button("Disconnect") {
                        disconnectFromSelectedDevice()
                    }.apply {
                        component.icon = AllIcons.Actions.Suspend
                        component.preferredSize = Dimension(100, 30)
                    }
                    button("List Devices") {
                        listConnectedDevices()
                    }.apply {
                        component.icon = AllIcons.Actions.Show
                    }
                }
            }

            group("Output") {
                row {
                    scrollCell(outputArea)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
                row {
                    button("Clear") {
                        clearOutput()
                    }
                }
            }

            group("Status") {
                row {
                    cell(statusLabel)
                }
            }
        }.apply {
            border = JBUI.Borders.empty(8)
        }
    }

    private fun showPairDeviceDialog() {
        val dialog = PairDeviceDialog(project) { device ->
            loadDevices()
            addOutput("Device '${device.name}' paired and added successfully")

            // Try to select the newly added device
            for (i in 0 until deviceListModel.size()) {
                if (deviceListModel.getElementAt(i).ip == device.ip) {
                    deviceList.selectedIndex = i
                    break
                }
            }
        }
        dialog.show()
    }

    private fun editSelectedDevice() {
        val selectedDevice = deviceList.selectedValue ?: return

        val dialog = EditDeviceDialog(project, selectedDevice) { updatedDevice ->
            loadDevices()
            addOutput("Device '${updatedDevice.name}' updated successfully")

            // Try to select the updated device in the list
            for (i in 0 until deviceListModel.size()) {
                if (deviceListModel.getElementAt(i).ip == updatedDevice.ip) {
                    deviceList.selectedIndex = i
                    break
                }
            }
        }
        dialog.show()
    }

    private fun removeSelectedDevice() {
        val selectedDevice = deviceList.selectedValue ?: return

        val result = Messages.showYesNoDialog(
            project,
            "Remove device '${selectedDevice.name}'?",
            "Confirm Removal",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            settingsService.removeDevice(selectedDevice.ip)
            loadDevices()
            addOutput("Device '${selectedDevice.name}' removed")
        }
    }

    private fun connectToSelectedDevice() {
        val device = deviceList.selectedValue ?: return

        updateStatus("Connecting to ${device.name}...")
        addOutput("Connecting to ${device.getConnectAddress()}...")

        scope.launch {
            try {
                val result = adbService.connectDevice(device)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ Connected to ${device.name} successfully!")
                        updateStatus("Connected to ${device.name}")
                    } else {
                        addOutput("❌ Failed to connect to ${device.name}")
                        addOutput("Output: ${result.output}")
                        addOutput("Error: ${result.error}")
                        updateStatus("Connection failed")

                        // Show user-friendly error message
                        if (result.output.contains("failed to connect")) {
                            addOutput("💡 Tip: Check if device port is correct or if device needs re-pairing")
                        }
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Exception during connection: ${e.message}")
                    updateStatus("Connection error")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun disconnectFromSelectedDevice() {
        val device = deviceList.selectedValue ?: return

        updateStatus("Disconnecting from ${device.name}...")
        addOutput("Disconnecting from ${device.getConnectAddress()}...")

        scope.launch {
            try {
                val result = adbService.disconnectDevice(device)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ Disconnected from ${device.name}")
                        updateStatus("Disconnected")
                    } else {
                        addOutput("❌ Failed to disconnect from ${device.name}")
                        addOutput("Error: ${result.error}")
                        updateStatus("Disconnect failed")
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Exception: ${e.message}")
                    updateStatus("Disconnect error")
                }
            }
        }
    }

    private fun listConnectedDevices() {
        updateStatus("Listing devices...")
        addOutput("Checking connected devices...")

        scope.launch {
            try {
                val result = adbService.listDevices()
                SwingUtilities.invokeLater {
                    addOutput("Connected devices:")
                    addOutput(result.output.ifEmpty { "No devices connected" })
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
    }

    private fun updateStatus(message: String) {
        SwingUtilities.invokeLater {
            statusLabel.text = message
        }
    }

    /**
     * Simple device list renderer
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
            }

            return this
        }
    }
}