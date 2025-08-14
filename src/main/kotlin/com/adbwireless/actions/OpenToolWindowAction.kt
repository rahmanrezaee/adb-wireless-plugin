package com.adbwireless.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Icon

/**
 * Simple action to open the tool window
 */
class OpenToolWindowAction : AnAction(
    "Open ADB Wireless Manager",
    "Open the ADB Wireless Manager tool window",
    IconLoader.getIcon("/icons/wifi.svg", OpenToolWindowAction::class.java)
) {

    companion object {
        val WIFI_ICON: Icon = IconLoader.getIcon("/icons/wifi.svg", OpenToolWindowAction::class.java)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ADB Wireless")
        toolWindow?.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}