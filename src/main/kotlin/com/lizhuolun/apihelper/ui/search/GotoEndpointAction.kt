package com.lizhuolun.apihelper.ui.search

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.scanner.EndpointScanner
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.EndpointSearchQuery
import com.lizhuolun.apihelper.ui.EndpointToolWindowService
import com.lizhuolun.apihelper.ui.EndpointTreeItem
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import com.lizhuolun.apihelper.ui.component.HttpMethodBadge
import com.lizhuolun.apihelper.ui.component.RoundedBadgeLabel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 全局快捷搜索接口动作（Navigate -> Search API Endpoint...）。
 *
 * 支持在任意编辑器位置通过快捷键或菜单唤起轻量端点搜索弹窗：
 * - Enter：跳转到选中接口源码
 * - Shift + Enter：直接跳转到对端（Controller ↔ Feign）方法
 * - Ctrl/Cmd + Enter：在 ApiHelper 调试面板中打开该接口
 *
 * @author lizhuolun
 * @date 2026/10/16
 */
class GotoEndpointAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && !project.isDisposed
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (project.isDisposed) return
        showPopup(project)
    }

    companion object {

        /**
         * 展示全局接口快速检索弹窗。
         *
         * @param project 当前工程
         */
        fun showPopup(project: Project) {
            if (project.isDisposed || DumbService.isDumb(project)) return
            val allItems = loadAllEndpointItems(project)
            val listModel = DefaultListModel<EndpointTreeItem>()
            allItems.forEach(listModel::addElement)

            val resultList = JBList(listModel).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                cellRenderer = EndpointListCellRenderer()
                emptyText.text = ApiHelperBundle.message("toolwindow.empty.text")
                if (model.size > 0) {
                    selectedIndex = 0
                }
            }

            val searchField = SearchTextField(false).apply {
                textEditor.emptyText.text = ApiHelperBundle.message("toolwindow.filter.placeholder")
                ApiHelperUiStyle.applyTextField(textEditor)
            }

            val countLabel = JBLabel("${allItems.size}").apply {
                foreground = UIUtil.getContextHelpForeground()
                font = font?.deriveFont(Font.PLAIN, 11f)
                border = BorderFactory.createEmptyBorder(0, JBUI.scale(6), 0, JBUI.scale(4))
            }

            val topBar = JPanel(BorderLayout()).apply {
                isOpaque = true
                background = UIUtil.getPanelBackground()
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8),
                )
                add(searchField, BorderLayout.CENTER)
                add(countLabel, BorderLayout.EAST)
            }

            val hintLabel = JBLabel(ApiHelperBundle.message("goto.endpoint.popup.hint")).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = font?.deriveFont(Font.PLAIN, 11f)
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, ApiHelperUiStyle.borderColor()),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10),
                )
            }

            val contentPanel = JPanel(BorderLayout()).apply {
                preferredSize = Dimension(JBUI.scale(620), JBUI.scale(380))
                add(topBar, BorderLayout.NORTH)
                add(JBScrollPane(resultList).apply {
                    border = BorderFactory.createEmptyBorder()
                }, BorderLayout.CENTER)
                add(hintLabel, BorderLayout.SOUTH)
            }

            var popupRef: JBPopup? = null

            fun triggerSelection(modifiers: Int) {
                val selected = resultList.selectedValue ?: return
                popupRef?.cancel()
                val isShift = (modifiers and InputEvent.SHIFT_DOWN_MASK) != 0
                val isMetaOrCtrl = (modifiers and InputEvent.META_DOWN_MASK) != 0 ||
                    (modifiers and InputEvent.CTRL_DOWN_MASK) != 0
                when {
                    isMetaOrCtrl && selected.kind == EndpointKind.CONTROLLER -> {
                        val method = ApplicationManager.getApplication().runReadAction(Computable {
                            selected.pointer?.element?.takeIf { it.isValid }
                        }) ?: return
                        val mapping = BilateralMappingCacheService.of(project).resolveMapping(method) ?: return
                        EndpointToolWindowService.of(project).debugEndpoint(mapping)
                    }
                    isShift -> navigateToCounterpart(project, selected)
                    else -> navigateToSource(project, selected)
                }
            }

            searchField.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = refilter()
                override fun removeUpdate(e: DocumentEvent?) = refilter()
                override fun changedUpdate(e: DocumentEvent?) = refilter()

                private fun refilter() {
                    val raw = searchField.text.trim()
                    val query = EndpointSearchQuery.parse(raw)
                    val filtered = if (query.isBlank) {
                        allItems
                    } else {
                        allItems.filter(query::matches)
                    }
                    listModel.clear()
                    filtered.forEach(listModel::addElement)
                    countLabel.text = if (query.isBlank) "${allItems.size}" else "${filtered.size}/${allItems.size}"
                    if (filtered.isNotEmpty()) {
                        resultList.selectedIndex = 0
                    }
                }
            })

            searchField.textEditor.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_DOWN -> {
                            val size = resultList.model.size
                            if (size > 0) {
                                val next = (resultList.selectedIndex + 1).coerceAtMost(size - 1)
                                resultList.selectedIndex = next
                                resultList.ensureIndexIsVisible(next)
                            }
                            e.consume()
                        }
                        KeyEvent.VK_UP -> {
                            val size = resultList.model.size
                            if (size > 0) {
                                val prev = (resultList.selectedIndex - 1).coerceAtLeast(0)
                                resultList.selectedIndex = prev
                                resultList.ensureIndexIsVisible(prev)
                            }
                            e.consume()
                        }
                        KeyEvent.VK_ENTER -> {
                            triggerSelection(e.modifiersEx)
                            e.consume()
                        }
                    }
                }
            })

            resultList.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        triggerSelection(e.modifiersEx)
                        e.consume()
                    }
                }
            })

            object : DoubleClickListener() {
                override fun onDoubleClick(event: MouseEvent): Boolean {
                    triggerSelection(event.modifiersEx)
                    return true
                }
            }.installOn(resultList)

            val popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(contentPanel, searchField.textEditor)
                .setTitle(ApiHelperBundle.message("goto.endpoint.popup.title"))
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .createPopup()
            popupRef = popup
            popup.showCenteredInCurrentWindow(project)
        }

        private fun loadAllEndpointItems(project: Project): List<EndpointTreeItem> {
            val cache = BilateralMappingCacheService.of(project)
            var controllers: List<HttpMappingInfo> = cache.getAllControllerMappings()
            var clients: List<HttpMappingInfo> = cache.getAllClientMappings()
            if (controllers.isEmpty() && clients.isEmpty()) {
                val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
                controllers = EndpointScanner.scanControllerEndpoints(project, manualProfile)
                clients = EndpointScanner.scanClientEndpoints(project)
                if (controllers.isNotEmpty()) cache.replaceController(controllers)
                if (clients.isNotEmpty()) cache.replaceClient(clients)
            }
            return ApplicationManager.getApplication().runReadAction(Computable {
                (controllers + clients).mapNotNull { info ->
                    val method = info.resolveMethod() ?: return@mapNotNull null
                    EndpointTreeItem.from(info, method, hasCounterpart = cache.hasCounterpart(info))
                }
            })
        }

        private fun navigateToSource(project: Project, item: EndpointTreeItem) {
            val descriptor = ApplicationManager.getApplication().runReadAction(Computable {
                val method = item.pointer?.element?.takeIf { it.isValid } ?: return@Computable null
                val file = method.containingFile?.virtualFile ?: return@Computable null
                OpenFileDescriptor(project, file, method.textOffset)
            }) ?: return
            descriptor.navigate(true)
        }

        private fun navigateToCounterpart(project: Project, item: EndpointTreeItem) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val method = ApplicationManager.getApplication().runReadAction(Computable {
                    item.pointer?.element?.takeIf { it.isValid }
                }) ?: return@executeOnPooledThread
                val cache = BilateralMappingCacheService.of(project)
                val targets = if (item.kind == EndpointKind.CONTROLLER) {
                    cache.findClientTargets(method)
                } else {
                    cache.findControllerTargets(method)
                }
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    val valid = ApplicationManager.getApplication().runReadAction(Computable {
                        targets.mapNotNull { it.resolveMethod() }
                            .filterIsInstance<NavigatablePsiElement>()
                    })
                    if (valid.isNotEmpty()) {
                        valid.first().navigate(true)
                    } else {
                        navigateToSource(project, item)
                    }
                }
            }
        }
    }

    private class EndpointListCellRenderer : JPanel(BorderLayout(JBUI.scale(8), 0)), ListCellRenderer<EndpointTreeItem> {

        private val methodBadge = HttpMethodBadge(HttpMethod.GET)
        private val urlLabel = JLabel().apply {
            font = font?.deriveFont(Font.PLAIN, 12f)
        }
        private val kindBadge = RoundedBadgeLabel()
        private val locationLabel = JLabel().apply {
            font = font?.deriveFont(Font.PLAIN, 11f)
        }
        private val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(kindBadge)
            add(locationLabel)
        }

        init {
            border = BorderFactory.createEmptyBorder(JBUI.scale(5), JBUI.scale(10), JBUI.scale(5), JBUI.scale(10))
            add(methodBadge, BorderLayout.WEST)
            add(urlLabel, BorderLayout.CENTER)
            add(rightPanel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out EndpointTreeItem>,
            value: EndpointTreeItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            if (value == null) return this
            isOpaque = true
            background = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else UIUtil.getLabelForeground()
            val muted = if (isSelected) list.selectionForeground else UIUtil.getContextHelpForeground()

            methodBadge.method = value.httpMethod
            urlLabel.text = value.url
            urlLabel.foreground = fg

            kindBadge.text = when (value.kind) {
                EndpointKind.CONTROLLER -> "Controller"
                EndpointKind.FEIGN -> "Feign"
                EndpointKind.HTTP_EXCHANGE -> "HttpExchange"
            }
            kindBadge.foreground = muted

            val simpleClass = value.className.substringAfterLast('.')
            locationLabel.text = "$simpleClass#${value.methodName}"
            locationLabel.foreground = muted
            return this
        }
    }
}
