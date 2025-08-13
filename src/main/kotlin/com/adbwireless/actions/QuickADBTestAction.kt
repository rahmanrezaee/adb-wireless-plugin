package com.adbwireless.actions

import com.adbwireless.services.ADBService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import java.awt.Dimension
import java.awt.Font
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Quick action to test ADB connectivity and show debug information
 */
class QuickADBTestAction : AnAction(
    "Test ADB Connection",
    "Run comprehensive ADB test and show debug information",
    AllIcons.General.InspectionsOK
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val adbService = project.service<ADBService>()

        val dialog = ADBTestDialog(project, adbService)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Dialog that shows comprehensive ADB test results
 */
class ADBTestDialog(
    private val project: com.intellij.openapi.project.Project,
    private val adbService: ADBService
) : DialogWrapper(project) {

    private val outputArea = JBTextArea().apply {
        isEditable = false
        font = Font("Monospaced", Font.PLAIN, 12)
        text = "Running ADB tests...\n"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        title = "ADB Connection Test"
        init()
        runTests()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(outputArea)
        scrollPane.preferredSize = Dimension(600, 400)
        return scrollPane
    }

    private fun runTests() {
        scope.launch {
            try {
                appendOutput("🚀 Starting comprehensive ADB test...\n")
                appendOutput("======================="  + "\n")

                // Test 1: Basic connectivity
                appendOutput("1️⃣ Testing basic ADB connectivity...\n")
                val connectivityResult = adbService.testAdbConnectivity()
                appendOutput("Result: ${if (connectivityResult.success) "✅ SUCCESS" else "❌ FAILED"}\n")
                appendOutput("Output:\n${connectivityResult.output}\n")
                if (connectivityResult.error.isNotEmpty()) {
                    appendOutput("Error:\n${connectivityResult.error}\n")
                }
                appendOutput("\n")

                // Test 2: ADB version
                appendOutput("2️⃣ Checking ADB version...\n")
                val versionResult = adbService.executeAdbCommand("version")
                appendOutput("Result: ${if (versionResult.success) "✅ SUCCESS" else "❌ FAILED"}\n")
                appendOutput("Output:\n${versionResult.output}\n")
                if (versionResult.error.isNotEmpty()) {
                    appendOutput("Error:\n${versionResult.error}\n")
                }
                appendOutput("\n")

                // Test 3: List devices
                appendOutput("3️⃣ Listing connected devices...\n")
                val devicesResult = adbService.listDevices()
                appendOutput("Result: ${if (devicesResult.success) "✅ SUCCESS" else "❌ FAILED"}\n")
                appendOutput("Output:\n${devicesResult.output}\n")
                if (devicesResult.error.isNotEmpty()) {
                    appendOutput("Error:\n${devicesResult.error}\n")
                }
                appendOutput("\n")

                // Test 4: ADB path info
                appendOutput("4️⃣ ADB path information...\n")
                val adbPath = adbService.getAdbPathForDisplay()
                appendOutput("ADB Path: $adbPath\n")

                if (adbPath != "ADB not found") {
                    val file = java.io.File(adbPath)
                    appendOutput("File exists: ${file.exists()}\n")
                    appendOutput("File size: ${file.length()} bytes\n")
                    appendOutput("Can execute: ${file.canExecute()}\n")
                }
                appendOutput("\n")

                // Test 5: Network info
                appendOutput("5️⃣ Network information...\n")
                try {
                    val localHost = java.net.InetAddress.getLocalHost()
                    appendOutput("Local IP: ${localHost.hostAddress}\n")
                    appendOutput("Hostname: ${localHost.hostName}\n")
                } catch (e: Exception) {
                    appendOutput("Error getting network info: ${e.message}\n")
                }
                appendOutput("\n")

                // Test 6: Environment variables
                appendOutput("6️⃣ Environment variables...\n")
                appendOutput("ANDROID_HOME: ${System.getenv("ANDROID_HOME") ?: "Not set"}\n")
                appendOutput("ANDROID_SDK_ROOT: ${System.getenv("ANDROID_SDK_ROOT") ?: "Not set"}\n")
                appendOutput("JAVA_HOME: ${System.getenv("JAVA_HOME") ?: "Not set"}\n")
                appendOutput("\n")

                appendOutput("=========================="  + "\n")

                val overallSuccess = connectivityResult.success && versionResult.success
                if (overallSuccess) {
                    appendOutput("🎉 OVERALL RESULT: ✅ ADB IS READY FOR WIRELESS DEBUGGING!\n")
                    appendOutput("\n📱 Next steps:\n")
                    appendOutput("1. Enable Wireless debugging on your Android device\n")
                    appendOutput("2. Use the Add Device dialog to pair your device\n")
                    appendOutput("3. Connect and start debugging wirelessly!\n")
                } else {
                    appendOutput("❌ OVERALL RESULT: ADB IS NOT READY\n")
                    appendOutput("\n🔧 Required actions:\n")
                    appendOutput("1. Install Android SDK Platform Tools\n")
                    appendOutput("2. Ensure ADB is accessible from command line\n")
                    appendOutput("3. Set up ANDROID_HOME environment variable (optional)\n")
                    appendOutput("4. Restart IntelliJ IDEA after installation\n")

                    showInstallationHelp()
                }

            } catch (e: Exception) {
                appendOutput("\n💥 Exception during testing: ${e.message}\n")
                e.printStackTrace()
            }
        }
    }

    private fun appendOutput(text: String) {
        SwingUtilities.invokeLater {
            outputArea.append(text)
            outputArea.caretPosition = outputArea.document.length
        }
    }

    private fun showInstallationHelp() {
        SwingUtilities.invokeLater {
            val result = Messages.showYesNoDialog(
                project,
                "ADB is not properly installed. Would you like to see installation instructions?",
                "ADB Installation Required",
                "Show Instructions",
                "Close",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                Messages.showInfoMessage(
                    project,
                    """
                    ADB Installation Guide:
                    
                    📥 DOWNLOAD:
                    Download Android SDK Platform Tools from:
                    https://developer.android.com/studio/releases/platform-tools
                    
                    📁 INSTALLATION:
                    1. Extract the downloaded zip file
                    2. Copy to: D:\Sdk\platform-tools\ (recommended)
                    3. Ensure adb.exe is at: D:\Sdk\platform-tools\adb.exe
                    
                    🔧 VERIFICATION:
                    Open Command Prompt and run:
                    D:\Sdk\platform-tools\adb.exe version
                    
                    You should see: "Android Debug Bridge version..."
                    
                    ⚙️ OPTIONAL - Add to PATH:
                    1. Open Windows Settings → System → About
                    2. Click "Advanced system settings"
                    3. Click "Environment Variables"
                    4. Add D:\Sdk\platform-tools to your PATH
                    
                    After installation, restart IntelliJ and run this test again.
                    """.trimIndent(),
                    "ADB Installation Instructions"
                )
            }
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("Copy Output") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val selection = java.awt.datatransfer.StringSelection(outputArea.text)
                    clipboard.setContents(selection, selection)
                    appendOutput("\n📋 Output copied to clipboard\n")
                }
            },
            cancelAction
        )
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}