package com.adbwireless.toolWindow

import com.adbwireless.dialogs.UnifiedDeviceDialog
import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*

/**
 * Modern ADB Wireless Manager Tool Window
 */
class ADBWirelessToolWindow(private val project: Project) {

    private val adbService = project.service<ADBService>()
    private val settingsService = SettingsService.getInstance()

    private val outputArea = JBTextArea().apply {
        isEditable = false
        rows = 12
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        text = "🚀 ADB Wireless Manager - Ready\n"
        background = JBColor.namedColor("Editor.background", UIUtil.getTextFieldBackground())
        border = JBUI.Borders.empty(12)
    }

    // Custom device list panel
    private val deviceListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)
        background = UIUtil.getListBackground()
    }

    // Device with connection status
    data class DeviceWithStatus(
        val device: Device,
        var isConnected: Boolean = false
    )

    private var deviceList = mutableListOf<DeviceWithStatus>()

    init {
        loadDevices()
        checkAdbStatus()
    }

    fun getContent(): JComponent {
        return panel {
            // Device Management Section
            group("Device Management") {
                // Modern toolbar with better spacing
                row {
                    button("Add New Device") {
                        showAddDeviceDialog()
                    }.apply {
                        component.icon = AllIcons.General.Add
                        component.preferredSize = Dimension(150, 34)
                        component.toolTipText = "Add and configure a new Android device"
                        component.font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
                    }

                    button("Refresh List") {
                        refreshDeviceList()
                    }.apply {
                        component.icon = AllIcons.Actions.Refresh
                        component.preferredSize = Dimension(120, 34)
                        component.toolTipText = "Refresh device list and check connection status"
                        component.font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
                    }

                    button("Restart ADB") {
                        restartAdbServer()
                    }.apply {
                        component.icon = AllIcons.Actions.Restart
                        component.preferredSize = Dimension(120, 34)
                        component.toolTipText = "Restart ADB server and reset all connections"
                        component.font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
                    }
                }

                // Modern device list with better styling
                row {
                    val scrollPane = JBScrollPane(deviceListPanel).apply {
                        preferredSize = Dimension(0, 240)
                        minimumSize = Dimension(0, 120)
                        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                        border = JBUI.Borders.compound(
                            JBUI.Borders.customLine(JBColor.border()),
                            JBUI.Borders.empty(4)
                        )
                        background = UIUtil.getListBackground()
                    }
                    cell(scrollPane)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            // Spacing between sections
            separator()


            // Activity Log Section
            group("Activity Log") {
                row {
                    scrollCell(outputArea)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()

                row {
                    button("Clear Log") {
                        clearOutput()
                    }.apply {
                        component.icon = AllIcons.Actions.GC
                        component.toolTipText = "Clear the activity log"
                    }

                    button("Copy Log") {
                        copyOutputToClipboard()
                    }.apply {
                        component.icon = AllIcons.Actions.Copy
                        component.toolTipText = "Copy log contents to clipboard"
                    }

                    // Add a spacer and status indicator
                    comment("").apply {
                        component.preferredSize = Dimension(20, 0)
                    }

                    text("💡 Green dot = Connected  •  Red dot = Disconnected").apply {
                        component.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                        component.foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                    }
                }
            }
        }.apply {
            border = JBUI.Borders.empty(16, 20, 20, 20)
        }
    }

    private fun createDevicePanel(deviceWithStatus: DeviceWithStatus): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(12, 16, 12, 16)
            )
            background = UIUtil.getListBackground()
            maximumSize = Dimension(Int.MAX_VALUE, 60)
        }

        // Left side: Icon and device info
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 8)).apply {
            background = UIUtil.getListBackground()

            // Modern connection status indicator
            val statusIndicator = JPanel().apply {
                preferredSize = Dimension(10, 10)
                background = if (deviceWithStatus.isConnected) {
                    JBColor.namedColor("Plugins.Button.installForeground", JBColor.GREEN)
                } else {
                    JBColor.namedColor("Component.errorFocusColor", JBColor.RED)
                }
                border = JBUI.Borders.empty(2)
            }

            val deviceInfoPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = UIUtil.getListBackground()

                val nameLabel = JLabel(deviceWithStatus.device.name).apply {
                    font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.BOLD)
                    foreground = UIUtil.getLabelForeground()
                }

                val addressLabel = JLabel("${deviceWithStatus.device.ip}:${deviceWithStatus.device.port}").apply {
                    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                    foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                }

                add(nameLabel)
                add(Box.createVerticalStrut(2))
                add(addressLabel)
            }

            add(statusIndicator)
            add(deviceInfoPanel)
        }

        // Right side: Action buttons with modern styling
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8)).apply {
            background = UIUtil.getListBackground()

            if (deviceWithStatus.isConnected) {
                // Connected device: Only show Disconnect button
                val disconnectBtn = JButton("Disconnect").apply {
                    preferredSize = Dimension(90, 28)
                    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                    toolTipText = "Disconnect from this device"
                    foreground = JBColor.namedColor("Component.errorFocusColor", JBColor.RED)
                    addActionListener {
                        disconnectFromDevice(deviceWithStatus)
                    }
                }
                add(disconnectBtn)
            } else {
                // Disconnected device: Show Edit and Remove buttons
                val editBtn = JButton("Edit").apply {
                    preferredSize = Dimension(60, 28)
                    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                    toolTipText = "Edit device settings, pair, or connect"
                    addActionListener {
                        showEditDeviceDialog(deviceWithStatus)
                    }
                }

                val removeBtn = JButton("Remove").apply {
                    preferredSize = Dimension(70, 28)
                    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                    foreground = JBColor.namedColor("Component.errorFocusColor", JBColor.RED)
                    toolTipText = "Remove device from list"
                    addActionListener {
                        removeDevice(deviceWithStatus)
                    }
                }

                add(editBtn)
                add(removeBtn)
            }
        }

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(rightPanel, BorderLayout.EAST)

        return panel
    }

    private fun rebuildDeviceList() {
        SwingUtilities.invokeLater {
            deviceListPanel.removeAll()
            deviceListPanel.background = UIUtil.getListBackground()

            if (deviceList.isEmpty()) {
                // Modern empty state
                val emptyPanel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    background = UIUtil.getListBackground()
                    border = JBUI.Borders.empty(40, 20, 40, 20)

                    val iconLabel = JLabel(AllIcons.General.Information).apply {
                        alignmentX = Component.CENTER_ALIGNMENT
                    }

                    val messageLabel = JLabel("No devices configured yet").apply {
                        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.BOLD)
                        foreground = UIUtil.getLabelForeground()
                        alignmentX = Component.CENTER_ALIGNMENT
                    }

                    val instructionLabel = JLabel("Click 'Add New Device' to get started").apply {
                        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                        foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                        alignmentX = Component.CENTER_ALIGNMENT
                    }

                    add(iconLabel)
                    add(Box.createVerticalStrut(8))
                    add(messageLabel)
                    add(Box.createVerticalStrut(4))
                    add(instructionLabel)
                }
                deviceListPanel.add(emptyPanel)
            } else {
                // Add device panels
                deviceList.forEach { deviceWithStatus ->
                    deviceListPanel.add(createDevicePanel(deviceWithStatus))
                }
            }

            deviceListPanel.revalidate()
            deviceListPanel.repaint()
        }
    }

    private fun showAddDeviceDialog() {
        val dialog = UnifiedDeviceDialog(project, null) { device ->
            loadDevices()
            addOutput("✅ Device '${device.name}' added successfully")
        }
        dialog.show()
    }

    private fun showEditDeviceDialog(deviceWithStatus: DeviceWithStatus) {
        val dialog = UnifiedDeviceDialog(project, deviceWithStatus.device) { updatedDevice ->
            loadDevices()
            addOutput("✅ Device '${updatedDevice.name}' updated successfully")
        }
        dialog.show()
    }

    private fun disconnectFromDevice(deviceWithStatus: DeviceWithStatus) {
        addOutput("🔌 Disconnecting from ${deviceWithStatus.device.getConnectAddress()}...")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Disconnecting from Device", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Disconnecting from ${deviceWithStatus.device.name}..."
                    val result = adbService.disconnectDevice(deviceWithStatus.device)

                    ApplicationManager.getApplication().invokeLater {
                        if (result.success) {
                            addOutput("✅ Disconnected from ${deviceWithStatus.device.name}")
                            refreshDeviceList()
                        } else {
                            addOutput("❌ Failed to disconnect from ${deviceWithStatus.device.name}")
                            if (result.error.isNotEmpty()) {
                                addOutput("⚠️ Error: ${result.error}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        addOutput("💥 Exception during disconnect: ${e.message}")
                    }
                }
            }
        })
    }

    private fun removeDevice(deviceWithStatus: DeviceWithStatus) {
        val result = Messages.showYesNoDialog(
            project,
            "Remove device '${deviceWithStatus.device.name}' (${deviceWithStatus.device.ip})?",
            "Confirm Removal",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            settingsService.removeDevice(deviceWithStatus.device.ip)
            loadDevices()
            addOutput("🗑️ Device '${deviceWithStatus.device.name}' removed from list")
        }
    }

    private fun refreshDeviceList() {
        addOutput("🔄 Refreshing device list and connection status...")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Refreshing Device List", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val devicesResult = adbService.listDevices()
                    val connectedAddresses = if (devicesResult.success) {
                        devicesResult.output.lines()
                            .filter { it.contains("device") && !it.contains("List of devices") }
                            .mapNotNull { line ->
                                val parts = line.split("\\s+".toRegex())
                                if (parts.isNotEmpty()) parts[0] else null
                            }
                    } else {
                        emptyList()
                    }

                    ApplicationManager.getApplication().invokeLater {
                        deviceList.clear()
                        settingsService.getSavedDevices().forEach { device ->
                            val isConnected = connectedAddresses.contains("${device.ip}:${device.port}")
                            deviceList.add(DeviceWithStatus(device, isConnected))
                        }

                        rebuildDeviceList()

                        if (deviceList.isEmpty()) {
                            addOutput("📝 No saved devices. Use 'Add New Device' to add one.")
                        } else {
                            addOutput("📱 Loaded ${deviceList.size} device(s) with connection status")
                            val connectedCount = deviceList.count { it.isConnected }
                            addOutput("🔗 Connected devices: $connectedCount")
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        addOutput("❌ Error refreshing device list: ${e.message}")
                    }
                }
            }
        })
    }

    private fun restartAdbServer() {
        addOutput("🔄 Restarting ADB server and resetting all connections...")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Restarting ADB Server", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Restarting ADB server..."
                    val result = adbService.restartAdbServer()

                    ApplicationManager.getApplication().invokeLater {
                        if (result.success) {
                            addOutput("✅ ADB server restarted successfully")
                            addOutput("🔌 All device connections have been reset")
                            refreshDeviceList()
                        } else {
                            addOutput("❌ Failed to restart ADB server")
                            addOutput("⚠️ Error: ${result.error}")
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        addOutput("💥 Exception during restart: ${e.message}")
                    }
                }
            }
        })
    }

    private fun checkAdbStatus() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Checking ADB", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val result = adbService.checkAdbAvailability()
                    ApplicationManager.getApplication().invokeLater {
                        if (result.success) {
                            addOutput("✅ ADB is available and ready")
                        } else {
                            addOutput("❌ ADB not available: ${result.error}")
                            addOutput("💡 Make sure Android SDK platform-tools are installed")
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        addOutput("❌ Error checking ADB: ${e.message}")
                    }
                }
            }
        })
    }

    private fun loadDevices() {
        SwingUtilities.invokeLater {
            deviceList.clear()
            settingsService.getSavedDevices().forEach { device ->
                deviceList.add(DeviceWithStatus(device, false))
            }

            rebuildDeviceList()

            if (deviceList.isEmpty()) {
                addOutput("📝 No saved devices. Use 'Add New Device' to add one.")
            } else {
                addOutput("📱 Loaded ${deviceList.size} saved device(s)")
                refreshDeviceList()
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
        outputArea.text = "🚀 ADB Wireless Manager - Log cleared\n"
        addOutput("🧹 Activity log cleared")
    }

    private fun copyOutputToClipboard() {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(outputArea.text)
        clipboard.setContents(stringSelection, null)
        addOutput("📋 Log copied to clipboard")
    }
}