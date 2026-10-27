package com.lizhuolun.apihelper.ui.callers

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.HttpMappingInfo
import javax.swing.JList

/**
 * 反向调用方查询：列出所有调用该 Controller 方法的 Feign / HttpExchange 客户端。
 *
 * 这是 IDE 原生 `Find Usages` 覆盖不到的能力——HTTP 端点之间的调用关系不在引用索引里，
 * 但 ApiHelper 的双边倒排索引天生就能 O(1) 反查。改动 Controller 前先看一眼「谁会挂」。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
class ShowEndpointCallersAction : AnAction(
    ApiHelperBundle.message("callers.action.show"),
    ApiHelperBundle.message("callers.action.show.description"),
    AllIcons.Actions.Find,
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = resolveMethod(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val method = resolveMethod(e) ?: return
        showCallers(project, method)
    }

    /**
     * 从事件中定位当前光标所在的方法。
     */
    private fun resolveMethod(e: AnActionEvent): PsiMethod? {
        (e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiMethod)?.let { return it }
        val editor: Editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val file: PsiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val project = e.project ?: return null
        return ApplicationManager.getApplication().runReadAction(Computable {
            val document = editor.document
            PsiDocumentManager.getInstance(project).getPsiFile(document)?.let { psiFile ->
                val element: PsiElement = psiFile.findElementAt(editor.caretModel.offset) ?: return@Computable null
                PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            } ?: PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), PsiMethod::class.java)
        })
    }

    companion object {

        /**
         * 展示调用方列表，供 Action 与接口树右键菜单共用。
         *
         * @param project 当前工程
         * @param method 目标 Controller 方法
         */
        fun showCallers(project: Project, method: PsiMethod) {
            var targets: List<HttpMappingInfo> = emptyList()
            // 缓存未命中时 findClientTargets 会兜底全量扫描，必须放进可取消的进度里
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                {
                    ProgressManager.getInstance().progressIndicator?.isIndeterminate = true
                    targets = BilateralMappingCacheService.of(project).findClientTargets(method)
                },
                ApiHelperBundle.message("callers.progress"),
                true,
                project,
            )
            if (targets.isEmpty()) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("ApiHelper")
                    .createNotification(
                        ApiHelperBundle.message("callers.popup.empty"),
                        NotificationType.INFORMATION,
                    )
                    .notify(project)
                return
            }

            val items = buildItems(project, targets)
            val popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                .createPopupChooserBuilder(items)
                .setTitle(ApiHelperBundle.message("callers.popup.title", displayName(method)))
                .setRenderer(CallerRenderer())
                .setItemChosenCallback { item -> navigate(project, item.mapping) }
                .createPopup()
            popup.showInFocusCenter()
        }

        /**
         * 把映射转换成展示项，所有 PSI 读取都在 read action 内完成。
         */
        private fun buildItems(
            project: Project,
            targets: List<HttpMappingInfo>,
        ): MutableList<CallerItem> =
            ApplicationManager.getApplication().runReadAction(Computable {
                targets.mapNotNull { mapping ->
                    val psiMethod = mapping.resolveMethod() ?: return@mapNotNull null
                    CallerItem(
                        mapping = mapping,
                        display = "${psiMethod.containingClass?.name.orEmpty()}#${psiMethod.name}",
                        moduleName = ModuleUtilCore.findModuleForPsiElement(psiMethod)?.name.orEmpty(),
                        url = mapping.url,
                        httpMethod = mapping.httpMethod.name,
                    )
                }.toMutableList()
            })

        /**
         * 跳转到调用点。
         */
        private fun navigate(project: Project, mapping: HttpMappingInfo) {
            ApplicationManager.getApplication().runReadAction(Computable {
                val method = mapping.resolveMethod() ?: return@Computable null
                val file = method.containingFile?.virtualFile ?: return@Computable null
                OpenFileDescriptor(project, file, method.textOffset)
            })?.navigate(true)
        }

        /**
         * 方法展示名，形如 `ClassName#methodName`。
         */
        private fun displayName(method: PsiMethod): String =
            ApplicationManager.getApplication().runReadAction(Computable {
                "${method.containingClass?.name.orEmpty()}#${method.name}"
            })
    }
}

/**
 * 调用方列表项。
 *
 * @property mapping 调用方映射
 * @property display `类名#方法名`
 * @property moduleName 所属模块
 * @property url 请求路径
 * @property httpMethod HTTP 方法
 * @author lizhuolun
 * @date 2026/10/27
 */
data class CallerItem(
    val mapping: HttpMappingInfo,
    val display: String,
    val moduleName: String,
    val url: String,
    val httpMethod: String,
)

/**
 * 调用方渲染器：主行展示方法，次行展示模块与路径。
 */
private class CallerRenderer : ColoredListCellRenderer<CallerItem>() {

    override fun customizeCellRenderer(
        list: JList<out CallerItem>,
        value: CallerItem?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        if (value == null) return
        icon = AllIcons.Nodes.Method
        append(value.httpMethod, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
        append(" ")
        append(value.display, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("  ${value.moduleName}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        append("  ${value.url}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}