package com.lizhuolun.apihelper.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.scanner.EndpointScanner
import com.lizhuolun.apihelper.settings.ApiHelperConfigurable
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import com.lizhuolun.apihelper.ui.debug.DebugHistoryEntry
import com.lizhuolun.apihelper.ui.debug.EndpointDebugPanel
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUastParentOfType
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * ApiHelper 工具窗口“接口”主面板，采用上下可折叠分栏布局：
 *
 * - 顶部：紧凑工具栏（刷新、搜索、展开、收起、定位当前接口、按模块分组、配对筛选、调试分栏开关）
 * - 上半区：可折叠的“接口”区（内含 Controller / Feign 子 Tab、搜索框与端点树）
 * - 下半区：可折叠的“调试”区（默认折叠以让接口树拥有最大可视空间，触发调试或点击标题栏时自动展开）
 *
 * 当上下两区同时展开时，通过 [OnePixelSplitter] 支持自由拖拽调节高度比例；
 * 当折叠任一区域时，被折叠的标题栏自动贴顶或贴底停靠，另一区域自动占满剩余空间。
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointToolWindowPanel(private val project: Project) : JBPanel<EndpointToolWindowPanel>(BorderLayout()) {

    private val log = thisLogger()

    private val controllerTree = EndpointTree(project, EndpointKind.CONTROLLER)
    private val feignTree = EndpointTree(project, EndpointKind.FEIGN)
    private val debugPanel = EndpointDebugPanel(project)

    /**
     * 调试请求历史变更回调，由外层 [EndpointToolWindowFactory] 绑定到“请求历史”Tab。
     */
    var onHistoryUpdated: (() -> Unit)?
        get() = debugPanel.onHistoryUpdated
        set(value) {
            debugPanel.onHistoryUpdated = value
        }

    private val interfaceCardLayout = CardLayout()
    private val interfaceContentPanel = JPanel(interfaceCardLayout).apply {
        isOpaque = false
    }

    private val searchField = SearchTextField(false).apply {
        textEditor.emptyText.text = ApiHelperBundle.message("toolwindow.filter.placeholder")
        ApiHelperUiStyle.applyTextField(textEditor)
        textEditor.registerKeyboardAction(
            {
                if (isSearchVisible) {
                    toggleSearch()
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_FOCUSED,
        )
    }

    private val searchBoxPanel = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
            BorderFactory.createEmptyBorder(6, 10, 6, 10),
        )
        add(searchField, BorderLayout.CENTER)
    }

    private val searchBadgeLabel = JLabel("").apply {
        foreground = UIUtil.getContextHelpForeground()
        verticalAlignment = SwingConstants.CENTER
    }

    private val ideTabButtons = mutableListOf<JLabel>()
    private val interfaceChevronLabel = JLabel(AllIcons.General.ChevronDown)
    private val debugChevronLabel = JLabel(AllIcons.General.ChevronRight)
    private val debugTargetSummaryLabel = JLabel("").apply {
        foreground = UIUtil.getContextHelpForeground()
        font = font?.deriveFont(Font.PLAIN, 11f)
        border = BorderFactory.createEmptyBorder(0, 0, 0, JBUI.scale(4))
    }

    private val toolbarComponent = buildToolbar()
    private val interfaceSectionHeader = buildInterfaceSectionHeader()
    private val debugSectionHeader = buildDebugSectionHeader()

    private val interfaceBodyPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(searchBoxPanel, BorderLayout.NORTH)
        add(interfaceContentPanel, BorderLayout.CENTER)
    }

    private val splitContainer = JPanel(BorderLayout()).apply {
        isOpaque = false
    }

    private val verticalSplitter = OnePixelSplitter(true, "ApiHelper.EndpointDebugSplitter", 0.50f)

    private var isSearchVisible: Boolean = false
    private var isGroupByModule: Boolean = false
    private var currentPairingFilter: EndpointPairingFilter = EndpointPairingFilter.ALL
    private var isInterfaceExpanded: Boolean = true
    private var isDebugExpanded: Boolean = false
    private var activeInterfaceTab: String = CARD_CONTROLLER
    private var controllerEndpointsLoaded: Boolean = false
    private var clientEndpointsLoaded: Boolean = false

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        interfaceContentPanel.add(controllerTree, CARD_CONTROLLER)
        interfaceContentPanel.add(feignTree, CARD_FEIGN)

        add(toolbarComponent, BorderLayout.NORTH)
        add(splitContainer, BorderLayout.CENTER)

        controllerTree.onCountsChanged = { total, filtered ->
            updateIdeTabCounts(total, filtered)
        }
        feignTree.onCountsChanged = { total, filtered ->
            updateIdeTabCounts(total, filtered)
        }
        controllerTree.onDebugRequested = { showDebugPanel(it) }

        searchBoxPanel.isVisible = false
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onSearchTextChanged()
            override fun removeUpdate(e: DocumentEvent?) = onSearchTextChanged()
            override fun changedUpdate(e: DocumentEvent?) = onSearchTextChanged()
        })

        selectInterfaceTab(CARD_CONTROLLER)
        rebuildSplitLayout()
    }

    private fun onSearchTextChanged() {
        val text = searchField.text.trim()
        controllerTree.applyFilter(text)
        feignTree.applyFilter(text)
        updateSearchBadge()
    }

    /**
     * 从缓存刷新两侧列表；缓存为空时执行一次后台全量扫描。
     */
    fun refresh(forceScan: Boolean = false) {
        if (project.isDisposed) return
        val cache = BilateralMappingCacheService.of(project)
        var controllers: List<HttpMappingInfo>
        var clients: List<HttpMappingInfo>

        if (forceScan) {
            val scanned = scanInBackground()
            if (scanned.failed) {
                controllers = cache.getAllControllerMappings()
                clients = cache.getAllClientMappings()
            } else {
                controllers = scanned.controllers
                clients = scanned.clients
                controllerEndpointsLoaded = true
                clientEndpointsLoaded = true
                cache.replaceController(controllers)
                cache.replaceClient(clients)
            }
        } else {
            controllers = cache.getAllControllerMappings()
            clients = cache.getAllClientMappings()
            if (controllers.isNotEmpty()) controllerEndpointsLoaded = true
            if (clients.isNotEmpty()) clientEndpointsLoaded = true
            if (!controllerEndpointsLoaded || !clientEndpointsLoaded) {
                val scanned = scanInBackground()
                if (!scanned.failed) {
                    if (!controllerEndpointsLoaded) {
                        controllers = scanned.controllers
                        cache.replaceController(scanned.controllers)
                        controllerEndpointsLoaded = true
                    }
                    if (!clientEndpointsLoaded) {
                        clients = scanned.clients
                        cache.replaceClient(scanned.clients)
                        clientEndpointsLoaded = true
                    }
                }
            }
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            controllerTree.refresh(controllers)
            feignTree.refresh(clients)
        }
    }

    /**
     * 创建工具窗口右上角标题栏动作。
     *
     * @return 标题栏动作列表
     */
    fun createTitleActions(): List<AnAction> {
        return listOf(
            TopBarAction(null, ApiHelperBundle.message("action.ApiHelper.GotoEndpoint.text"), AllIcons.Actions.Find) {
                com.lizhuolun.apihelper.ui.search.GotoEndpointAction.showPopup(project)
            },
            TopBarAction(null, ApiHelperBundle.message("toolwindow.action.settings"), AllIcons.General.Settings) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, ApiHelperConfigurable::class.java)
            },
        )
    }

    private fun buildToolbar(): JComponent {
        val filterGroup = DefaultActionGroup(
            ApiHelperBundle.message("toolwindow.action.filter.pairing"),
            true,
        ).apply {
            templatePresentation.icon = AllIcons.General.Filter
            add(PairingFilterToggleAction(ApiHelperBundle.message("toolwindow.filter.pairing.all"), EndpointPairingFilter.ALL))
            add(PairingFilterToggleAction(ApiHelperBundle.message("toolwindow.filter.pairing.paired"), EndpointPairingFilter.PAIRED_ONLY))
            add(PairingFilterToggleAction(ApiHelperBundle.message("toolwindow.filter.pairing.unpaired"), EndpointPairingFilter.UNPAIRED_ONLY))
        }

        val actionGroup = DefaultActionGroup().apply {
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.refresh"), AllIcons.Actions.Refresh) { refresh(forceScan = true) })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.search"), AllIcons.Actions.Search) { toggleSearch() })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.expand.all"), AllIcons.Actions.Expandall) {
                if (!isInterfaceExpanded) toggleInterfaceSection()
                activeTree().expandAll()
            })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.collapse.all"), AllIcons.Actions.Collapseall) {
                if (!isInterfaceExpanded) toggleInterfaceSection()
                activeTree().collapseAll()
            })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.locate.current"), AllIcons.General.Locate) {
                locateCurrentEditorEndpoint()
            })
            add(SplitDebugToggleAction())
            addSeparator()
            add(GroupByModuleToggleAction())
            add(filterGroup)
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, actionGroup, true)
        toolbar.setReservePlaceAutoPopupIcon(false)
        toolbar.targetComponent = this

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(1, 6, 1, 6),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(toolbar.component, BorderLayout.WEST)
        }
    }

    /**
     * 构建“接口”可折叠分组栏，左侧带折叠箭头与标题，中间内嵌 Controller / Feign 切换 Tab。
     */
    private fun buildInterfaceSectionHeader(): JComponent {
        ideTabButtons.clear()
        val toggleBox = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(6), JBUI.scale(6), JBUI.scale(8))
            add(interfaceChevronLabel)
            add(JLabel(ApiHelperBundle.message("toolwindow.nav.interfaces")).apply {
                font = font?.deriveFont(Font.BOLD, 12f)
                foreground = UIUtil.getLabelForeground()
            })
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    toggleInterfaceSection()
                }
            })
        }

        val tabsBox = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(ideTabButton(ApiHelperBundle.message("toolwindow.tab.controller"), CARD_CONTROLLER))
            add(ideTabButton(ApiHelperBundle.message("toolwindow.tab.feign"), CARD_FEIGN))
        }

        val leftContainer = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(toggleBox)
            add(tabsBox)
        }

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(searchBadgeLabel)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 8),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(leftContainer, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    /**
     * 构建“调试”可折叠分组栏，右侧展示当前已载入的接口摘要。
     */
    private fun buildDebugSectionHeader(): JComponent {
        val toggleBox = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(6), JBUI.scale(6), JBUI.scale(8))
            add(debugChevronLabel)
            add(JLabel(ApiHelperBundle.message("toolwindow.nav.debug")).apply {
                font = font?.deriveFont(Font.BOLD, 12f)
                foreground = UIUtil.getLabelForeground()
            })
        }

        val rightBox = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(debugTargetSummaryLabel)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 8),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(toggleBox, BorderLayout.WEST)
            add(rightBox, BorderLayout.EAST)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    toggleDebugSection()
                }
            })
        }
    }

    private fun toggleInterfaceSection() {
        isInterfaceExpanded = !isInterfaceExpanded
        if (!isInterfaceExpanded && !isDebugExpanded) {
            isDebugExpanded = true
        }
        rebuildSplitLayout()
    }

    private fun toggleDebugSection() {
        isDebugExpanded = !isDebugExpanded
        if (!isDebugExpanded && !isInterfaceExpanded) {
            isInterfaceExpanded = true
        }
        rebuildSplitLayout()
    }

    /**
     * 根据“接口”与“调试”两个折叠区的展开状态重新编排上下分栏容器。
     */
    private fun rebuildSplitLayout() {
        interfaceChevronLabel.icon = if (isInterfaceExpanded) AllIcons.General.ChevronDown else AllIcons.General.ChevronRight
        debugChevronLabel.icon = if (isDebugExpanded) AllIcons.General.ChevronDown else AllIcons.General.ChevronRight

        splitContainer.removeAll()
        verticalSplitter.firstComponent = null
        verticalSplitter.secondComponent = null

        when {
            isInterfaceExpanded && isDebugExpanded -> {
                val topPane = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(interfaceSectionHeader, BorderLayout.NORTH)
                    add(interfaceBodyPanel, BorderLayout.CENTER)
                }
                val bottomPane = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(debugSectionHeader, BorderLayout.NORTH)
                    add(debugPanel, BorderLayout.CENTER)
                }
                verticalSplitter.firstComponent = topPane
                verticalSplitter.secondComponent = bottomPane
                splitContainer.add(verticalSplitter, BorderLayout.CENTER)
            }
            isInterfaceExpanded && !isDebugExpanded -> {
                val topPane = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(interfaceSectionHeader, BorderLayout.NORTH)
                    add(interfaceBodyPanel, BorderLayout.CENTER)
                }
                splitContainer.add(topPane, BorderLayout.CENTER)
                splitContainer.add(debugSectionHeader, BorderLayout.SOUTH)
            }
            else -> {
                val bottomPane = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(debugSectionHeader, BorderLayout.NORTH)
                    add(debugPanel, BorderLayout.CENTER)
                }
                splitContainer.add(interfaceSectionHeader, BorderLayout.NORTH)
                splitContainer.add(bottomPane, BorderLayout.CENTER)
            }
        }
        splitContainer.revalidate()
        splitContainer.repaint()
    }

    private fun ideTabButton(label: String, card: String): JComponent {
        val button = JLabel(label).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = UIUtil.getContextHelpForeground()
            font = font?.deriveFont(Font.PLAIN, 12f)
            border = BorderFactory.createEmptyBorder(JBUI.scale(7), JBUI.scale(10), JBUI.scale(7), JBUI.scale(10))
            putClientProperty(KEY_CARD, card)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (!isInterfaceExpanded) {
                        isInterfaceExpanded = true
                        rebuildSplitLayout()
                    }
                    selectInterfaceTab(card)
                }
            })
        }
        ideTabButtons.add(button)
        return button
    }

    private fun selectInterfaceTab(card: String) {
        activeInterfaceTab = card
        interfaceCardLayout.show(interfaceContentPanel, card)
        ideTabButtons.forEach { btn ->
            val btnCard = btn.getClientProperty(KEY_CARD) as? String
            updateIdeTabStyle(btn, btnCard == card)
        }
        activeTree().expandAll()
        updateSearchBadge()
    }

    private fun updateIdeTabStyle(label: JLabel, active: Boolean) {
        label.foreground = if (active) UIUtil.getLabelForeground() else UIUtil.getContextHelpForeground()
        label.font = label.font.deriveFont(if (active) Font.BOLD else Font.PLAIN, 12f)
        label.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, if (active) UIUtil.getLabelForeground() else Color(0, 0, 0, 0)),
            BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(10), JBUI.scale(5), JBUI.scale(10)),
        )
        label.isOpaque = false
    }

    private fun updateIdeTabCounts(total: Int, filtered: Int) {
        ideTabButtons.forEach { btn ->
            when (btn.getClientProperty(KEY_CARD)) {
                CARD_CONTROLLER -> btn.text = controllerTabTitle(
                    controllerTree.getTotalCount(),
                    controllerTree.getFilteredCount(),
                )
                CARD_FEIGN -> btn.text = feignTabTitle(
                    feignTree.getTotalCount(),
                    feignTree.getFilteredCount(),
                )
            }
        }
    }

    private fun controllerTabTitle(total: Int, filtered: Int): String =
        formatTabTitle(ApiHelperBundle.message("toolwindow.tab.controller"), total, filtered)

    private fun feignTabTitle(total: Int, filtered: Int): String =
        formatTabTitle(ApiHelperBundle.message("toolwindow.tab.feign"), total, filtered)

    private fun formatTabTitle(baseTitle: String, total: Int, filtered: Int): String {
        val hasFilter = searchField.text.isNotBlank() || currentPairingFilter != EndpointPairingFilter.ALL
        return if (hasFilter && filtered != total) {
            "$baseTitle [$filtered/$total]"
        } else {
            "$baseTitle [$total]"
        }
    }

    /**
     * 展开调试区并加载指定接口。
     *
     * @param item 端点展示项
     */
    fun showDebugPanel(item: EndpointTreeItem) {
        if (!isDebugExpanded) {
            isDebugExpanded = true
            rebuildSplitLayout()
        }
        val methodText = if (item.httpMethod.name == "ANY") "GET" else item.httpMethod.name
        debugTargetSummaryLabel.text = "$methodText ${item.url}"
        debugPanel.loadEndpoint(item)
    }

    /**
     * 展开调试区并回放指定的历史请求记录。
     *
     * @param entry 历史记录条目
     */
    fun replayHistoryEntry(entry: DebugHistoryEntry) {
        if (!isDebugExpanded) {
            isDebugExpanded = true
            rebuildSplitLayout()
        }
        debugTargetSummaryLabel.text = "${entry.draft.method} ${entry.draft.url}"
        debugPanel.restoreHistoryEntry(entry)
    }

    /**
     * 重新加载调试面板的环境列表配置。
     */
    fun reloadDebugEnvironments() {
        debugPanel.reloadEnvironments()
    }

    /**
     * 在端点树中定位并选中当前编辑器光标所在的接口方法。
     */
    fun locateCurrentEditorEndpoint() {
        val method = resolveCurrentEditorMethod()
        if (method == null) {
            notifyBalloon(ApiHelperBundle.message("toolwindow.notification.locate.not.found"))
            return
        }
        if (!isInterfaceExpanded) {
            isInterfaceExpanded = true
            rebuildSplitLayout()
        }
        if (trySelectMethodInTrees(method)) {
            return
        }
        if (searchField.text.isNotBlank() || currentPairingFilter != EndpointPairingFilter.ALL) {
            searchField.text = ""
            applyPairingFilter(EndpointPairingFilter.ALL)
            if (trySelectMethodInTrees(method)) {
                return
            }
        }
        notifyBalloon(ApiHelperBundle.message("toolwindow.notification.locate.not.found"))
    }

    private fun trySelectMethodInTrees(method: PsiMethod): Boolean {
        if (controllerTree.containsMethod(method)) {
            selectInterfaceTab(CARD_CONTROLLER)
            if (controllerTree.selectMethod(method)) return true
        }
        if (feignTree.containsMethod(method)) {
            selectInterfaceTab(CARD_FEIGN)
            if (feignTree.selectMethod(method)) return true
        }
        return false
    }

    private fun resolveCurrentEditorMethod(): PsiMethod? {
        return ApplicationManager.getApplication().runReadAction(Computable {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@Computable null
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@Computable null
            val offset = editor.caretModel.offset
            val element = psiFile.findElementAt(offset)
                ?: psiFile.findElementAt((offset - 1).coerceAtLeast(0))
                ?: return@Computable null
            element.getUastParentOfType<UMethod>(false)?.javaPsi
        })
    }

    private fun notifyBalloon(content: String) {
        if (project.isDisposed) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ApiHelper")
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * 当前是否展示接口列表。
     */
    fun isInterfaceListVisible(): Boolean = isInterfaceExpanded

    /**
     * 当前是否展示接口调试。
     */
    fun isDebugVisible(): Boolean = isDebugExpanded

    private fun toggleSearch() {
        if (!isInterfaceExpanded) {
            isInterfaceExpanded = true
            rebuildSplitLayout()
        }
        isSearchVisible = !isSearchVisible
        searchBoxPanel.isVisible = isSearchVisible
        if (isSearchVisible) {
            searchField.textEditor.requestFocusInWindow()
        } else {
            searchField.text = ""
        }
        revalidate()
        repaint()
    }

    private fun activeTree(): EndpointTree = if (activeInterfaceTab == CARD_CONTROLLER) controllerTree else feignTree

    private fun updateSearchBadge() {
        val text = searchField.text.trim()
        val filtered = activeTree().getFilteredCount()
        val total = activeTree().getTotalCount()
        searchBadgeLabel.text = if (text.isEmpty()) {
            ""
        } else {
            "$text ($filtered/$total)"
        }
        searchBadgeLabel.isVisible = text.isNotEmpty()
    }

    private fun scanInBackground(): EndpointScanResult {
        return try {
            val (controllers, clients) = ProgressManager.getInstance()
                .runProcessWithProgressSynchronously<Pair<List<HttpMappingInfo>, List<HttpMappingInfo>>, RuntimeException>(
                    {
                        val manualProfile = ApiHelperSettings.getInstance().state.manualActiveProfile
                        ApplicationManager.getApplication().runReadAction(Computable {
                            val controllers = EndpointScanner.scanControllerEndpoints(project, manualProfile)
                            val clients = EndpointScanner.scanClientEndpoints(project)
                            controllers to clients
                        })
                    },
                    ApiHelperBundle.message("progress.finding.targets"),
                    true,
                    project,
                )
            EndpointScanResult(controllers, clients, failed = false)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.warn("ApiHelper: 工具窗口扫描端点失败, project=${project.name}", e)
            EndpointScanResult(emptyList(), emptyList(), failed = true)
        }
    }

    private inner class ToolbarAction(
        text: String,
        icon: Icon,
        private val action: () -> Unit,
    ) : AnAction(text, null, icon) {
        override fun actionPerformed(e: AnActionEvent) {
            action()
        }
    }

    private inner class SplitDebugToggleAction : ToggleAction(
        ApiHelperBundle.message("toolwindow.action.toggle.debug.split"),
        null,
        AllIcons.Actions.PreviewDetailsVertically,
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = isDebugExpanded

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            isDebugExpanded = state
            if (!isDebugExpanded && !isInterfaceExpanded) {
                isInterfaceExpanded = true
            }
            rebuildSplitLayout()
        }
    }

    private inner class GroupByModuleToggleAction : ToggleAction(
        ApiHelperBundle.message("toolwindow.action.group.module"),
        null,
        AllIcons.Actions.GroupByModule,
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = isGroupByModule

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            isGroupByModule = state
            controllerTree.setGroupByModule(state)
            feignTree.setGroupByModule(state)
        }
    }

    private inner class PairingFilterToggleAction(
        text: String,
        private val filter: EndpointPairingFilter,
    ) : ToggleAction(text) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = currentPairingFilter == filter

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) {
                applyPairingFilter(filter)
            }
        }
    }

    private fun applyPairingFilter(filter: EndpointPairingFilter) {
        currentPairingFilter = filter
        controllerTree.setPairingFilter(filter)
        feignTree.setPairingFilter(filter)
        updateSearchBadge()
    }

    private inner class TopBarAction(
        text: String?,
        description: String?,
        icon: Icon?,
        private val action: () -> Unit,
    ) : AnAction(text, description, icon) {
        override fun actionPerformed(e: AnActionEvent) {
            action()
        }
    }

    companion object {
        private const val CARD_CONTROLLER = "controller"
        private const val CARD_FEIGN = "feign"
        private const val KEY_CARD = "card"
    }

    private data class EndpointScanResult(
        val controllers: List<HttpMappingInfo>,
        val clients: List<HttpMappingInfo>,
        val failed: Boolean,
    )
}
