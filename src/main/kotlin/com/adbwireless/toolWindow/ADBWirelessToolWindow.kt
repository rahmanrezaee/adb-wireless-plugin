package com.adbwireless.toolWindow

import com.adbwireless.dialogs.InitialAdbSetupDialog
import com.adbwireless.dialogs.SelectUsbDeviceDialog
import com.adbwireless.dialogs.UnifiedDeviceDialog
import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.adbwireless.services.UsbQuickConnector
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * Simplified ADB Wireless Manager Tool Window (No SDK Configuration Section)
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
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)
        background = UIUtil.getListBackground()
        minimumSize = Dimension(400, 100)
        preferredSize = Dimension(600, 200)
    }

    // Device with connection status
    data class DeviceWithStatus(
        val device: Device,
        var isConnected: Boolean = false
    )

    private var deviceList = mutableListOf<DeviceWithStatus>()

    init {
        // Check if this is first time setup
        if (settingsService.isFirstTimeSetup || !settingsService.isAdbConfigured()) {
            showInitialSetupDialog()
        } else {
            loadDevices()
            checkAdbStatus()
        }
    }

    fun getContent(): JComponent {
        return panel {
            // Device Management Section (No SDK Configuration)
            group("Device Management") {
                // Main toolbar with device actions
                row {
                    button("Add New Device") {
                        if (checkAdbBeforeAction()) {
                            showAddDeviceDialog()
                        }
                    }.apply {
                        component.icon = AllIcons.General.Add
                        component.preferredSize = Dimension(150, 34)
                        component.toolTipText = "Add and configure a new Android device"
                        component.font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
                    }

                    // Quick Connect via USB button beside "Add New Device"
                    button("Quick Connect (USB)") {
                        if (checkAdbBeforeAction()) {
                            quickConnectViaUsb()
                        }
                    }.apply {
                        component.preferredSize = Dimension(170, 34)
                        component.toolTipText = "Enable and connect ADB over Wi‑Fi from a USB-connected device"
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

                    // Push settings button to the far right
                    comment("").resizableColumn()

                    // Settings button - icon only, positioned at tail
                    button("") {
                        openSettings()
                    }.apply {
                        component.icon = AllIcons.General.Settings
                        component.preferredSize = Dimension(34, 34)
                        component.toolTipText = "Configure Android SDK and ADB settings"
                        component.putClientProperty("JButton.buttonType", "square")
                    }
                }

                // Device list
                row {
                    val scrollPane = JBScrollPane(deviceListPanel).apply {
                        preferredSize = Dimension(0, 260)
                        minimumSize = Dimension(400, 120)
                        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                        border = JBUI.Borders.compound(
                            JBUI.Borders.customLine(JBColor.border()),
                            JBUI.Borders.empty(4)
                        )
                        background = UIUtil.getListBackground()

                        // Ensure proper viewport sizing
                        viewport.preferredSize = Dimension(400, 260)
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
            minimumSize = Dimension(500, 400)
            preferredSize = Dimension(700, 600)
        }
    }

    /**
     * Show initial setup dialog if ADB is not configured
     */
    private fun showInitialSetupDialog() {
        SwingUtilities.invokeLater {
            val dialog = InitialAdbSetupDialog(project)
            if (dialog.showAndGet()) {
                // Setup completed successfully
                loadDevices()
                checkAdbStatus()
                addOutput("🎉 ADB configuration completed successfully!")
            } else {
                // User cancelled setup
                addOutput("⚠️ ADB configuration cancelled. Please configure ADB in Settings to use this plugin.")
            }
        }
    }

    /**
     * Check if ADB is configured before performing actions
     */
    private fun checkAdbBeforeAction(): Boolean {
        if (!settingsService.isAdbConfigured()) {
            val result = Messages.showYesNoDialog(
                project,
                "ADB is not properly configured. Would you like to configure it now?",
                "ADB Configuration Required",
                "Configure ADB",
                "Cancel",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                openSettings()
            }
            return false
        }
        return true
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "ADB Wireless Manager")
    }

    private fun createDevicePanel(deviceWithStatus: DeviceWithStatus): JPanel {
        val panel = JPanel(java.awt.BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(12, 16, 12, 16)
            )
            background = UIUtil.getListBackground()
            minimumSize = Dimension(400, 60)
            preferredSize = Dimension(600, 60)
            maximumSize = Dimension(Int.MAX_VALUE, 60)
        }

        // Left side: Icon and device info
        val leftPanel = JPanel(java.awt.BorderLayout()).apply {
            background = UIUtil.getListBackground()
            preferredSize = Dimension(300, 50)

            // Status indicator
            val statusIndicator = JPanel().apply {
                preferredSize = Dimension(12, 12)
                minimumSize = Dimension(12, 12)
                background = if (deviceWithStatus.isConnected) {
                    JBColor.namedColor("Plugins.Button.installForeground", JBColor.GREEN)
                } else {
                    JBColor.namedColor("Component.errorFocusColor", JBColor.RED)
                }
                border = JBUI.Borders.empty(2)
            }

            val deviceInfoPanel = JPanel().apply {
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                background = UIUtil.getListBackground()
                border = JBUI.Borders.emptyLeft(12)

                val nameLabel = JLabel(deviceWithStatus.device.name).apply {
                    font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.BOLD)
                    foreground = UIUtil.getLabelForeground()
                    preferredSize = Dimension(250, 20)
                }

                val addressLabel = JLabel("${deviceWithStatus.device.ip}:${deviceWithStatus.device.port}").apply {
                    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
                    foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                    preferredSize = Dimension(250, 16)
                }

                add(nameLabel)
                add(javax.swing.Box.createVerticalStrut(2))
                add(addressLabel)
            }

            add(statusIndicator, java.awt.BorderLayout.WEST)
            add(deviceInfoPanel, java.awt.BorderLayout.CENTER)
        }

        // Right side: Action buttons with fixed sizing
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8)).apply {
            background = UIUtil.getListBackground()
            preferredSize = Dimension(180, 50)

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
                        if (checkAdbBeforeAction()) {
                            showEditDeviceDialog(deviceWithStatus)
                        }
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

        panel.add(leftPanel, java.awt.BorderLayout.CENTER)
        panel.add(rightPanel, java.awt.BorderLayout.EAST)

        return panel
    }

    private fun rebuildDeviceList() {
        SwingUtilities.invokeLater {
            deviceListPanel.removeAll()
            deviceListPanel.background = UIUtil.getListBackground()

            if (deviceList.isEmpty()) {
                // Modern empty state
                val emptyPanel = JPanel().apply {
                    layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
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
                    add(javax.swing.Box.createVerticalStrut(8))
                    add(messageLabel)
                    add(javax.swing.Box.createVerticalStrut(4))
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
                        addOutput("💥 Exception during disconnect: ${e.message ?: e.toString()}")
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
        if (!checkAdbBeforeAction()) return

        addOutput("🔄 Refreshing device list and connection status...")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Refreshing Device List", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val devicesResult = adbService.listDevices()
                    val connectedAddresses: Set<String> = if (devicesResult.success) {
                        parseConnectedSerials(devicesResult.output)
                    } else {
                        emptySet()
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
                        if (e.message?.contains("ADB not found") == true) {
                            addOutput("🔧 Please configure your Android SDK in Settings")
                        }
                    }
                }
            }
        })
    }

    private fun restartAdbServer() {
        if (!checkAdbBeforeAction()) return

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
                            if (result.error.contains("ADB not found")) {
                                addOutput("🔧 Please configure your Android SDK in Settings")
                            }
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        addOutput("💥 Exception during restart: ${e.message ?: e.toString()}")
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
                            addOutput("📍 ${adbService.getSdkStatus()}")
                        } else {
                            addOutput("❌ ADB not available: ${result.error}")
                            addOutput("🔧 Please configure your Android SDK in Settings → Tools → ADB Wireless Manager")
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

    // Quick Connect via USB flow used by the toolbar button
    private fun quickConnectViaUsb() {
        addOutput("⚡ Quick Connect via USB started...")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Quick Connect via USB", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val connector = UsbQuickConnector(adbService, indicator)

                    indicator.text = "Discovering USB connected devices..."
                    val devices = connector.listUsbDevices()

                    if (devices.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            addOutput("⚠️ No USB devices found. Connect a device via USB with USB debugging enabled.")
                            Messages.showWarningDialog(
                                project,
                                "Connect a device via USB with USB debugging enabled.",
                                "No USB Devices Found"
                            )
                        }
                        return
                    }

                    // Choose device on EDT when needed
                    val chosen: UsbQuickConnector.UsbDevice = if (devices.size == 1) {
                        devices.first()
                    } else {
                        val ref = AtomicReference<UsbQuickConnector.UsbDevice?>(null)
                        ApplicationManager.getApplication().invokeAndWait {
                            val dlg = SelectUsbDeviceDialog(project, devices)
                            if (dlg.showAndGet()) {
                                ref.set(dlg.selectedDevice)
                            }
                        }
                        val picked = ref.get()
                        if (picked == null) {
                            ApplicationManager.getApplication().invokeLater {
                                addOutput("ℹ️ Quick Connect cancelled")
                            }
                            return
                        }
                        picked
                    }

                    indicator.text = "Fetching device IP address..."
                    val ip = connector.getDeviceIp(chosen.serial)
                        ?: throw IllegalStateException("Could not determine device IP address. Ensure device Wi‑Fi is ON and connected.")

                    val port = 5555
                    indicator.text = "Enabling ADB over TCP/IP on port $port..."
                    connector.enableTcpIp(chosen.serial, port)

                    indicator.text = "Connecting to $ip:$port..."
                    val hostPort = "$ip:$port"
                    val ok = connector.connect(hostPort)
                    if (!ok) {
                        throw IllegalStateException("Failed to connect to $hostPort. Ensure device and PC are on the same network.")
                    }

                    // Save device using SettingsService
                    settingsService.saveDevice(
                        Device(
                            name = chosen.model ?: chosen.product ?: chosen.deviceName ?: "Android Device",
                            ip = ip,
                            port = port.toString()
                        )
                    )

                    // Inform success immediately (no refresh yet)
                    ApplicationManager.getApplication().invokeLater {
                        addOutput("✅ Connected to ${chosen.displayName()} at $hostPort (saved for future reconnects)")
                    }

                    // Wait for device to report 'device' state via get-state (more reliable than parsing 'devices')
                    indicator.text = "Verifying device is online..."
                    val online = waitUntilDeviceOnline(hostPort, timeoutMs = 15000, pollIntervalMs = 400)
                            || run {
                        // One reconnect attempt if not seen yet
                        indicator.text = "Reconnecting to ensure device is online..."
                        connector.connect(hostPort)
                        waitUntilDeviceOnline(hostPort, timeoutMs = 8000, pollIntervalMs = 400)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (online) {
                            addOutput("🔍 Verified device online: $hostPort")
                        } else {
                            addOutput("⌛ Device did not report online state within timeout: $hostPort (refreshing anyway)")
                        }
                        refreshDeviceList()
                    }
                } catch (t: Throwable) {
                    ApplicationManager.getApplication().invokeLater {
                        addOutput("❌ Quick Connect via USB failed: ${t.message ?: "Unknown error"}")
                        Messages.showErrorDialog(
                            project,
                            "${t.message ?: "Unknown error"}\n\nTips:\n• Allow USB debugging\n• Accept the device authorization prompt\n• Ensure Wireless debugging (if required) is enabled",
                            "Quick Connect via USB Failed"
                        )
                    }
                }
            }
        })
    }

    private fun parseConnectedSerials(output: String): Set<String> {
        val out = mutableSetOf<String>()
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("List of devices attached") }
            .forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val serial = parts[0]
                    val state = parts[1]
                    if (state == "device") {
                        out += serial
                    }
                }
            }
        return out
    }

    // Poll 'adb -s <hostPort> get-state' until it returns 'device'
    private fun waitUntilDeviceOnline(hostPort: String, timeoutMs: Int = 12000, pollIntervalMs: Int = 300): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val res = adbService.executeAdbCommand("-s", hostPort, "get-state")
            val state = (res.output.ifBlank { res.error }).trim().lowercase()
            if (state == "device") return true
            try {
                Thread.sleep(pollIntervalMs.toLong())
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
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