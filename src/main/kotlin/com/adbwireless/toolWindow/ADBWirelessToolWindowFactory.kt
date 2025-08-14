package com.adbwireless.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Simple factory for the unified tool window
 */
class ADBWirelessToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val adbWirelessWindow = ADBWirelessToolWindow(project)
        val content = ContentFactory.getInstance()
            .createContent(adbWirelessWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}