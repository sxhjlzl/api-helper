package com.lizhuolun.apihelper.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.ui.contract.ContractReportPanel
import com.lizhuolun.apihelper.ui.debug.EndpointDebugPanel
import com.lizhuolun.apihelper.ui.debug.EnvironmentManagePanel
import com.lizhuolun.apihelper.ui.debug.GlobalParamsPanel
import com.lizhuolun.apihelper.ui.debug.RequestHistoryPanel

/**
 * ApiHelper 工具窗口工厂。
 *
 * 使用 IDE 原生 ContentManager 注册 5 个平级的一级工作区标签页：
 * 1. 接口（Controller / Feign 端点树浏览）
 * 2. 调试（请求与响应上下分区的轻量 API 调试器）
 * 3. 前置 URL（多环境 Base URL 表格管理）
 * 4. 请求历史（历史请求列表与一键回放）
 * 5. 全局参数（全局公共 Header 表格管理）
 *
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        configureToolWindowPlacement(toolWindow)

        val mainPanel = EndpointToolWindowPanel(project)
        val contractPanel = ContractReportPanel(project)
        val debugPanel = EndpointDebugPanel(project)
        val envPanel = EnvironmentManagePanel(project)
        val globalParamsPanel = GlobalParamsPanel()
        lateinit var historyPanel: RequestHistoryPanel

        val contentFactory = ContentFactory.getInstance()
        val mainContent = contentFactory.createContent(
            mainPanel,
            ApiHelperBundle.message("toolwindow.nav.interfaces"),
            false,
        ).apply {
            isCloseable = false
        }

        val contractContent = contentFactory.createContent(
            contractPanel,
            ApiHelperBundle.message("toolwindow.tab.contract"),
            false,
        ).apply {
            isCloseable = false
        }

        val debugContent = contentFactory.createContent(
            debugPanel,
            ApiHelperBundle.message("toolwindow.nav.debug"),
            false,
        ).apply {
            isCloseable = false
        }

        val envContent = contentFactory.createContent(
            envPanel,
            ApiHelperBundle.message("toolwindow.tab.environments"),
            false,
        ).apply {
            isCloseable = false
        }

        historyPanel = RequestHistoryPanel(project) { entry ->
            toolWindow.contentManager.setSelectedContent(debugContent)
            debugPanel.restoreHistoryEntry(entry)
        }
        val historyContent = contentFactory.createContent(
            historyPanel,
            ApiHelperBundle.message("toolwindow.tab.history"),
            false,
        ).apply {
            isCloseable = false
        }

        val globalParamsContent = contentFactory.createContent(
            globalParamsPanel,
            ApiHelperBundle.message("toolwindow.tab.global.params"),
            false,
        ).apply {
            isCloseable = false
        }

        mainPanel.onDebugRequested = { item ->
            toolWindow.contentManager.setSelectedContent(debugContent)
            debugPanel.loadEndpoint(item)
        }

        debugPanel.onHistoryUpdated = {
            historyPanel.refreshHistory()
        }

        val contentManager = toolWindow.contentManager
        contentManager.addContent(mainContent)
        contentManager.addContent(contractContent)
        contentManager.addContent(debugContent)
        contentManager.addContent(envContent)
        contentManager.addContent(historyContent)
        contentManager.addContent(globalParamsContent)

        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                when (event.content) {
                    contractContent -> contractPanel.refresh()
                    debugContent -> debugPanel.reloadEnvironments()
                    envContent -> envPanel.loadFromSettings()
                    historyContent -> historyPanel.refreshHistory()
                    globalParamsContent -> globalParamsPanel.loadFromSettings()
                }
            }
        })

        EndpointToolWindowService.of(project).register(
            panel = mainPanel,
            debugPanel = debugPanel,
            envPanel = envPanel,
            globalParamsPanel = globalParamsPanel,
            debugContent = debugContent,
        )
        toolWindow.setTitleActions(mainPanel.createTitleActions())

        // 面板创建后立即加载一次数据
        mainPanel.refresh()
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
