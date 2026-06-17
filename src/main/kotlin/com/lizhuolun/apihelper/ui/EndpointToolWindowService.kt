package com.lizhuolun.apihelper.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.lizhuolun.apihelper.cache.CacheChangeListener

/**
 * 工具窗口面板管理器，负责在 PSI 变更或缓存刷新时通知面板更新。
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
     * 注册实际创建好的面板。
     *
     * @param panel 工具窗口面板
     */
    fun register(panel: EndpointToolWindowPanel) {
        this.panel = panel
    }

    /**
     * 触发工具窗口刷新；若面板尚未创建则静默忽略。
     */
    fun refresh() {
        if (project.isDisposed) return
        panel?.refresh()
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
