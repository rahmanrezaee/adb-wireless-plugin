package com.adbwireless.dialogs

import com.adbwireless.services.UsbQuickConnector
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import javax.swing.*

class SelectUsbDeviceDialog(
    project: Project?,
    private val devices: List<UsbQuickConnector.UsbDevice>
) : DialogWrapper(project) {

    private val list = JBList(devices.map { it.displayName() })
    var selectedDevice: UsbQuickConnector.UsbDevice? = null
        private set

    init {
        title = "Select USB Device"
        init()
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        if (devices.isNotEmpty()) list.selectedIndex = 0
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        panel.add(JLabel("Choose a USB-connected device to enable Wi‑Fi ADB:"), BorderLayout.NORTH)
        panel.add(JScrollPane(list), BorderLayout.CENTER)
        return panel
    }

    override fun doOKAction() {
        val idx = list.selectedIndex
        if (idx in devices.indices) {
            selectedDevice = devices[idx]
            super.doOKAction()
        }
    }
}