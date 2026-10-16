package com.lizhuolun.apihelper.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.lizhuolun.apihelper.cache.CacheChangeListener
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.ui.debug.EnvironmentManagePanel
import com.lizhuolun.apihelper.ui.debug.GlobalParamsPanel

/**
 * 工具窗口面板管理器，负责在 PSI 变更、配置变更或缓存刷新时通知各面板更新。
 *
 * 由于面板可能尚未被创建（工具窗口未打开），这里使用可空引用，
 * 并在 [EndpointToolWindowFactory.createToolWindowContent] 中完成注册。
 * 同时订阅 [CacheChangeListener] 消息总线，实现缓存层与 UI 层的解耦。
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/6/12
 */
@Service(Service.Level.PROJECT)
class EndpointToolWindowService(private val project: Project) {

    @Volatile
    private var panel: EndpointToolWindowPanel? = null

    @Volatile
    private var envPanel: EnvironmentManagePanel? = null

    @Volatile
    private var globalParamsPanel: GlobalParamsPanel? = null

    @Volatile
    private var mainContent: Content? = null

    init {
        project.messageBus.connect(project).subscribe(
            CacheChangeListener.TOPIC,
            object : CacheChangeListener {
                override fun onCacheChanged() {
                    refresh()
                }
            },
        )
    }

    /**
     * 注册实际创建好的工具窗口面板与关联 Tab。
     *
     * @param panel 接口主面板
     * @param envPanel 前置 URL 管理面板
     * @param globalParamsPanel 全局参数管理面板
     * @param mainContent 接口主 Tab Content
     */
    fun register(
        panel: EndpointToolWindowPanel,
        envPanel: EnvironmentManagePanel? = null,
        globalParamsPanel: GlobalParamsPanel? = null,
        mainContent: Content? = null,
    ) {
        this.panel = panel
        this.envPanel = envPanel
        this.globalParamsPanel = globalParamsPanel
        this.mainContent = mainContent
    }

    /**
     * 触发工具窗口刷新；若面板尚未创建则静默忽略。
     */
    fun refresh() {
        if (project.isDisposed) return
        panel?.refresh()
    }

    /**
     * 重新加载调试面板与“前置 URL / 全局参数”Tab 的配置。
     */
    fun reloadDebugEnvironments() {
        if (project.isDisposed) return
        panel?.reloadDebugEnvironments()
        envPanel?.loadFromSettings()
        globalParamsPanel?.loadFromSettings()
    }

    /**
     * 打开工具窗口，切换到“接口”主 Tab 并展开底部的“调试”面板加载指定端点。
     *
     * @param info Controller 端点映射
     */
    fun debugEndpoint(info: HttpMappingInfo) {
        if (project.isDisposed) return
        val cache = com.lizhuolun.apihelper.cache.BilateralMappingCacheService.of(project)
        val item = ApplicationManager.getApplication().runReadAction(Computable {
            val method = info.resolveMethod() ?: return@Computable null
            EndpointTreeItem.from(info, method, hasCounterpart = cache.hasCounterpart(info))
        }) ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ApiHelper")
        toolWindow?.show {
            mainContent?.let { toolWindow.contentManager.setSelectedContent(it) }
            panel?.showDebugPanel(item)
        } ?: panel?.showDebugPanel(item)
    }

    companion object {

        /**
         * 便捷获取入口。
         *
         * @param project 当前工程
         * @return 项目级工具窗口服务
         */
        fun of(project: Project): EndpointToolWindowService =
            project.getService(EndpointToolWindowService::class.java)
    }
}
