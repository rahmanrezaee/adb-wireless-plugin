package com.adbwireless.dialogs

import com.adbwireless.services.SettingsService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.*

/**
 * Initial ADB setup dialog - shows only once on first use
 */
class InitialAdbSetupDialog(private val project: Project) : DialogWrapper(project) {

    private val settingsService = SettingsService.getInstance()

    // UI Components
    private val autoDetectRadio = JBRadioButton("Auto-detect Android SDK", true)
    private val customPathRadio = JBRadioButton("Use custom ADB path", false)

    private val sdkPathField = TextFieldWithBrowseButton().apply {
        addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                title = "Select Android SDK Directory"
                description = "Choose the root directory of your Android SDK installation"
            }

            val virtualFile = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                descriptor,
                project,
                null
            )

            virtualFile?.let { file ->
                text = file.path
                validateCurrentSelection()
            }
        }
        isEnabled = false
    }

    private val customAdbField = TextFieldWithBrowseButton().apply {
        addActionListener {
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
                project,
                null
            )

            virtualFile?.let { file ->
                text = file.path
                validateCurrentSelection()
            }
        }
        isEnabled = false
    }

    private val statusLabel = JBLabel().apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }

    private val detectionResultLabel = JBLabel().apply {
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
    }

    init {
        title = "ADB Wireless Manager - Initial Setup"

        // Set up radio button group
        val buttonGroup = ButtonGroup()
        buttonGroup.add(autoDetectRadio)
        buttonGroup.add(customPathRadio)

        // Add listeners
        autoDetectRadio.addActionListener {
            sdkPathField.isEnabled = false
            customAdbField.isEnabled = false
            if (autoDetectRadio.isSelected) {
                performAutoDetection()
            }
        }

        customPathRadio.addActionListener {
            sdkPathField.isEnabled = true
            customAdbField.isEnabled = true
            clearStatus()
        }

        // Perform initial auto-detection
        performAutoDetection()

        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("ADB Configuration Required") {
                row {
                    text("Welcome! To use ADB Wireless Manager, we need to configure the Android Debug Bridge (ADB).").apply {
                        component.font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL)
                    }
                }
            }

            separator()

            group("Configuration Method") {
                row {
                    cell(autoDetectRadio)
                        .comment("Automatically find ADB from your Android SDK installation")
                }

                row {
                    cell(customPathRadio)
                        .comment("Specify custom paths if you have a non-standard setup")
                }
            }

            separator()

            group("SDK Path Configuration") {
                row("Android SDK Directory:") {
                    cell(sdkPathField)
                        .align(AlignX.FILL)
                        .comment("Path to your Android SDK root directory")
                        .enabled(customPathRadio.isSelected)
                }.layout(RowLayout.PARENT_GRID)
            }

            group("Custom ADB Path") {
                row("ADB Executable:") {
                    cell(customAdbField)
                        .align(AlignX.FILL)
                        .comment("Direct path to ADB executable file")
                        .enabled(customPathRadio.isSelected)
                }.layout(RowLayout.PARENT_GRID)
            }

            separator()

            group("Detection Results") {
                row {
                    cell(detectionResultLabel)
                }

                row {
                    cell(statusLabel)
                }

                row {
                    button("Test ADB Connection") {
                        testAdbConnection()
                    }.comment("Verify that ADB is working with current configuration")
                }
            }

            separator()

            group("Setup Instructions") {
                row {
                    text(getInstructions()).apply {
                        component.foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                    }
                }
            }
        }.apply {
            preferredSize = Dimension(650, 600)
            border = JBUI.Borders.empty(20)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("Save & Continue") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    saveConfiguration()
                }
            },
            cancelAction
        )
    }

    private fun performAutoDetection() {
        detectionResultLabel.text = "🔍 Auto-detecting Android SDK..."
        statusLabel.text = ""

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Detecting Android SDK", false) {
            override fun run(indicator: ProgressIndicator) {
                val detectedSdk = settingsService.detectAndroidSdkPath()

                ApplicationManager.getApplication().invokeLater {
                    if (detectedSdk != null) {
                        detectionResultLabel.text = "✅ Found Android SDK: $detectedSdk"
                        detectionResultLabel.foreground = JBColor.GREEN

                        val adbPath = settingsService.getAdbPathFromSdk(detectedSdk)
                        if (File(adbPath).exists()) {
                            statusLabel.text = "✅ ADB found: $adbPath"
                            statusLabel.foreground = JBColor.GREEN
                        } else {
                            statusLabel.text = "❌ ADB not found in SDK"
                            statusLabel.foreground = JBColor.RED
                        }
                    } else {
                        detectionResultLabel.text = "⚠️ No Android SDK auto-detected"
                        detectionResultLabel.foreground = JBColor.ORANGE
                        statusLabel.text = "Please select custom path or install Android SDK"
                        statusLabel.foreground = JBColor.ORANGE
                    }
                }
            }
        })
    }

    private fun validateCurrentSelection() {
        if (customPathRadio.isSelected) {
            val sdkPath = sdkPathField.text.trim()
            val customAdb = customAdbField.text.trim()

            when {
                customAdb.isNotEmpty() -> {
                    if (File(customAdb).exists()) {
                        statusLabel.text = "✅ Custom ADB found: $customAdb"
                        statusLabel.foreground = JBColor.GREEN
                    } else {
                        statusLabel.text = "❌ Custom ADB file not found"
                        statusLabel.foreground = JBColor.RED
                    }
                }
                sdkPath.isNotEmpty() -> {
                    val adbPath = settingsService.getAdbPathFromSdk(sdkPath)
                    if (File(adbPath).exists()) {
                        statusLabel.text = "✅ ADB found in SDK: $adbPath"
                        statusLabel.foreground = JBColor.GREEN
                    } else {
                        statusLabel.text = "❌ ADB not found in specified SDK"
                        statusLabel.foreground = JBColor.RED
                    }
                }
                else -> {
                    statusLabel.text = "Please specify SDK directory or custom ADB path"
                    statusLabel.foreground = JBColor.ORANGE
                }
            }
        }
    }

    private fun clearStatus() {
        statusLabel.text = "Configure paths above"
        statusLabel.foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
        detectionResultLabel.text = ""
    }

    private fun testAdbConnection() {
        val adbPath = getCurrentAdbPath()
        if (adbPath.isEmpty()) {
            Messages.showErrorDialog(project, "Please configure ADB path first", "No ADB Path")
            return
        }

        statusLabel.text = "🔄 Testing ADB connection..."
        statusLabel.foreground = JBColor.ORANGE

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Testing ADB", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val processBuilder = ProcessBuilder(adbPath, "version")
                    val process = processBuilder.start()
                    val exitCode = process.waitFor()

                    ApplicationManager.getApplication().invokeLater {
                        if (exitCode == 0) {
                            statusLabel.text = "✅ ADB connection test successful!"
                            statusLabel.foreground = JBColor.GREEN
                            Messages.showInfoMessage(project, "ADB is working correctly!", "Connection Test")
                        } else {
                            statusLabel.text = "❌ ADB connection test failed"
                            statusLabel.foreground = JBColor.RED
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        statusLabel.text = "❌ ADB test failed: ${e.message}"
                        statusLabel.foreground = JBColor.RED
                    }
                }
            }
        })
    }

    private fun getCurrentAdbPath(): String {
        return when {
            customPathRadio.isSelected -> {
                val customAdb = customAdbField.text.trim()
                if (customAdb.isNotEmpty()) {
                    customAdb
                } else {
                    val sdkPath = sdkPathField.text.trim()
                    if (sdkPath.isNotEmpty()) {
                        settingsService.getAdbPathFromSdk(sdkPath)
                    } else {
                        ""
                    }
                }
            }
            else -> {
                val detectedSdk = settingsService.detectAndroidSdkPath()
                if (detectedSdk != null) {
                    settingsService.getAdbPathFromSdk(detectedSdk)
                } else {
                    ""
                }
            }
        }
    }

    private fun saveConfiguration() {
        val adbPath = getCurrentAdbPath()

        if (adbPath.isEmpty() || !File(adbPath).exists()) {
            Messages.showErrorDialog(
                project,
                "Please configure a valid ADB path before continuing",
                "Configuration Required"
            )
            return
        }

        // Save configuration
        when {
            customPathRadio.isSelected -> {
                val customAdb = customAdbField.text.trim()
                if (customAdb.isNotEmpty()) {
                    settingsService.useCustomAdbPath = true
                    settingsService.customAdbPath = customAdb
                } else {
                    settingsService.useCustomAdbPath = false
                    settingsService.androidSdkPath = sdkPathField.text.trim()
                }
            }
            else -> {
                settingsService.useCustomAdbPath = false
                val detectedSdk = settingsService.detectAndroidSdkPath()
                if (detectedSdk != null) {
                    settingsService.androidSdkPath = detectedSdk
                }
            }
        }

        // Mark as configured
        settingsService.isFirstTimeSetup = false

        Messages.showInfoMessage(
            project,
            "ADB configuration saved successfully!\nYou can change these settings later in Settings → Tools → ADB Wireless Manager",
            "Setup Complete"
        )

        close(OK_EXIT_CODE)
    }

    private fun getInstructions(): String {
        return """
            📋 Setup Guide:
            
            🔍 Auto-detect (Recommended):
            • The plugin will automatically find your Android SDK
            • Works with standard Android Studio installations
            • Checks common SDK locations and environment variables
            
            🛠️ Custom Configuration:
            • Use if you have a non-standard setup
            • SDK Directory: Point to Android SDK root (contains platform-tools/)
            • Custom ADB: Point directly to adb.exe (Windows) or adb (Mac/Linux)
            
            ✅ Verification:
            • Use "Test ADB Connection" to verify your configuration
            • A successful test means you're ready to use wireless ADB
            
            💡 This setup only runs once. You can change settings later in:
            Settings → Tools → ADB Wireless Manager
        """.trimIndent()
    }
}