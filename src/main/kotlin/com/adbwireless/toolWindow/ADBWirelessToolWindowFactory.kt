package com.adbwireless.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory class for creating the ADB Wireless tool window
 */
class ADBWirelessToolWindowFactory : ToolWindowFactory {

    /**
     * Create the tool window content when the tool window is opened
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Create the main tool window component
        val adbWirelessWindow = ADBWirelessToolWindow(project)

        // Create content with the component
        val content = ContentFactory.getInstance()
            .createContent(adbWirelessWindow.getContent(), "", false)

        // Add content to tool window
        toolWindow.contentManager.addContent(content)
    }
}