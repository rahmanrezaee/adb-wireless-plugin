package com.adbwireless.dialogs

import com.adbwireless.services.QRCodeService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.datatransfer.StringSelection
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
        preferredSize = Dimension(300, 300)
        text = "Generating QR Code..."
    }

    private val passwordLabel = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 18f)
        horizontalAlignment = SwingConstants.CENTER
        foreground = Color(0x2E7D32)
    }

    private val qrDataLabel = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        font = Font("Monospaced", Font.PLAIN, 10)
        background = UIManager.getColor("Panel.background")
    }

    private val statusLabel = JLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        font = font.deriveFont(Font.ITALIC, 12f)
    }

    private var currentPassword: String = ""
    private var currentQRData: String = ""
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
                row {
                    cell(statusLabel)
                        .align(Align.CENTER)
                }
            }

            group("QR Code Data") {
                row("Raw Data:") {
                    scrollCell(qrDataLabel)
                        .align(Align.FILL)
                        .comment("This is the raw QR code content that your device will scan")
                }
            }

            group("Instructions") {
                row {
                    text("""
                        <html>
                            <div style="max-width: 400px;">
                                <h4>How to connect using QR code:</h4>
                                <ol>
                                    <li>On your Android device, go to <b>Settings → Developer Options → Wireless debugging</b></li>
                                    <li>Tap <b>"Pair device with QR code"</b></li>
                                    <li>Scan the QR code above with your device's camera</li>
                                    <li>Your device will automatically pair with Android Studio</li>
                                    <li>Return to ADB Wireless Manager and click "Connect" to start debugging!</li>
                                </ol>
                                <p><b>Note:</b> The pairing code shown above is for reference only - scanning the QR code handles everything automatically.</p>
                            </div>
                        </html>
                    """.trimIndent())
                }
            }

            group("Actions") {
                row {
                    button("Regenerate QR Code") {
                        generateQRCode()
                    }.apply {
                        component.icon = AllIcons.Actions.Refresh
                    }
                    button("Copy QR Data") {
                        copyQRDataToClipboard()
                    }.apply {
                        component.icon = AllIcons.Actions.Copy
                    }
                    button("Copy Pairing Code") {
                        copyPasswordToClipboard()
                    }.apply {
                        component.icon = AllIcons.Actions.Copy
                    }
                    button("Help") {
                        showDetailedHelp()
                    }.apply {
                        component.icon = AllIcons.General.ContextHelp
                    }
                }
            }
        }.apply {
            preferredSize = Dimension(550, 700)
            border = JBUI.Borders.empty(16)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    private fun generateQRCode() {
        statusLabel.text = "Generating QR code..."
        statusLabel.foreground = Color.GRAY
        qrCodeLabel.text = "Generating..."
        qrCodeLabel.icon = null
        passwordLabel.text = ""
        qrDataLabel.text = ""

        scope.launch {
            try {
                val (password, qrImage) = qrCodeService.generateWirelessDebuggingQR(deviceIp, devicePort)

                // Generate the QR data string for display
                val qrData = qrCodeService.generateWirelessDebuggingQRData(deviceIp, devicePort, password)

                currentPassword = password
                currentQRImage = qrImage
                currentQRData = qrData

                SwingUtilities.invokeLater {
                    if (qrImage != null) {
                        // Scale the image to fit the label
                        val scaledImage = qrImage.getScaledInstance(250, 250, Image.SCALE_SMOOTH)
                        qrCodeLabel.icon = ImageIcon(scaledImage)
                        qrCodeLabel.text = ""

                        passwordLabel.text = "Pairing Code: $password"
                        passwordLabel.foreground = Color(0x2E7D32)

                        qrDataLabel.text = qrData

                        statusLabel.text = "✅ QR code generated successfully!"
                        statusLabel.foreground = Color(0x2E7D32)
                    } else {
                        showError("Failed to generate QR code image")
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Error generating QR code: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showError(message: String) {
        qrCodeLabel.text = "❌ $message"
        qrCodeLabel.icon = null
        passwordLabel.text = ""
        statusLabel.text = message
        statusLabel.foreground = Color.RED
        qrDataLabel.text = ""
    }

    private fun copyQRDataToClipboard() {
        if (currentQRData.isNotEmpty()) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(currentQRData)
            clipboard.setContents(selection, selection)

            showTemporaryMessage("QR data copied to clipboard!", Color.BLUE)
        }
    }

    private fun copyPasswordToClipboard() {
        if (currentPassword.isNotEmpty()) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(currentPassword)
            clipboard.setContents(selection, selection)

            showTemporaryMessage("Pairing code copied to clipboard!", Color.BLUE)
        }
    }

    private fun showTemporaryMessage(message: String, color: Color) {
        val originalText = statusLabel.text
        val originalColor = statusLabel.foreground

        statusLabel.text = message
        statusLabel.foreground = color

        // Reset after 2 seconds
        scope.launch {
            delay(2000)
            SwingUtilities.invokeLater {
                statusLabel.text = originalText
                statusLabel.foreground = originalColor
            }
        }
    }

    private fun showDetailedHelp() {
        Messages.showInfoMessage(
            project,
            """
            Wireless ADB Debugging Setup:
            
            🔧 SETUP STEPS:
            1. Enable "Developer Options" on your Android device
            2. Go to Settings → Developer Options → Wireless debugging
            3. Turn ON "Wireless debugging"
            4. Tap "Pair device with QR code"
            5. Scan the QR code shown in this dialog
            
            📱 DEVICE REQUIREMENTS:
            • Android 11 (API level 30) or higher
            • Developer Options enabled
            • WiFi connection (same network as your computer)
            
            🔍 TROUBLESHOOTING:
            • Make sure both devices are on the same WiFi network
            • Try turning Wireless debugging OFF and ON again
            • If QR scanning fails, try manual pairing instead
            • Check your firewall settings if connection fails
            
            💡 TIP: After successful pairing, you can connect to this device 
            multiple times without re-pairing (until device reboot).
            """.trimIndent(),
            "Wireless ADB Help"
        )
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}