package com.lizhuolun.apihelper.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * ApiHelper 工具窗口工厂。
 *
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        configureToolWindowPlacement(toolWindow)

        val panel = EndpointToolWindowPanel(project)
        EndpointToolWindowService.of(project).register(panel)
        toolWindow.setTitleActions(panel.createTitleActions())

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)

        // 面板创建后立即加载一次数据
        panel.refresh()
    }

    private fun configureToolWindowPlacement(toolWindow: ToolWindow) {
        if (toolWindow.anchor != ToolWindowAnchor.RIGHT) {
            toolWindow.setAnchor(ToolWindowAnchor.RIGHT, null)
        }
        if (toolWindow.isSplitMode) {
            toolWindow.setSplitMode(false, null)
        }
    }
}
