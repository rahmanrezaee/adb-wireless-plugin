package com.adbwireless.dialogs

import com.adbwireless.services.QRCodeService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

/**
 * Dialog for generating QR codes for wireless debugging
 */
class GenerateQRCodeDialog(
    private val project: Project,
    private val deviceIp: String,
    private val devicePort: String = "5555"
) : DialogWrapper(project) {

    private val qrCodeService = project.service<QRCodeService>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI Components
    private val qrCodeLabel = JLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        border = JBUI.Borders.empty(16)
    }

    private val passwordLabel = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 16f)
        horizontalAlignment = SwingConstants.CENTER
        foreground = Color(0x2E7D32)
    }

    private val instructionsLabel = JLabel().apply {
        text = """
            <html>
                <div style="text-align: center; max-width: 400px;">
                    <h3>Wireless Debugging QR Code</h3>
                    <p>Scan this QR code with your Android device to enable wireless debugging:</p>
                    <ol style="text-align: left;">
                        <li>Go to <b>Settings → Developer Options → Wireless debugging</b></li>
                        <li>Tap <b>"Pair device with QR code"</b></li>
                        <li>Scan this QR code with your device</li>
                        <li>Enter the pairing code shown above</li>
                        <li>Your device will be ready for wireless debugging!</li>
                    </ol>
                </div>
            </html>
        """.trimIndent()
    }

    private var currentPassword: String = ""
    private var currentQRImage: BufferedImage? = null

    init {
        title = "Generate QR Code for Wireless Debugging"
        init()
        generateQRCode()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("QR Code") {
                row {
                    cell(qrCodeLabel)
                        .align(Align.CENTER)
                }
                row {
                    cell(passwordLabel)
                        .align(Align.CENTER)
                }
            }

            group("Instructions") {
                row {
                    cell(instructionsLabel)
                        .align(Align.CENTER)
                }
            }

            group("Actions") {
                row {
                    button("Regenerate QR Code") {
                        generateQRCode()
                    }.apply {
                        component.icon = AllIcons.Actions.Refresh
                    }
                    button("Copy Pairing Code") {
                        copyPasswordToClipboard()
                    }.apply {
                        component.icon = AllIcons.Actions.Copy
                    }
                }
            }
        }.apply {
            preferredSize = Dimension(500, 600)
            border = JBUI.Borders.empty(16)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    private fun generateQRCode() {
        scope.launch {
            try {
                val (password, qrImage) = qrCodeService.generateWirelessDebuggingQR(deviceIp, devicePort)
                currentPassword = password
                currentQRImage = qrImage

                SwingUtilities.invokeLater {
                    if (qrImage != null) {
                        val scaledImage = qrImage.getScaledInstance(250, 250, Image.SCALE_SMOOTH)
                        qrCodeLabel.icon = ImageIcon(scaledImage)
                        passwordLabel.text = "Pairing Code: $password"
                        passwordLabel.isVisible = true
                    } else {
                        qrCodeLabel.text = "Failed to generate QR code"
                        passwordLabel.isVisible = false
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    qrCodeLabel.text = "Error generating QR code: ${e.message}"
                    passwordLabel.isVisible = false
                }
            }
        }
    }

    private fun copyPasswordToClipboard() {
        if (currentPassword.isNotEmpty()) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(currentPassword)
            clipboard.setContents(selection, selection)
            
            // Show a brief notification
            passwordLabel.text = "Copied to clipboard!"
            passwordLabel.foreground = Color.BLUE
            
            // Reset after 2 seconds
            scope.launch {
                delay(2000)
                SwingUtilities.invokeLater {
                    passwordLabel.text = "Pairing Code: $currentPassword"
                    passwordLabel.foreground = Color(0x2E7D32)
                }
            }
        }
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}
