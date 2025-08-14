package com.adbwireless.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Action to open ADB Wireless settings
 */
class OpenSettingsAction : AnAction(
    "ADB Wireless Settings",
    "Configure Android SDK and ADB settings",
    IconLoader.getIcon("/icons/wifi.svg", OpenSettingsAction::class.java)
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "ADB Wireless Manager"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }
}