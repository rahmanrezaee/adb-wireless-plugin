package com.adbwireless.dialogs

import com.adbwireless.models.Device
import com.adbwireless.services.ADBService
import com.adbwireless.services.SettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * Enhanced dialog with comprehensive debugging for device pairing
 */
class AddDeviceDialog(
    private val project: Project,
    private val onDeviceAdded: (Device) -> Unit
) : DialogWrapper(project) {

    private val adbService = project.service<ADBService>()
    private val settingsService = SettingsService.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI Components
    private val deviceNameField = JBTextField("Test Device").apply {
        emptyText.text = "Enter device name (e.g., Galaxy S23)"
    }

    private val deviceIpField = JBTextField().apply {
        emptyText.text = "192.168.1.xxx"
    }

    // Manual pairing components
    private val pairingPortField = JBTextField().apply {
        emptyText.text = "Pairing port (e.g., 37561)"
        columns = 10
    }

    private val pairingCodeField = JBTextField().apply {
        emptyText.text = "6-digit code"
        columns = 8
    }

    // Debug output area
    private val debugOutput = JBTextArea().apply {
        isEditable = false
        rows = 15
        columns = 60
        font = Font("Monospaced", Font.PLAIN, 11)
        background = Color(0xF5F5F5)
        text = "Debug output will appear here...\n"
    }

    private val statusLabel = JLabel("Ready for pairing")
    private var isOperationInProgress = false

    init {
        title = "Add New Wireless Device - DEBUG MODE"
        init()
        setupUI()
        runInitialChecks()
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
                        .comment("Get this from Android Settings → Developer Options → Wireless debugging")
                }
            }

            group("Manual Pairing") {
                row("Pairing Port:") {
                    cell(pairingPortField)
                        .comment("From 'Pair device with pairing code' - NOT the main port")
                }
                row("Pairing Code:") {
                    cell(pairingCodeField)
                        .comment("6-digit code shown on your device")
                }
                row {
                    button("🔗 Pair Device") {
                        pairDeviceManually()
                    }.apply {
                        component.preferredSize = Dimension(150, 30)
                    }
                    button("🧪 Test ADB") {
                        testAdbConnection()
                    }
                    button("🔄 Restart ADB") {
                        restartAdbServer()
                    }
                }
            }

            group("Debug Output") {
                row {
                    scrollCell(debugOutput)
                        .align(Align.FILL)
                        .resizableColumn()
                }
                row {
                    button("Clear Debug") {
                        clearDebug()
                    }
                    button("Copy Debug") {
                        copyDebugToClipboard()
                    }
                }
            }

            group("Status") {
                row {
                    cell(statusLabel).apply {
                        component.font = component.font.deriveFont(Font.BOLD, 12f)
                    }
                }
            }
        }.apply {
            preferredSize = Dimension(700, 600)
            border = JBUI.Borders.empty(16)
        }
    }

    private fun setupUI() {
        // Auto-fill IP if possible
        scope.launch {
            try {
                val localHost = java.net.InetAddress.getLocalHost()
                val hostAddress = localHost.hostAddress
                if (hostAddress.startsWith("192.168.") || hostAddress.startsWith("10.") || hostAddress.startsWith("172.")) {
                    val networkPrefix = hostAddress.substringBeforeLast(".")
                    SwingUtilities.invokeLater {
                        deviceIpField.emptyText.text = "$networkPrefix.xxx"
                        addDebug("💡 Your computer's IP: $hostAddress")
                        addDebug("💡 Your device should have IP like: $networkPrefix.xxx")
                    }
                }
            } catch (e: Exception) {
                addDebug("⚠️ Could not detect network: ${e.message}")
            }
        }
    }

    private fun runInitialChecks() {
        addDebug("🚀 Starting ADB Wireless Manager Debug Mode")


        scope.launch {
            addDebug("1️⃣ Testing ADB installation...")
            val testResult = adbService.testAdbConnectivity()

            SwingUtilities.invokeLater {
                if (testResult.success) {
                    addDebug("✅ ADB test completed successfully!")
                    addDebug(testResult.output)
                    updateStatus("ADB Ready - Ready for pairing", false)
                } else {
                    addDebug("❌ ADB test failed!")
                    addDebug("Error: ${testResult.error}")
                    addDebug("Output: ${testResult.output}")
                    updateStatus("ADB Error - Check debug output", true)

                    showAdbInstallationHelp()
                }
            }
        }
    }

    private fun pairDeviceManually() {
        val deviceName = deviceNameField.text.trim()
        val deviceIp = deviceIpField.text.trim()
        val pairingPort = pairingPortField.text.trim()
        val pairingCode = pairingCodeField.text.trim()

        addDebug("🔗 STARTING MANUAL PAIRING PROCESS")


        // Validate inputs
        addDebug("📋 Validating inputs...")
        addDebug("  Device Name: '$deviceName'")
        addDebug("  Device IP: '$deviceIp'")
        addDebug("  Pairing Port: '$pairingPort'")
        addDebug("  Pairing Code: '$pairingCode'")

        val validationError = validateInputs(deviceName, deviceIp, pairingPort, pairingCode)
        if (validationError != null) {
            addDebug("❌ Validation failed: $validationError")
            updateStatus(validationError, true)
            return
        }

        addDebug("✅ All inputs valid, proceeding with pairing...")

        setOperationInProgress(true, "Pairing with device...")

        scope.launch {
            try {
                addDebug("\n🚀 Executing ADB pair command...")
                addDebug("Command: adb pair $deviceIp:$pairingPort $pairingCode")
                addDebug("⏳ Waiting for ADB response (timeout: 30s)...")

                // Add periodic status updates
                val statusJob = launch {
                    var dots = 0
                    while (isActive) {
                        delay(1000)
                        dots = (dots + 1) % 4
                        val dotsStr = ".".repeat(dots)
                        SwingUtilities.invokeLater {
                            updateStatus("🔄 Pairing in progress$dotsStr", false)
                        }
                    }
                }

                val result = adbService.pairDevice(deviceIp, pairingPort, pairingCode)
                statusJob.cancel()

                SwingUtilities.invokeLater {
                    addDebug("\n📊 PAIRING RESULT:")
                    addDebug("  Success: ${result.success}")
                    addDebug("  Exit Code: ${result.exitCode}")
                    addDebug("  Timed Out: ${result.timedOut}")
                    addDebug("  Command: ${result.command}")
                    addDebug("  Output: '${result.output}'")
                    addDebug("  Error: '${result.error}'")

                    if (result.timedOut) {
                        addDebug("\n⏰ PAIRING TIMED OUT!")
                        addDebug("This usually means:")
                        addDebug("• Wrong IP address or port")
                        addDebug("• Device not on same network")
                        addDebug("• Firewall blocking connection")
                        addDebug("• Pairing code expired")
                        updateStatus("❌ Pairing timed out - check network and try again", true)
                        showTimeoutTroubleshooting()

                    } else if (result.success) {
                        addDebug("\n🎉 PAIRING SUCCESSFUL!")
                        addDebug("Device should now be paired and ready for connection")

                        // Save device
                        val device = Device(deviceName, deviceIp, "5555")
                        settingsService.saveDevice(device)

                        updateStatus("✅ Pairing successful! Device saved.", false)

                        // Ask if user wants to test connection
                        val testConnection = Messages.showYesNoDialog(
                            project,
                            "Pairing successful! Would you like to test the connection now?",
                            "Test Connection?",
                            Messages.getQuestionIcon()
                        )

                        if (testConnection == Messages.YES) {
                            testConnection(device)
                        } else {
                            onDeviceAdded(device)
                            close(OK_EXIT_CODE)
                        }

                    } else {
                        addDebug("\n❌ PAIRING FAILED!")
                        handlePairingFailure(result)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addDebug("\n💥 EXCEPTION DURING PAIRING:")
                    addDebug("  Exception: ${e.javaClass.simpleName}")
                    addDebug("  Message: ${e.message}")
                    addDebug("  Stack trace:")
                    e.printStackTrace()

                    updateStatus("❌ Exception during pairing: ${e.message}", true)
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun testConnection(device: Device) {
        addDebug("\n🔌 TESTING CONNECTION...")
        addDebug("Attempting to connect to ${device.getConnectAddress()}")

        scope.launch {
            try {
                val result = adbService.connectDevice(device)

                SwingUtilities.invokeLater {
                    addDebug("\n📊 CONNECTION TEST RESULT:")
                    addDebug("  Success: ${result.success}")
                    addDebug("  Exit Code: ${result.exitCode}")
                    addDebug("  Output: '${result.output}'")
                    addDebug("  Error: '${result.error}'")

                    if (result.success) {
                        addDebug("🎉 CONNECTION SUCCESSFUL!")
                        addDebug("Device is ready for wireless debugging!")
                        updateStatus("✅ Connection test successful!", false)

                        Messages.showInfoMessage(
                            project,
                            "Success! Device '${device.name}' is now connected and ready for wireless debugging.",
                            "Connection Successful"
                        )

                        onDeviceAdded(device)
                        close(OK_EXIT_CODE)
                    } else {
                        addDebug("❌ CONNECTION FAILED!")
                        addDebug("The device was paired but connection failed.")
                        addDebug("This might be normal - the connection port may be different.")

                        updateStatus("⚠️ Pairing OK, but connection failed. Check device settings.", true)

                        // Still add the device as it was successfully paired
                        onDeviceAdded(device)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addDebug("💥 CONNECTION TEST EXCEPTION: ${e.message}")
                    updateStatus("Connection test failed", true)
                }
            }
        }
    }

    private fun validateInputs(name: String, ip: String, port: String, code: String): String? {
        return when {
            name.isEmpty() -> "❌ Please enter a device name"
            ip.isEmpty() -> "❌ Please enter the device IP address"
            !ip.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) ->
                "❌ Invalid IP format. Use format like 192.168.1.100"
            port.isEmpty() -> "❌ Please enter the pairing port"
            !port.all { it.isDigit() } -> "❌ Pairing port must be numeric"
            port.toIntOrNull() !in 1..65535 -> "❌ Invalid port number (1-65535)"
            code.isEmpty() -> "❌ Please enter the pairing code"
            code.length != 6 -> "❌ Pairing code must be exactly 6 digits"
            !code.all { it.isDigit() } -> "❌ Pairing code must contain only digits"
            else -> null
        }
    }

    private fun handlePairingFailure(result: ADBService.CommandResult) {
        addDebug("🔍 ANALYZING FAILURE...")

        val output = result.output.lowercase()
        val error = result.error.lowercase()
        val combined = "$output $error"

        when {
            "failed to authenticate" in combined || "authentication failed" in combined -> {
                addDebug("📱 DIAGNOSIS: Authentication failed")
                addDebug("💡 SOLUTION: Check the pairing code and try again")
                updateStatus("❌ Wrong pairing code. Please check and retry.", true)
            }
            "connection refused" in combined || "refused" in combined -> {
                addDebug("📱 DIAGNOSIS: Connection refused")
                addDebug("💡 SOLUTION: Check IP address and port")
                updateStatus("❌ Connection refused. Check IP and port.", true)
            }
            "timeout" in combined || "timed out" in combined -> {
                addDebug("📱 DIAGNOSIS: Connection timeout")
                addDebug("💡 SOLUTION: Check network connection")
                updateStatus("❌ Connection timeout. Check network.", true)
            }
            "no route to host" in combined || "unreachable" in combined -> {
                addDebug("📱 DIAGNOSIS: Network unreachable")
                addDebug("💡 SOLUTION: Ensure both devices are on same WiFi network")
                updateStatus("❌ Network unreachable. Check WiFi connection.", true)
            }
            "invalid" in combined && "port" in combined -> {
                addDebug("📱 DIAGNOSIS: Invalid port")
                addDebug("💡 SOLUTION: Check the pairing port from device settings")
                updateStatus("❌ Invalid port. Check device pairing settings.", true)
            }
            else -> {
                addDebug("📱 DIAGNOSIS: Unknown error")
                addDebug("💡 SOLUTION: Try restarting wireless debugging on device")
                updateStatus("❌ Pairing failed. See debug output for details.", true)
            }
        }

        // Show troubleshooting dialog
        showPairingTroubleshooting()
    }

    private fun testAdbConnection() {
        addDebug("\n🧪 TESTING ADB CONNECTION...")
        setOperationInProgress(true, "Testing ADB...")

        scope.launch {
            try {
                val result = adbService.testAdbConnectivity()

                SwingUtilities.invokeLater {
                    addDebug("\n📊 ADB TEST RESULT:")
                    addDebug("Success: ${result.success}")
                    addDebug("Output:\n${result.output}")
                    if (result.error.isNotEmpty()) {
                        addDebug("Error:\n${result.error}")
                    }

                    if (result.success) {
                        updateStatus("✅ ADB test successful", false)
                    } else {
                        updateStatus("❌ ADB test failed", true)
                        showAdbInstallationHelp()
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addDebug("💥 ADB TEST EXCEPTION: ${e.message}")
                    updateStatus("❌ ADB test error", true)
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun restartAdbServer() {
        addDebug("\n🔄 RESTARTING ADB SERVER...")
        setOperationInProgress(true, "Restarting ADB server...")

        scope.launch {
            try {
                val result = adbService.restartAdbServer()

                SwingUtilities.invokeLater {
                    addDebug("\n📊 ADB RESTART RESULT:")
                    addDebug("Success: ${result.success}")
                    addDebug("Output: ${result.output}")
                    if (result.error.isNotEmpty()) {
                        addDebug("Error: ${result.error}")
                    }

                    if (result.success) {
                        addDebug("✅ ADB server restarted successfully")
                        updateStatus("✅ ADB server restarted", false)
                    } else {
                        addDebug("❌ Failed to restart ADB server")
                        updateStatus("❌ ADB restart failed", true)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addDebug("💥 ADB RESTART EXCEPTION: ${e.message}")
                    updateStatus("❌ ADB restart error", true)
                }
            } finally {
                SwingUtilities.invokeLater {
                    setOperationInProgress(false)
                }
            }
        }
    }

    private fun showAdbInstallationHelp() {
        SwingUtilities.invokeLater {
            Messages.showInfoMessage(
                project,
                """
                ADB Not Found or Not Working!
                
                📥 INSTALLATION STEPS:
                
                1. Download Android SDK Platform Tools:
                   https://developer.android.com/studio/releases/platform-tools
                
                2. Extract to D:\Sdk\platform-tools\ (or any folder)
                
                3. Ensure adb.exe is at D:\Sdk\platform-tools\adb.exe
                
                4. Test by opening Command Prompt and running:
                   D:\Sdk\platform-tools\adb.exe version
                
                🔧 ENVIRONMENT SETUP (Optional):
                - Add D:\Sdk\platform-tools to your Windows PATH
                - Set ANDROID_HOME environment variable to D:\Sdk
                
                After installation, click "Test ADB" button to verify.
                """.trimIndent(),
                "ADB Installation Required"
            )
        }
    }

    private fun showTimeoutTroubleshooting() {
        SwingUtilities.invokeLater {
            Messages.showInfoMessage(
                project,
                """
                Pairing Timeout Troubleshooting:
                
                🔍 COMMON CAUSES:
                • Wrong IP address - check device wireless debugging screen
                • Wrong port - use PAIRING port, not connection port  
                • Different WiFi networks - both devices must be on same network
                • Pairing code expired - generate new code on device
                • Windows Firewall blocking ADB
                
                🛠️ QUICK FIXES:
                1. On Android device:
                   - Turn OFF wireless debugging
                   - Turn ON wireless debugging again
                   - Tap "Pair device with pairing code" again
                   - Use the NEW port and code
                
                2. Check network connection:
                   - Open Command Prompt
                   - Run: ping ${deviceIpField.text.trim()}
                   - Should get replies if network is OK
                
                3. Test ADB manually:
                   - Open Command Prompt  
                   - Run: D:\\Sdk\\platform-tools\\adb.exe pair ${deviceIpField.text.trim()}:${pairingPortField.text.trim()} ${pairingCodeField.text.trim()}
                
                4. Try mobile hotspot:
                   - Connect computer to phone's hotspot
                   - Try pairing again
                
                💡 TIP: Pairing codes expire in 30-60 seconds, so work quickly!
                """.trimIndent(),
                "Timeout Troubleshooting"
            )
        }
    }

    private fun showPairingTroubleshooting() {
        SwingUtilities.invokeLater {
            Messages.showInfoMessage(
                project,
                """
                Pairing Troubleshooting Guide:
                
                📱 ON YOUR ANDROID DEVICE:
                1. Go to Settings → Developer Options → Wireless debugging
                2. Turn OFF Wireless debugging, then turn it back ON
                3. Tap "Pair device with pairing code" (NOT "Pair device with QR code")
                4. Note the IP:PORT and 6-digit code shown
                
                💻 IN THIS DIALOG:
                1. Enter the PAIRING port (the one shown in step 3, like 37561)
                2. Enter the 6-digit code exactly as shown
                3. Click "Pair Device" within 30 seconds
                
                🔍 COMMON ISSUES:
                • Wrong port: Use PAIRING port, not connection port
                • Expired code: Generate new code if it takes too long
                • Network: Both devices must be on same WiFi
                • Firewall: Windows may block ADB (port 5555)
                
                📝 WHAT TO CHECK:
                • Device IP matches your computer's network range
                • No typos in pairing code
                • Wireless debugging is still enabled on device
                """.trimIndent(),
                "Pairing Help"
            )
        }
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
                Color.RED
            } else {
                Color(0x2E7D32)
            }
            addDebug("📊 Status: $message")
        }
    }

    private fun addDebug(message: String) {
        SwingUtilities.invokeLater {
            val timestamp = java.time.LocalTime.now().toString().substring(0, 8)
            debugOutput.append("[$timestamp] $message\n")
            debugOutput.caretPosition = debugOutput.document.length
        }
    }

    private fun clearDebug() {
        debugOutput.text = ""
        addDebug("🧹 Debug output cleared")
    }

    private fun copyDebugToClipboard() {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = java.awt.datatransfer.StringSelection(debugOutput.text)
        clipboard.setContents(selection, selection)
        addDebug("📋 Debug output copied to clipboard")
    }

    override fun createActions(): Array<Action> {
        return arrayOf(object : AbstractAction("Close") {
            override fun actionPerformed(e: ActionEvent?) {
                close(CANCEL_EXIT_CODE)
            }
        })
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}