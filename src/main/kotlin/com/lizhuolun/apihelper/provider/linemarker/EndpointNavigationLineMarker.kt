package com.lizhuolun.apihelper.provider.linemarker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.provider.UastElementUtils
import com.lizhuolun.apihelper.ui.EndpointToolWindowService
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * 行内导航图标公共基类，统一处理：
 * - 左键点击 -> 跳转到对端方法（多目标时显示选择 popup）
 * - 右键点击 -> 弹出"复制 URL"菜单，复制完整 URL 到剪贴板
 *
 * 注意：右键事件不会经过 navHandler，必须通过 GutterIconRenderer.getPopupMenuActions()
 * 暴露给 IDEA 的 gutter 上下文菜单系统。
 */
abstract class EndpointNavigationLineMarker : LineMarkerProviderDescriptor() {

    protected abstract val markerIcon: Icon
    protected abstract val titleKey: String
    protected abstract val accessibleKey: String

    /**
     * tooltip 底部操作提示。
     */
    protected open val tooltipHintKey: String = "linemarker.tooltip.hint"

    /**
     * 是否在 gutter 右键菜单中提供操作。
     */
    protected open val popupActionsEnabled: Boolean = true

    /**
     * 是否在 gutter 右键菜单中提供调试入口。
     */
    protected open val debugPopupActionEnabled: Boolean = false

    /**
     * 当前 LineMarker 是否应该对该方法生效。
     */
    protected abstract fun isApplicable(method: PsiMethod): Boolean

    /**
     * 判断当前方法在工程里是否存在对端匹配项。
     *
     * 实现应尽可能轻量，避免在 LineMarker 渲染（read action）期间触发全工程扫描。
     */
    protected abstract fun hasCounterpart(project: Project, method: PsiMethod): Boolean

    /**
     * 查找跳转目标。
     */
    protected abstract fun findTargets(project: Project, method: PsiMethod): List<HttpMappingInfo>

    /**
     * 计算当前方法自身的映射信息（用于右键复制与调试）。
     */
    protected abstract fun resolveSelfMapping(project: Project, method: PsiMethod): HttpMappingInfo?

    final override fun getName(): String = ApiHelperBundle.message(accessibleKey)

    final override fun getIcon(): Icon = markerIcon

    final override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val project = element.project
        if (project.isDisposed) return null
        if (DumbService.isDumb(project)) return null

        val method = UastElementUtils.extractMethodForNameAnchor(element) ?: return null
        if (!isApplicable(method)) return null

        // 只有存在对端匹配时才显示 gutter 图标，避免无对应接口的方法出现冗余图标
        if (!hasCounterpart(project, method)) return null

        val selfMapping = resolveSelfMapping(project, method)
        val selfUrl = selfMapping?.url

        val tooltipTitle = ApiHelperBundle.message(titleKey)
        val tooltipHint = ApiHelperBundle.message(tooltipHintKey)
        val tooltip = if (selfUrl.isNullOrEmpty()) {
            "$tooltipTitle<br/>$tooltipHint"
        } else {
            "$tooltipTitle<br/><code>$selfUrl</code><br/>$tooltipHint"
        }

