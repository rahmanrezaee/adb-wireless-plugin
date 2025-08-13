// OpenToolWindowAction.kt
package com.adbwireless.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action to simply open the ADB Wireless tool window
 */
class OpenToolWindowAction : AnAction(
    "Open ADB Wireless Manager",
    "Open the ADB Wireless Manager tool window",
    AllIcons.Nodes.DataTables
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ADB Wireless")
        toolWindow?.show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}