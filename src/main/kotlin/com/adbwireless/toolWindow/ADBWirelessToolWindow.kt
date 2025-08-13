package com.adbwireless.toolWindow

import com.adbwireless.dialogs.AddDeviceDialog
import com.adbwireless.dialogs.EditDeviceDialog
import com.adbwireless.dialogs.PairDeviceDialog
import com.adbwireless.dialogs.GenerateQRCodeDialog
import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Improved ADB Wireless Manager with QR scanning and streamlined UX
 */
class ADBWirelessToolWindow(private val project: Project) {

    // Services
    private val adbService = project.service<ADBService>()
    private val settingsService = SettingsService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State management
    private var isOperationInProgress = false
    private var selectedDevice: Device? = null

    // Status components
    private val statusLabel = JLabel("Ready").apply {
        icon = AllIcons.General.Information
        foreground = JBColor.GRAY
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    private val adbStatusLabel = JLabel().apply {
        font = font.deriveFont(Font.PLAIN, 11f)
    }

    // Device list with custom renderer
    private val deviceListModel = DefaultListModel<Device>()
    private var deviceList = JBList(deviceListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = ModernDeviceListRenderer()
        border = EmptyBorder(4, 4, 4, 4)
    }

    // Connection buttons (shown when device is selected)
    private val connectButton = createModernButton("Connect", AllIcons.Actions.Execute, JBColor(0x2E7D32, 0x4CAF50))
    private val disconnectButton = createModernButton("Disconnect", AllIcons.Actions.Suspend, JBColor(0xD32F2F, 0xF44336))

    // Output area with modern styling
    private val outputArea = JBTextArea().apply {
        isEditable = false
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        background = JBColor(Gray._252, Gray._40)
        border = EmptyBorder(8, 8, 8, 8)
    }

    // Connection status panel
    private val connectionStatusPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border()),
            JBUI.Borders.empty(8)
        )
        isVisible = false
    }

    init {
        setupEventHandlers()
        loadSavedDevices()
        initializeOutput()
        checkAdbStatus()
    }

    fun getContent(): JComponent {
        return createModernMainPanel()
    }

    private fun createModernMainPanel(): JPanel {
        val mainPanel = JPanel(BorderLayout()).apply {
            background = UIUtil.getPanelBackground()
        }

        // Header with status and add device button
        val headerPanel = createHeaderPanel()
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Main content in tabs (removed Connect tab)
        val tabbedPane = JBTabbedPane().apply {
            addTab("Devices", AllIcons.Nodes.DataTables, createDevicesPanel())
            addTab("Console", AllIcons.Debugger.Console, createConsolePanel())
        }

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        // Status bar
        val statusPanel = createStatusPanel()
        mainPanel.add(statusPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun createHeaderPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(8, 12)
            )
            background = JBColor(Gray._248, Gray._45)

            val titleLabel = JLabel("ADB Wireless Manager").apply {
                font = font.deriveFont(Font.BOLD, 16f)
                icon = AllIcons.General.Web
            }
            add(titleLabel, BorderLayout.WEST)

            // Toolbar with add device button
            val toolbarPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                add(createIconButton(AllIcons.General.Add, "Add new device") {
                    showAddDeviceDialog()
                })
                add(createIconButton(AllIcons.Actions.Refresh, "Refresh device list and check ADB status") {
                    refreshAll()
                })
            }
            add(toolbarPanel, BorderLayout.EAST)
        }
    }

    private fun createDevicesPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        // Main content area
        val contentPanel = JPanel(BorderLayout())

        // Devices list with header
        val listPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border()),
                JBUI.Borders.empty(8)
            )
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            val titleLabel = JLabel("Saved Devices").apply {
                font = font.deriveFont(Font.BOLD, 13f)
            }
            add(titleLabel, BorderLayout.WEST)

            val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                add(createIconButton(AllIcons.General.InspectionsOK, "Test ADB Connection") {
                    testAdbConnection()
                })
                add(createIconButton(AllIcons.Actions.Show, "List connected ADB devices") {
                    listConnectedDevices()
                })
                add(createIconButton(AllIcons.Actions.GC, "Clear all devices") {
                    clearAllDevices()
                })
            }
            add(actionsPanel, BorderLayout.EAST)
        }

        listPanel.add(headerPanel, BorderLayout.NORTH)
        listPanel.add(JBScrollPane(deviceList), BorderLayout.CENTER)

        contentPanel.add(listPanel, BorderLayout.CENTER)

        // Connection control panel (shown when device selected)
        updateConnectionStatusPanel()
        contentPanel.add(connectionStatusPanel, BorderLayout.SOUTH)

        panel.add(contentPanel, BorderLayout.CENTER)

        return panel
    }

    private fun createConsolePanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        val scrollPane = JBScrollPane(outputArea).apply {
            preferredSize = Dimension(400, 200)
            border = JBUI.Borders.customLine(JBColor.border())
        }

        panel.add(scrollPane, BorderLayout.CENTER)

        // Console controls
        val controlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(createIconButton(AllIcons.Actions.GC, "Clear console") {
                clearOutput()
            })
            add(createIconButton(AllIcons.Actions.Copy, "Copy to clipboard") {
                copyOutputToClipboard()
            })
        }

        panel.add(controlsPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun createStatusPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBColor.border()),
                JBUI.Borders.empty(4, 8)
            )
            background = JBColor(Gray._248, Gray._45)

            add(statusLabel, BorderLayout.WEST)
            add(adbStatusLabel, BorderLayout.EAST)
        }
    }

    private fun updateConnectionStatusPanel() {
        connectionStatusPanel.removeAll()

        if (selectedDevice != null) {
            connectionStatusPanel.isVisible = true

            val device = selectedDevice!!

            // Device info
            val deviceInfoPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("Selected: ${device.name} (${device.ip}:${device.defaultPort})").apply {
                    font = font.deriveFont(Font.BOLD, 12f)
                })
            }
            connectionStatusPanel.add(deviceInfoPanel)

            // Action buttons
            val actionsPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 8)).apply {
                add(connectButton)
                add(disconnectButton)

                add(JButton("Edit Device").apply {
                    icon = AllIcons.Actions.Edit
                    addActionListener { editSelectedDevice() }
                })

                add(JButton("Delete Device").apply {
                    icon = AllIcons.Actions.DeleteTag
                    addActionListener { deleteSelectedDevice() }
                })

                add(JButton("Re-pair Device").apply {
                    icon = AllIcons.General.Web
                    addActionListener { repairDevice() }
                })

                add(JButton("Generate QR Code").apply {
                    icon = AllIcons.Actions.Preview
                    addActionListener { generateQRCodeForDevice() }
                })
            }
            connectionStatusPanel.add(actionsPanel)

        } else {
            connectionStatusPanel.isVisible = false
        }

        connectionStatusPanel.revalidate()
        connectionStatusPanel.repaint()
    }

    private fun showAddDeviceDialog() {
        val dialog = AddDeviceDialog(project) { device ->
            // Device added successfully, refresh list
            loadSavedDevices()
            addOutput("📱 Device '${device.name}' added successfully", OutputType.SUCCESS)

            // Select the new device
            deviceListModel.elements().toList()
                .find { it.ip == device.ip }
                ?.let { newDevice ->
                    deviceList.setSelectedValue(newDevice, true)
                    selectedDevice = newDevice
                    updateConnectionStatusPanel()
                }


        }
        dialog.show()
    }

    private fun connectDevice() {
        val device = selectedDevice ?: return

        setOperationInProgress(true, "Connecting to ${device.name}...")
        addOutput("🔌 Connecting to ${device.getConnectAddress()}", OutputType.INFO)

        scope.launch {
            try {
                val result = adbService.connectDevice(device)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ Successfully connected to ${device.name}!", OutputType.SUCCESS)
                        addOutput("🎉 Device is ready for debugging!", OutputType.SUCCESS)
                        settingsService.updateLastConnected(device.ip)
                        loadSavedDevices()
                        updateStatus("Connected to ${device.name}")
                    } else {
                        addOutput("❌ Connection failed!", OutputType.ERROR)
                        addOutput("📋 ${result.error.ifEmpty { result.output }}", OutputType.ERROR)
                        showConnectionTroubleshooting()
                        updateStatus("Connection failed")
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Exception during connection: ${e.message}", OutputType.ERROR)
                    updateStatus("Connection error")
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun disconnectDevice() {
        val device = selectedDevice ?: return

        setOperationInProgress(true, "Disconnecting from ${device.name}...")
        addOutput("🔌 Disconnecting from ${device.getConnectAddress()}", OutputType.INFO)

        scope.launch {
            try {
                val result = adbService.disconnectDevice(device)
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ Disconnected from ${device.name}", OutputType.SUCCESS)
                        updateStatus("Disconnected from ${device.name}")
                    } else {
                        addOutput("❌ Disconnect failed: ${result.error}", OutputType.ERROR)
                        updateStatus("Disconnect failed")
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Exception during disconnect: ${e.message}", OutputType.ERROR)
                    updateStatus("Disconnect error")
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun repairDevice() {
        val device = selectedDevice ?: return

        val dialog = PairDeviceDialog(project, device) { success ->
            if (success) {
                addOutput("✅ Device '${device.name}' re-paired successfully", OutputType.SUCCESS)
                updateStatus("Device re-paired")
            }
        }
        dialog.show()
    }

    private fun generateQRCodeForDevice() {
        val device = selectedDevice ?: return

        val dialog = GenerateQRCodeDialog(project, device.ip, device.defaultPort)
        dialog.show()
        addOutput("📱 Generated QR code for ${device.name}", OutputType.INFO)
        updateStatus("QR code generated")
    }

    private fun editSelectedDevice() {
        val device = selectedDevice ?: return

        val dialog = EditDeviceDialog(project, device) { updatedDevice ->
            settingsService.removeDevice(device.ip)
            settingsService.saveDevice(updatedDevice)
            loadSavedDevices()

            // Update selected device
            val newDeviceIndex = (0 until deviceListModel.size()).find {
                deviceListModel.getElementAt(it).ip == device.ip
            }
            newDeviceIndex?.let { index ->
                deviceList.selectedIndex = index  // Use selectedIndex, not selectedValue
                selectedDevice = deviceListModel.getElementAt(index)
                updateConnectionStatusPanel()
            }

            addOutput("📝 Device '${updatedDevice.name}' updated successfully", OutputType.SUCCESS)
        }
        dialog.show()
    }

    private fun deleteSelectedDevice() {
        val device = selectedDevice ?: return

        val result = Messages.showYesNoDialog(
            project,
            "Delete saved device '${device.name}' (${device.ip})?",
            "Confirm Deletion",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            settingsService.removeDevice(device.ip)
            loadSavedDevices()
            selectedDevice = null
            updateConnectionStatusPanel()
            addOutput("🗑️ Device '${device.name}' deleted", OutputType.INFO)
            updateStatus("Device deleted")
        }
    }

    private fun listConnectedDevices() {
        setOperationInProgress(true, "Listing devices...")
        addOutput("📱 Checking connected ADB devices...", OutputType.INFO)

        scope.launch {
            try {
                val result = adbService.listDevices()
                SwingUtilities.invokeLater {
                    addOutput("📋 Connected devices:", OutputType.INFO)
                    addOutput(result.output.ifEmpty { "No devices connected" }, OutputType.INFO)
                    updateStatus("Device list updated")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Error listing devices: ${e.message}", OutputType.ERROR)
                    updateStatus("List devices failed")
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun clearAllDevices() {
        val result = Messages.showYesNoDialog(
            project,
            "Clear all saved devices?\n\nThis action cannot be undone.",
            "Confirm Clear All",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            settingsService.clearAllDevices()
            loadSavedDevices()
            selectedDevice = null
            updateConnectionStatusPanel()
            addOutput("🧹 All saved devices cleared", OutputType.INFO)
            updateStatus("All devices cleared")
        }
    }

    private fun refreshAll() {
        loadSavedDevices()
        checkAdbStatus()
        addOutput("🔄 Refreshed device list and ADB status", OutputType.INFO)
        updateStatus("Refreshed")
    }

    private fun testAdbConnection() {
        setOperationInProgress(true, "Testing ADB connection...")
        addOutput("🔍 Testing ADB connectivity...", OutputType.INFO)

        scope.launch {
            try {
                val result = adbService.testAdbConnectivity()
                SwingUtilities.invokeLater {
                    if (result.success) {
                        addOutput("✅ ADB connection test successful!", OutputType.SUCCESS)
                        addOutput("📋 ${result.output}", OutputType.INFO)
                        updateStatus("ADB test successful")
                    } else {
                        addOutput("❌ ADB connection test failed!", OutputType.ERROR)
                        addOutput("📋 ${result.error}", OutputType.ERROR)
                        updateStatus("ADB test failed")
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addOutput("❌ Error during ADB test: ${e.message}", OutputType.ERROR)
                    updateStatus("ADB test error")
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun createModernButton(text: String, icon: Icon, color: JBColor? = null): JButton {
        return JButton(text, icon).apply {
            font = font.deriveFont(Font.BOLD, 12f)
            color?.let { foreground = it }
            putClientProperty("JButton.variant", "primary")
            isFocusPainted = false
        }
    }

    private fun createIconButton(icon: Icon, tooltip: String, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            addActionListener { action() }
        }
    }

    private fun setupEventHandlers() {
        connectButton.addActionListener { connectDevice() }
        disconnectButton.addActionListener { disconnectDevice() }

        // Enhanced device list interactions
        deviceList.addListSelectionListener {
            if (!deviceList.isSelectionEmpty) {
                selectedDevice = deviceList.selectedValue
                updateConnectionStatusPanel()
                updateStatus("Selected: ${selectedDevice?.name}")
            } else {
                selectedDevice = null
                updateConnectionStatusPanel()
            }
        }

        deviceList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && !deviceList.isSelectionEmpty) {
                    connectDevice()
                }
            }
        })
    }

    // Helper methods (initializeOutput, checkAdbStatus, etc.)
    private fun initializeOutput() {
        addOutput("🚀 ADB Wireless Manager initialized", OutputType.INFO)
        addOutput("💡 Quick Start Guide:", OutputType.INFO)
        addOutput("   1. Click the + button to add a new device", OutputType.INFO)
        addOutput("   2. Choose pairing method (QR code or manual)", OutputType.INFO)
        addOutput("   3. Select device and click Connect", OutputType.INFO)
        addOutput("   4. Start debugging wirelessly!", OutputType.INFO)
        addOutput("═══════════════════════════════════════════", OutputType.SEPARATOR)
    }

    private fun checkAdbStatus() {
        scope.launch {
            try {
                val result = adbService.testAdbConnectivity()
                SwingUtilities.invokeLater {
                    if (result.success) {
                        adbStatusLabel.apply {
                            text = "ADB Ready"
                            icon = AllIcons.General.InspectionsOK
                            foreground = JBColor(0x2E7D32, 0x4CAF50)
                        }
                        addOutput("✅ ADB is available and ready", OutputType.SUCCESS)
                        addOutput("📍 ADB path: ${adbService.getAdbPathForDisplay()}", OutputType.INFO)
                        addOutput("📋 ${result.output}", OutputType.INFO)
                    } else {
                        adbStatusLabel.apply {
                            text = "ADB Not Found"
                            icon = AllIcons.General.Error
                            foreground = JBColor(0xD32F2F, 0xF44336)
                        }
                        addOutput("⚠️ Warning: ADB not found", OutputType.WARNING)
                        addOutput("💡 ${result.error}", OutputType.WARNING)
                        addOutput("🔧 Please ensure Android SDK is installed at D:\\Sdk", OutputType.WARNING)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    adbStatusLabel.apply {
                        text = "ADB Error"
                        icon = AllIcons.General.Error
                        foreground = JBColor.RED
                    }
                    addOutput("❌ Error checking ADB: ${e.message}", OutputType.ERROR)
                }
            }
        }
    }

    private fun showConnectionTroubleshooting() {
        addOutput("💡 Connection Troubleshooting:", OutputType.WARNING)
        addOutput("   • Ensure the device is paired first", OutputType.WARNING)
        addOutput("   • Check if the connection port changed", OutputType.WARNING)
        addOutput("   • Try re-pairing if connection keeps failing", OutputType.WARNING)
    }

    private fun setOperationInProgress(inProgress: Boolean, message: String = "") {
        isOperationInProgress = inProgress
        SwingUtilities.invokeLater {
            val buttons = listOf(connectButton, disconnectButton)
            buttons.forEach { it.isEnabled = !inProgress }

            if (inProgress) {
                statusLabel.apply {
                    text = message
                    icon = AnimatedIcon.Default()
                }
            } else {
                statusLabel.apply {
                    text = "Ready"
                    icon = AllIcons.General.Information
                }
            }
        }
    }

    private fun updateStatus(message: String) {
        SwingUtilities.invokeLater {
            if (!isOperationInProgress) {
                statusLabel.text = message
            }
        }
    }

    private fun loadSavedDevices() {
        SwingUtilities.invokeLater {
            val currentSelection = selectedDevice
            deviceListModel.clear()
            settingsService.getSavedDevices().forEach {
                deviceListModel.addElement(it)
            }

            // Restore selection if possible
            currentSelection?.let { current ->
                val deviceIndex = (0 until deviceListModel.size()).find {
                    deviceListModel.getElementAt(it).ip == current.ip
                }
                deviceIndex?.let { index ->
                    deviceList.selectedIndex = index
                    selectedDevice = deviceListModel.getElementAt(index)
                }
            }
        }
    }

    enum class OutputType { INFO, SUCCESS, WARNING, ERROR, SEPARATOR }

    private fun addOutput(message: String, type: OutputType = OutputType.INFO) {
        SwingUtilities.invokeLater {
            val timestamp = java.time.LocalTime.now().toString().substring(0, 8)
            val formattedMessage = when (type) {
                OutputType.SEPARATOR -> message
                else -> "[$timestamp] $message"
            }
            outputArea.append("$formattedMessage\n")
            outputArea.caretPosition = outputArea.document.length
        }
    }

    private fun clearOutput() {
        outputArea.text = ""
        addOutput("🧹 Console cleared", OutputType.INFO)
    }

    private fun copyOutputToClipboard() {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = java.awt.datatransfer.StringSelection(outputArea.text)
        clipboard.setContents(selection, selection)
        addOutput("📋 Console output copied to clipboard", OutputType.INFO)
    }

    /**
     * Custom renderer for device list with modern styling
     */
    private inner class ModernDeviceListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            if (value is Device) {
                val timeDiff = System.currentTimeMillis() - value.lastConnected
                val timeAgo = when {
                    timeDiff < 60_000 -> "now"
                    timeDiff < 3_600_000 -> "${timeDiff / 60_000}m ago"
                    timeDiff < 86_400_000 -> "${timeDiff / 3_600_000}h ago"
                    else -> "${timeDiff / 86_400_000}d ago"
                }

                text = """
                    <html>
                        <b>${value.name}</b><br>
                        <small style="color: gray;">${value.ip}:${value.defaultPort} • Last used $timeAgo</small>
                    </html>
                """.trimIndent()

                icon = AllIcons.General.Web
                border = EmptyBorder(6, 8, 6, 8)
            }

            return this
        }
    }
}