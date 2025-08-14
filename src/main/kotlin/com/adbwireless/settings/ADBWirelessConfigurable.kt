package com.adbwireless.settings

import com.adbwireless.services.SettingsService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.io.File
import javax.swing.JComponent

/**
 * Simplified ADB configuration settings page
 */
class ADBWirelessConfigurable : Configurable {

    private val settingsService = SettingsService.getInstance()

    // UI Components
    private val adbPathField = TextFieldWithBrowseButton().apply {
        addActionListener { e ->
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().apply {
                title = "Select ADB Executable"
                description = "Choose the ADB executable file"
                withFileFilter { file ->
                    val name = file.name.lowercase()
                    name == "adb" || name == "adb.exe"
                }
            }

            val virtualFile = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                descriptor,
                null,
                null
            )

            virtualFile?.let { file ->
                text = file.path
                // When user selects a file, mark as custom path
                settingsService.useCustomAdbPath = true
                settingsService.customAdbPath = file.path
                validateAdb()
            }
        }
    }

    private val statusLabel = JBLabel().apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.BOLD)
    }

    private val adbPathLabel = JBLabel().apply {
        font = Font("JetBrains Mono", Font.PLAIN, 11)
        foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
    }

    override fun getDisplayName(): String = "ADB Wireless Manager"

    override fun createComponent(): JComponent {
        // Load settings and initialize
        loadSettings()
        validateAdb()

        return panel {
            group("ADB Configuration") {
                row("ADB Path:") {
                    cell(adbPathField)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .comment("Path to ADB executable (defaults to Android SDK + /platform-tools/adb.exe)")
                        .onChanged {
                            validateAdb()
                        }

                    button("Reset") {
                        resetToDefault()
                    }.apply {
                        component.toolTipText = "Reset to default Android SDK path"
                    }
                }.layout(RowLayout.PARENT_GRID)
            }

            separator()

            group("Current Status") {
                row("Status:") {
                    cell(statusLabel)
                        .align(AlignX.FILL)
                }

                row("ADB Path:") {
                    scrollCell(adbPathLabel)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            separator()

            group("Instructions") {
                row {
                    scrollCell(createInstructionsTextArea())
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }
        }.apply {
            border = JBUI.Borders.empty(20)
            preferredSize = java.awt.Dimension(600, 400)
        }
    }

    private fun createInstructionsTextArea(): javax.swing.JTextArea {
        return javax.swing.JTextArea().apply {
            text = getInstructions()
            isEditable = false
            background = UIUtil.getPanelBackground()
            foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            border = JBUI.Borders.empty(8)
            lineWrap = true
            wrapStyleWord = true
        }
    }

    private fun resetToDefault() {
        // Clear custom settings
        settingsService.useCustomAdbPath = false
        settingsService.customAdbPath = ""

        // Try to detect SDK and set default path
        val detectedSdk = settingsService.detectAndroidSdkPath()
        if (detectedSdk != null) {
            settingsService.androidSdkPath = detectedSdk
            val defaultPath = settingsService.getAdbPathFromSdk(detectedSdk)
            adbPathField.text = defaultPath
        } else {
            // Fallback to system adb
            val defaultPath = if (System.getProperty("os.name").lowercase().contains("windows")) {
                "adb.exe"
            } else {
                "adb"
            }
            adbPathField.text = defaultPath
        }

        validateAdb()
    }

    private fun loadSettings() {
        val currentAdbPath = getCurrentAdbPath()
        adbPathField.text = currentAdbPath
    }

    private fun getCurrentAdbPath(): String {
        // If custom ADB path is set, use it
        if (settingsService.useCustomAdbPath && settingsService.customAdbPath.isNotEmpty()) {
            return settingsService.customAdbPath
        }

        // If Android SDK path is configured, use SDK + platform-tools/adb.exe
        if (settingsService.androidSdkPath.isNotEmpty()) {
            return settingsService.getAdbPathFromSdk(settingsService.androidSdkPath)
        }

        // Try auto-detected SDK path
        val detectedSdk = settingsService.detectAndroidSdkPath()
        if (detectedSdk != null) {
            return settingsService.getAdbPathFromSdk(detectedSdk)
        }

        // Default fallback
        return if (System.getProperty("os.name").lowercase().contains("windows")) {
            "adb.exe"
        } else {
            "adb"
        }
    }

    private fun validateAdb() {
        val adbPath = adbPathField.text.trim()

        if (adbPath.isEmpty()) {
            statusLabel.text = "❌ ADB Path Required"
            statusLabel.foreground = JBColor.RED
            adbPathLabel.text = "Please specify ADB path"
            return
        }

        // Check if file exists
        val adbFile = File(adbPath)
        if (!adbFile.exists()) {
            statusLabel.text = "❌ ADB File Not Found"
            statusLabel.foreground = JBColor.RED
            adbPathLabel.text = adbPath
            return
        }

        // Test ADB connection
        statusLabel.text = "🔄 Testing ADB..."
        statusLabel.foreground = JBColor.ORANGE

        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Testing ADB Connection", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val commandLine = GeneralCommandLine().apply {
                        exePath = adbPath
                        addParameters("version")
                    }

                    val processOutput = CapturingProcessHandler(commandLine).runProcess(10000)

                    ApplicationManager.getApplication().invokeLater {
                        if (processOutput.exitCode == 0) {
                            statusLabel.text = "✅ ADB Configuration Valid"
                            statusLabel.foreground = JBColor.GREEN
                            adbPathLabel.text = adbPath
                        } else {
                            statusLabel.text = "❌ ADB Test Failed"
                            statusLabel.foreground = JBColor.RED
                            adbPathLabel.text = "Exit code: ${processOutput.exitCode}"
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        statusLabel.text = "❌ ADB Test Error"
                        statusLabel.foreground = JBColor.RED
                        adbPathLabel.text = "Error: ${e.message}"
                    }
                }
            }
        })
    }

    private fun getInstructions(): String {
        return """
            📋 ADB Configuration:
            
            🔧 Default Behavior:
            • The ADB path defaults to your Android SDK + /platform-tools/adb.exe
            • Works automatically with standard Android Studio installations
            
            🛠️ Custom Configuration:
            • Click the browse button to select a specific ADB executable
            • Useful for custom Android SDK installations or standalone ADB
            
            ✅ Path Examples:
            • Windows: C:\Users\YourName\AppData\Local\Android\Sdk\platform-tools\adb.exe
            • macOS: ~/Library/Android/sdk/platform-tools/adb
            • Linux: ~/Android/Sdk/platform-tools/adb
            
            💡 The configuration is automatically validated when you change the path.
        """.trimIndent()
    }

    override fun isModified(): Boolean {
        val currentPath = adbPathField.text.trim()
        val savedPath = getCurrentAdbPath()
        return currentPath != savedPath
    }

    override fun apply() {
        val adbPath = adbPathField.text.trim()

        // Determine if this is a custom path or SDK-based path
        val detectedSdk = settingsService.detectAndroidSdkPath()
        val defaultSdkAdbPath = if (detectedSdk != null) {
            settingsService.getAdbPathFromSdk(detectedSdk)
        } else {
            ""
        }

        if (adbPath == defaultSdkAdbPath && detectedSdk != null) {
            // Using default SDK path
            settingsService.useCustomAdbPath = false
            settingsService.androidSdkPath = detectedSdk
            settingsService.customAdbPath = ""
        } else {
            // Using custom path
            settingsService.useCustomAdbPath = true
            settingsService.customAdbPath = adbPath
            settingsService.androidSdkPath = ""
        }

        validateAdb()
    }

    override fun reset() {
        loadSettings()
        validateAdb()
    }
}