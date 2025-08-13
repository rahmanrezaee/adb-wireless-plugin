package com.adbwireless.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import kotlinx.coroutines.*
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.SwingUtilities

// ✅ ADD THESE MISSING IMPORTS
import javax.swing.AbstractAction
import javax.swing.Action
import com.intellij.execution.util.ExecUtil
import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

/**
 * Direct ADB test action that bypasses the service layer
 */
class DirectADBTestAction : AnAction(
    "Direct ADB Test",
    "Test ADB commands directly without service layer",
    AllIcons.General.InspectionsOK
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dialog = DirectADBTestDialog(project)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

class DirectADBTestDialog(private val project: com.intellij.openapi.project.Project) : DialogWrapper(project) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val ipField = JBTextField("172.16.1.105")
    private val portField = JBTextField("35299")
    private val codeField = JBTextField("207347")

    private val outputArea = JBTextArea().apply {
        isEditable = false
        rows = 25
        columns = 80
        font = Font("Monospaced", Font.PLAIN, 11)
        text = "Direct ADB Test - Bypassing Plugin Service Layer\n${"=".repeat(60)}\n\n"
    }

    init {
        title = "Direct ADB Test"
        init()
        runInitialTest()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("Test Parameters") {
                row("Device IP:") {
                    cell(ipField).comment("Your Android device IP")
                }
                row("Pairing Port:") {
                    cell(portField).comment("Port from 'Pair device with pairing code'")
                }
                row("Pairing Code:") {
                    cell(codeField).comment("6-digit code from device")
                }
                row {
                    button("🔗 Test Pairing") {
                        testPairing()
                    }
                    button("🧪 Test ADB Version") {
                        testAdbVersion()
                    }
                    button("📱 List Devices") {
                        testListDevices()
                    }
                    button("🧹 Clear Output") {
                        clearOutput()
                    }
                }
            }

            group("Output") {
                row {
                    scrollCell(outputArea)
                        .align(Align.FILL)
                        .resizableColumn()
                }
            }
        }.apply {
            preferredSize = Dimension(800, 600)
        }
    }

    private fun runInitialTest() {
        appendOutput("🚀 Starting direct ADB tests...\n")
        appendOutput("This bypasses the plugin service layer to test raw ADB functionality.\n\n")

        scope.launch {
            testAdbVersion()
        }
    }

    private fun testAdbVersion() {
        appendOutput("1️⃣ Testing ADB version...\n")

        scope.launch {
            try {
                val result = executeCommand("D:\\Sdk\\platform-tools\\adb.exe", "version")

                SwingUtilities.invokeLater {
                    if (result.success) {
                        appendOutput("✅ ADB Version Test: SUCCESS\n")
                        appendOutput("Output: ${result.output}\n")
                    } else {
                        appendOutput("❌ ADB Version Test: FAILED\n")
                        appendOutput("Error: ${result.error}\n")
                    }
                    appendOutput("Execution time: ${result.executionTimeMs}ms\n\n")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendOutput("💥 Exception in version test: ${e.message}\n\n")
                }
            }
        }
    }

    private fun testListDevices() {
        appendOutput("2️⃣ Testing device list...\n")

        scope.launch {
            try {
                val result = executeCommand("D:\\Sdk\\platform-tools\\adb.exe", "devices", "-l")

                SwingUtilities.invokeLater {
                    if (result.success) {
                        appendOutput("✅ List Devices Test: SUCCESS\n")
                        appendOutput("Output: ${result.output}\n")
                    } else {
                        appendOutput("❌ List Devices Test: FAILED\n")
                        appendOutput("Error: ${result.error}\n")
                    }
                    appendOutput("Execution time: ${result.executionTimeMs}ms\n\n")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendOutput("💥 Exception in devices test: ${e.message}\n\n")
                }
            }
        }
    }

    private fun testPairing() {
        val ip = ipField.text.trim()
        val port = portField.text.trim()
        val code = codeField.text.trim()

        if (ip.isEmpty() || port.isEmpty() || code.isEmpty()) {
            appendOutput("❌ Please fill in all fields before testing pairing\n\n")
            return
        }

        appendOutput("3️⃣ Testing ADB pairing...\n")
        appendOutput("Command: adb pair $ip:$port $code\n")
        appendOutput("⏳ Executing with 45 second timeout...\n")

        scope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // Show progress updates
                val progressJob = launch {
                    var seconds = 0
                    while (isActive) {
                        delay(1000)
                        seconds++
                        SwingUtilities.invokeLater {
                            appendOutput("⏰ ${seconds}s elapsed...\n")
                        }
                    }
                }

                val result = executeCommand("D:\\Sdk\\platform-tools\\adb.exe", "pair", "$ip:$port", code, timeoutSeconds = 45)
                progressJob.cancel()

                val totalTime = System.currentTimeMillis() - startTime

                SwingUtilities.invokeLater {
                    appendOutput("\n📊 PAIRING RESULT:\n")
                    appendOutput("Success: ${result.success}\n")
                    appendOutput("Exit Code: ${result.exitCode}\n")
                    appendOutput("Timed Out: ${result.timedOut}\n")
                    appendOutput("Total Time: ${totalTime}ms\n")
                    appendOutput("Output: '${result.output}'\n")
                    appendOutput("Error: '${result.error}'\n")

                    if (result.success) {
                        appendOutput("🎉 PAIRING SUCCESSFUL!\n")
                        appendOutput("Try connecting with: adb connect $ip:5555\n")
                    } else if (result.timedOut) {
                        appendOutput("⏰ PAIRING TIMED OUT!\n")
                        appendOutput("This suggests network connectivity issues.\n")
                    } else {
                        appendOutput("❌ PAIRING FAILED!\n")
                        appendOutput("Check the error message above for details.\n")
                    }
                    appendOutput("\n${"=".repeat(60)}\n\n")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendOutput("💥 Exception during pairing test: ${e.message}\n")
                    e.printStackTrace()
                    appendOutput("\n${"=".repeat(60)}\n\n")
                }
            }
        }
    }

    /**
     * Direct command execution using IntelliJ utilities
     */
    private suspend fun executeCommand(vararg command: String, timeoutSeconds: Long = 30): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Create GeneralCommandLine from command array
            val commandLine = GeneralCommandLine(command.toList())
            commandLine.withWorkDirectory(null as File?) // No specific working directory

            val result = ExecUtil.execAndGetOutput(commandLine)

            val executionTime = System.currentTimeMillis() - startTime

            CommandResult(
                success = result.exitCode == 0,
                output = result.stdout.trim(),
                error = result.stderr.trim(),
                exitCode = result.exitCode,
                timedOut = result.isTimeout,
                executionTimeMs = executionTime
            )

        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            CommandResult(
                success = false,
                output = "",
                error = "Exception: ${e.message}",
                exitCode = -1,
                timedOut = false,
                executionTimeMs = executionTime
            )
        }
    }

    private fun appendOutput(text: String) {
        SwingUtilities.invokeLater {
            outputArea.append(text)
            outputArea.caretPosition = outputArea.document.length
        }
    }

    private fun clearOutput() {
        outputArea.text = "Output cleared.\n"
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("Copy Output") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val selection = java.awt.datatransfer.StringSelection(outputArea.text)
                    clipboard.setContents(selection, selection)
                    appendOutput("📋 Output copied to clipboard\n")
                }
            },
            cancelAction
        )
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int,
        val timedOut: Boolean,
        val executionTimeMs: Long
    )
}