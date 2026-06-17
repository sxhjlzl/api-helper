package com.lizhuolun.apihelper.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiMethod
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.ui.component.RoundedCornerBorder
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

/**
 * 端点列表树组件，仅负责树形展示与交互。
 *
 * 搜索、工具栏等外层容器由 [EndpointToolWindowPanel] 统一组织，
 * 避免每个树实例重复创建搜索框，并保证顶部布局与参考设计一致。
 *
 * @param project 当前工程
 * @param kind 本树展示的端点类别，用于计算对端跳转
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointTree(
    private val project: Project,
    private val kind: EndpointKind,
) : JPanel(BorderLayout()) {

    private val treeModel = EndpointTreeModel()
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        emptyText.text = ApiHelperBundle.message("toolwindow.empty.text")
        cellRenderer = EndpointTreeCellRenderer()
        rowHeight = 0
    }

    /**
     * 当前完整端点数据，用于过滤时重建树。
     */
    private var allItems: List<EndpointTreeItem> = emptyList()

    /**
     * 当前过滤后的条目数量。
     */
    private var filteredCount: Int = 0

    /**
     * 数量变化回调，参数为 (总数, 过滤后数量)。
     */
    var onCountsChanged: ((total: Int, filtered: Int) -> Unit)? = null

    /**
     * 调试接口回调，由工具窗口主面板切换到调试 Tab 并预填请求。
     */
    var onDebugRequested: ((item: EndpointTreeItem) -> Unit)? = null

    init {
        isOpaque = false
        add(JBScrollPane(tree).apply {
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)
        setupListeners()
    }

    /**
     * 刷新整棵树。
     *
     * 调用方应确保在 read action 内传入 endpoints，或保证其中的 PSI 属性已可安全访问。
     * 本方法会在 read action 内一次性解析智能指针并提取展示所需字段，
     * 后续过滤与渲染不再触碰 PSI。
     *
     * @param endpoints 新的端点列表
     */
    fun refresh(endpoints: List<HttpMappingInfo>) {
        val items = ApplicationManager.getApplication().runReadAction(Computable {
            endpoints.mapNotNull { info ->
                val method = info.resolveMethod() ?: return@mapNotNull null
                EndpointTreeItem.from(info, method)
            }
        })
        allItems = items
        applyFilter("")
    }

    /**
     * 使用指定文本过滤树。
     *
     * @param text 过滤文本
     */
    fun applyFilter(text: String) {
        val trimmed = text.trim()
        val filtered = if (trimmed.isBlank()) {
            allItems
        } else {
            val lower = trimmed.lowercase()
            allItems.filter {
                it.url.lowercase().contains(lower) ||
                    it.httpMethod.name.lowercase().contains(lower) ||
                    it.methodName.lowercase().contains(lower) ||
                    it.className.lowercase().contains(lower)
            }
        }
        filteredCount = filtered.size
        onCountsChanged?.invoke(allItems.size, filteredCount)
        treeModel.refresh(filtered)
        expandAll()
    }

    private fun setupListeners() {
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateSelected()
                    e.consume()
                }
            }
        })

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                navigateSelected()
                return true
            }
        }.installOn(tree)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                showEndpointPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                showEndpointPopup(e)
            }
        })
    }

    /**
     * 展开树中所有类分组节点。
     */
    fun expandAll() {
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    /**
     * 收起树中所有类分组节点。
     */
    fun collapseAll() {
        for (i in tree.rowCount - 1 downTo 0) {
            tree.collapseRow(i)
        }
    }

    /**
     * 获取当前 Tab 下完整的端点总数。
     *
     * @return 端点总数
     */
    fun getTotalCount(): Int = allItems.size

    /**
     * 获取当前过滤后显示的端点数量。
     *
     * @return 过滤后端点数量
     */
    fun getFilteredCount(): Int = filteredCount

    private fun navigateSelected() {
        val pointer = (tree.lastSelectedPathComponent as? EndpointNode)?.pointer ?: return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val method = ApplicationManager.getApplication().runReadAction(Computable {
                pointer.element?.takeIf { it.isValid }
            }) ?: return@invokeLater
            navigateToMethod(method)
        }
    }

    private fun showEndpointPopup(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val node = path.lastPathComponent as? EndpointNode ?: return
        if (node.item == null) return
        tree.selectionPath = path
        buildPopupMenu().show(tree, e.x, e.y)
    }

    private fun buildPopupMenu(): JPopupMenu {
        val menu = JPopupMenu().apply {
            isOpaque = false
            background = UIUtil.getPanelBackground()
            border = RoundedCornerBorder(fillColor = UIUtil.getPanelBackground(), arc = 8)
        }
        val debugItem = menuItem(
            ApiHelperBundle.message("toolwindow.action.debug.endpoint"),
            AllIcons.Actions.Execute,
        ) { debugSelectedEndpoint() }
        val navigateItem = menuItem(
            ApiHelperBundle.message("toolwindow.action.navigate.counterpart"),
            AllIcons.Actions.Forward,
        ) { navigateToCounterpart() }
        val copyItem = menuItem(
            ApiHelperBundle.message("toolwindow.action.copy.url"),
            AllIcons.Actions.Copy,
        ) { copySelectedUrl() }
        menu.add(debugItem)
        menu.addSeparator()
        menu.add(navigateItem)
        menu.add(copyItem)
        return menu
    }

    private fun menuItem(text: String, icon: Icon, action: () -> Unit): JMenuItem {
        return JMenuItem(text, icon).apply {
            border = BorderFactory.createEmptyBorder(
                JBUI.scale(5),
                JBUI.scale(10),
                JBUI.scale(5),
                JBUI.scale(14),
            )
            iconTextGap = JBUI.scale(8)
            addActionListener { action() }
        }
    }

    private fun debugSelectedEndpoint() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val item = node.item ?: return
        onDebugRequested?.invoke(item)
    }

    private fun copySelectedUrl() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val url = node.item?.url ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(url))
    }

    private fun navigateToCounterpart() {
        val node = tree.lastSelectedPathComponent as? EndpointNode ?: return
        val pointer = node.pointer ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val method = ApplicationManager.getApplication().runReadAction(Computable {
                pointer.element?.takeIf { it.isValid }
            }) ?: return@executeOnPooledThread

            val targets = when (kind) {
                EndpointKind.CONTROLLER -> BilateralMappingCacheService.of(project).findClientTargets(method)
                else -> BilateralMappingCacheService.of(project).findControllerTargets(method)
            }

            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val valid = ApplicationManager.getApplication().runReadAction(Computable {
                    targets.mapNotNull { it.resolveMethod() }
                        .filterIsInstance<NavigatablePsiElement>()
                })
                when (valid.size) {
                    0 -> {}
                    1 -> valid[0].navigate(true)
                    else -> JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(valid)
                        .setTitle(ApiHelperBundle.message("toolwindow.action.navigate.counterpart"))
                        .setItemSelectedCallback { it?.navigate(true) }
                        .createPopup()
                        .showInFocusCenter()
                }
            }
        }
    }

    private fun navigateToMethod(method: PsiMethod) {
        ApplicationManager.getApplication().runReadAction(Computable {
            val file = method.containingFile?.virtualFile ?: return@Computable null
            val offset = method.textOffset
            OpenFileDescriptor(project, file, offset)
        })?.let {
            it.navigate(true)
        }
    }
}