        return EndpointLineMarkerInfo(
            element = element,
            icon = markerIcon,
            tooltipText = tooltip,
            accessibleName = ApiHelperBundle.message(accessibleKey),
            project = project,
            titleKey = titleKey,
            targetProvider = ::findTargets,
            selfMapping = selfMapping,
            popupActionsEnabled = popupActionsEnabled,
            debugPopupActionEnabled = debugPopupActionEnabled,
        )
    }

    /**
     * 自定义 LineMarkerInfo：
     * - navHandler 处理左键跳转
     * - createGutterRenderer 返回的 renderer 暴露 popup actions，处理右键复制 URL
     */
    private class EndpointLineMarkerInfo(
        element: PsiElement,
        icon: Icon,
        tooltipText: String,
        private val accessibleName: String,
        private val project: Project,
        private val titleKey: String,
        private val targetProvider: (Project, PsiMethod) -> List<HttpMappingInfo>,
        private val selfMapping: HttpMappingInfo?,
        private val popupActionsEnabled: Boolean,
        private val debugPopupActionEnabled: Boolean,
    ) : LineMarkerInfo<PsiElement>(
        element,
        element.textRange,
        icon,
        { tooltipText },
        // 注意：不能在 LineMarkerInfo 中长期持有 PsiMethod，点击时用最新的锚点元素重新解析方法
        { event, anchor -> navigateToTargets(event, project, anchor, targetProvider, titleKey) },
        GutterIconRenderer.Alignment.RIGHT,
        { accessibleName },
    ) {

        override fun createGutterRenderer(): GutterIconRenderer {
            return object : LineMarkerGutterIconRenderer<PsiElement>(this) {
                override fun getPopupMenuActions(): ActionGroup? {
                    if (!popupActionsEnabled) return null
                    val mapping = selfMapping ?: return null
                    val url = mapping.url
                    if (url.isBlank()) return null
                    val group = DefaultActionGroup()
                    if (debugPopupActionEnabled) {
                        group.add(object : AnAction(ApiHelperBundle.message("popup.debug.endpoint.action"), null, AllIcons.Actions.Execute) {
                            override fun actionPerformed(e: AnActionEvent) {
                                EndpointToolWindowService.of(project).debugEndpoint(mapping)
                            }
                        })
                        group.addSeparator()
                    }
                    val label = ApiHelperBundle.message("popup.copy.url.action")
                    group.add(object : AnAction(label, null, AllIcons.Actions.Copy) {
                        override fun actionPerformed(e: AnActionEvent) {
                            copyUrlAndNotify(project, url)
                        }
                    })
                    return group
                }

                override fun equals(other: Any?): Boolean = super.equals(other)
                override fun hashCode(): Int = super.hashCode()
            }
        }
    }

    companion object {

        /**
         * 未找到匹配的对端接口时发送气泡通知，避免点击图标后无任何反馈。
         */
        private fun notifyNoTargets(project: Project) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ApiHelper")
                .createNotification(
                    ApiHelperBundle.message("notification.no.targets"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }

        /**
         * 把 URL 写入系统剪贴板并发送一条气泡通知。
         */
        private fun copyUrlAndNotify(project: Project, url: String) {
            CopyPasteManager.getInstance().setContents(StringSelection(url))
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ApiHelper")
                .createNotification(
                    ApiHelperBundle.message("notification.url.copied", url),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }

        /**
         * 跳转到对端目标方法。
         * - 单目标：直接 navigate
         * - 多目标：用 PsiTargetNavigator 展示选择 popup
         *
         * 查找目标可能触发兜底全量扫描，必须放在可取消的后台进度中执行，避免阻塞 EDT。
         */
        private fun navigateToTargets(
            event: MouseEvent,
            project: Project,
            anchor: PsiElement,
            targetProvider: (Project, PsiMethod) -> List<HttpMappingInfo>,
            titleKey: String,
        ) {
            if (!anchor.isValid) return
            val sourceMethod = UastElementUtils.extractMethodForNameAnchor(anchor) ?: return
            val targets = try {
                ProgressManager.getInstance()
                    .runProcessWithProgressSynchronously<List<HttpMappingInfo>, RuntimeException>(
                        { targetProvider(project, sourceMethod) },
                        ApiHelperBundle.message("progress.finding.targets"),
                        true,
                        project,
                    )
            } catch (e: ProcessCanceledException) {
                // 用户主动取消查找时静默返回
                return
            }
            if (targets.isEmpty()) {
                notifyNoTargets(project)
                return
            }
            val validTargets = ApplicationManager.getApplication().runReadAction(Computable {
                targets.mapNotNull { it.resolveMethod() }
                    .filterIsInstance<NavigatablePsiElement>()
            })
            if (validTargets.isEmpty()) {
                notifyNoTargets(project)
                return
            }
            if (validTargets.size == 1) {
                validTargets[0].navigate(true)
                return
            }
            PsiTargetNavigator { validTargets }
                .navigate(event, ApiHelperBundle.message(titleKey), project)
        }
    }
}
