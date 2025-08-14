package com.adbwireless.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.AbstractAction
import javax.swing.Action

/**
 * Dialog for re-pairing a device with both port and code fields
 */
class RepairDeviceDialog(
    private val project: Project,
    private val deviceIp: String,
    private val onRepair: (port: String, code: String) -> Unit
) : DialogWrapper(project) {

    private val pairingPortField = JBTextField()
    private val pairingCodeField = JBTextField()

    init {
        title = "Re-pair Device ($deviceIp)"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("Re-pairing Information") {
                row("Pairing Port:") {
                    cell(pairingPortField)
                        .focused()
                        .comment("Port from 'Pair device with pairing code' (NOT the connection port)")
                }
                row("Pairing Code:") {
                    cell(pairingCodeField)
                        .comment("6-digit code shown on your Android device")
                }
            }

            group("Instructions") {
                row {
                    text("""
                        📱 On your Android device:
                        1. Go to Settings → Developer Options → Wireless debugging
                        2. Tap "Pair device with pairing code"
                        3. Enter the PORT and CODE shown above
                        4. Click "Re-pair" below
                    """.trimIndent())
                }
            }
        }.apply {
            preferredSize = Dimension(400, 200)
            border = JBUI.Borders.empty(16)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("Re-pair") {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    performRepair()
                }
            },
            cancelAction
        )
    }

    private fun performRepair() {
        val port = pairingPortField.text.trim()
        val code = pairingCodeField.text.trim()

        // Validate inputs
        if (port.isEmpty()) {
            Messages.showErrorDialog(project, "Please enter the pairing port", "Validation Error")
            return
        }

        if (code.isEmpty()) {
            Messages.showErrorDialog(project, "Please enter the pairing code", "Validation Error")
            return
        }

        if (code.length != 6 || !code.all { it.isDigit() }) {
            Messages.showErrorDialog(project, "Pairing code must be exactly 6 digits", "Invalid Code")
            return
        }

        // All validation passed, perform the repair
        onRepair(port, code)
        close(OK_EXIT_CODE)
    }
}