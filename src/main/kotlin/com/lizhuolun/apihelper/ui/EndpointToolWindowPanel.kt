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
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.ui.SearchTextField
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUastParentOfType
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * ApiHelper 工具窗口“接口”标签页主面板。
 *
 * 顶部包含紧凑工具栏与 Controller / Feign 子标签切换栏，主体展示全高端点树，
 * 右键点击“调试接口”时通过 [EndpointToolWindowService] 切换到平级的一级“调试”标签页。
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointToolWindowPanel(private val project: Project) : JBPanel<EndpointToolWindowPanel>(BorderLayout()) {

    private val log = thisLogger()

    private val controllerTree = EndpointTree(project, EndpointKind.CONTROLLER)
    private val feignTree = EndpointTree(project, EndpointKind.FEIGN)

    /**
     * 用户在接口树中请求调试某个接口时的回调，由 [EndpointToolWindowFactory] 路由到一级“调试”标签页。
     */
    var onDebugRequested: ((EndpointTreeItem) -> Unit)? = null

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
            BorderFactory.createEmptyBorder(5, 8, 5, 8),
        )
        add(searchField, BorderLayout.CENTER)
    }

    private val searchBadgeLabel = JLabel("").apply {
        foreground = UIUtil.getContextHelpForeground()
        font = ApiHelperUiStyle.smallFont()
        verticalAlignment = SwingConstants.CENTER
    }

    private val ideTabButtons = mutableListOf<JLabel>()
    private val moduleFilterGroup = DefaultActionGroup(ApiHelperBundle.message("toolwindow.filter.module"), true)
    private val frameworkFilterGroup = DefaultActionGroup(ApiHelperBundle.message("toolwindow.filter.framework"), true)
    private val filterButtons = mutableMapOf<DefaultActionGroup, JButton>()
    private val toolbarComponent = buildToolbar()
    private val filterBarComponent = buildFilterBar()
    private val ideTabBar = buildIdeTabBar()
    private val filterAndTabPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(filterBarComponent, BorderLayout.NORTH)
        add(ideTabBar, BorderLayout.SOUTH)
    }

    private val headerPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(toolbarComponent, BorderLayout.NORTH)
        add(filterAndTabPanel, BorderLayout.CENTER)
        add(searchBoxPanel, BorderLayout.SOUTH)
    }

    private var isSearchVisible: Boolean = false
    private var isGroupByModule: Boolean = false
    private var selectedModules: Set<String> = emptySet()
    private var selectedFrameworks: Set<EndpointKind> = emptySet()
    private var currentPairingFilter: EndpointPairingFilter = EndpointPairingFilter.ALL
    private var activeEndpointType: EndpointTypeFilter = EndpointTypeFilter.SERVER
    private var controllerEndpointsLoaded: Boolean = false
    private var clientEndpointsLoaded: Boolean = false

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        interfaceContentPanel.add(controllerTree, CARD_CONTROLLER)
        interfaceContentPanel.add(feignTree, CARD_FEIGN)

        add(headerPanel, BorderLayout.NORTH)
        add(interfaceContentPanel, BorderLayout.CENTER)

        controllerTree.onCountsChanged = { total, filtered ->
            updateIdeTabCounts(total, filtered)
        }
        feignTree.onCountsChanged = { total, filtered ->
            updateIdeTabCounts(total, filtered)
        }
        controllerTree.onDebugRequested = { item ->
            onDebugRequested?.invoke(item)
        }

        searchBoxPanel.isVisible = false
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onSearchTextChanged()
            override fun removeUpdate(e: DocumentEvent?) = onSearchTextChanged()
            override fun changedUpdate(e: DocumentEvent?) = onSearchTextChanged()
        })

        selectInterfaceTab(CARD_CONTROLLER)
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
            updateModuleFilterActions()
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
                activeTree().expandAll()
            })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.collapse.all"), AllIcons.Actions.Collapseall) {
                activeTree().collapseAll()
            })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.locate.current"), AllIcons.General.Locate) {
                locateCurrentEditorEndpoint()
            })
            addSeparator()
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.export.openapi"), AllIcons.FileTypes.Json) { exportOpenApi() })
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
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(1, 6, 1, 6),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(toolbar.component, BorderLayout.WEST)
        }
    }

    private fun filterPopupButton(group: DefaultActionGroup): JButton =
        JButton(group.templatePresentation.text, AllIcons.General.Dropdown).apply {
            filterButtons[group] = this
            font = ApiHelperUiStyle.labelFont()
            foreground = UIUtil.getLabelForeground()
            horizontalAlignment = SwingConstants.LEFT
            horizontalTextPosition = SwingConstants.LEFT
            verticalAlignment = SwingConstants.CENTER
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(0, JBUI.scale(2), 0, JBUI.scale(2))
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    background = ApiHelperUiStyle.hoverBackgroundColor()
                    isContentAreaFilled = true
                    isOpaque = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    isContentAreaFilled = false
                    isOpaque = false
                    repaint()
                }
            })
            addActionListener {
                ActionManager.getInstance()
                    .createActionPopupMenu(ActionPlaces.POPUP, group)
                    .component
                    .show(this, 0, height)
            }
        }

    private fun buildFilterBar(): JComponent {
        moduleFilterGroup.add(FacetFilterToggleAction(
            ApiHelperBundle.message("toolwindow.filter.all"),
            { selectedModules.isEmpty() },
        ) { selectedModules = emptySet(); applyFacetFilters() })
        moduleFilterGroup.add(SelectFacetAction { showModuleSelection() })
        moduleFilterGroup.addSeparator()

        frameworkFilterGroup.add(FacetFilterToggleAction(
            ApiHelperBundle.message("toolwindow.filter.all"),
            { selectedFrameworks.isEmpty() },
        ) { selectedFrameworks = emptySet(); applyFacetFilters() })
        frameworkFilterGroup.add(SelectFacetAction { showFrameworkSelection() })
        frameworkFilterGroup.addSeparator()
        EndpointKind.entries.forEach { kind ->
            frameworkFilterGroup.add(FacetFilterToggleAction(
                frameworkDisplayName(kind),
                { selectedFrameworks == setOf(kind) },
            ) { selectedFrameworks = setOf(kind); applyFacetFilters() })
        }

        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(filterPopupButton(moduleFilterGroup))
            add(filterPopupButton(frameworkFilterGroup))
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 6, 0, 6),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(buttons, BorderLayout.WEST)
        }
    }

    private fun buildIdeTabBar(): JComponent {
        ideTabButtons.clear()
        val tabsBox = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
            isOpaque = false
            add(ideTabButton(ApiHelperBundle.message("toolwindow.filter.type.server"), CARD_CONTROLLER))
            add(ideTabButton(ApiHelperBundle.message("toolwindow.filter.type.client"), CARD_FEIGN))
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
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 8),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(tabsBox, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    private fun ideTabButton(label: String, card: String): JComponent {
        val button = JLabel(label).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = UIUtil.getContextHelpForeground()
            font = ApiHelperUiStyle.labelFont(bold = false)
            border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(8))
            putClientProperty(KEY_CARD, card)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    selectInterfaceTab(card)
                }
            })
        }
        ideTabButtons.add(button)
        return button
    }

    private fun selectInterfaceTab(card: String) {
        activeEndpointType = if (card == CARD_CONTROLLER) EndpointTypeFilter.SERVER else EndpointTypeFilter.CLIENT
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
        label.font = ApiHelperUiStyle.labelFont(bold = active)
        val indicatorColor = if (active) ApiHelperUiStyle.primaryActionBackground() else Color(0, 0, 0, 0)
        label.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, JBUI.scale(2), 0, indicatorColor),
            BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(8), JBUI.scale(4), JBUI.scale(8)),
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
        formatTabTitle(ApiHelperBundle.message("toolwindow.filter.type.server"), total, filtered)

    private fun feignTabTitle(total: Int, filtered: Int): String =
        formatTabTitle(ApiHelperBundle.message("toolwindow.filter.type.client"), total, filtered)

    private fun formatTabTitle(baseTitle: String, total: Int, filtered: Int): String {
        val hasFilter = searchField.text.isNotBlank() ||
                currentPairingFilter != EndpointPairingFilter.ALL ||
                hasActiveFacetFilters()
        return if (hasFilter && filtered != total) {
            "$baseTitle ($filtered/$total)"
        } else {
            "$baseTitle ($total)"
        }
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
        if (trySelectMethodInTrees(method)) {
            return
        }
        if (searchField.text.isNotBlank() || currentPairingFilter != EndpointPairingFilter.ALL || hasActiveFacetFilters()) {
            searchField.text = ""
            applyPairingFilter(EndpointPairingFilter.ALL)
            selectedModules = emptySet()
            selectedFrameworks = emptySet()
            applyFacetFilters()
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
     * 导出全工程端点为 OpenAPI 3.0 JSON 并复制到剪贴板；导出在后台线程执行，避免阻塞 EDT。
     */
    private fun exportOpenApi() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = com.lizhuolun.apihelper.ui.openapi.OpenApiExporter.export(project) ?: return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (result.operationCount == 0) {
                    notifyBalloon(ApiHelperBundle.message("toolwindow.notification.openapi.empty"))
                    return@invokeLater
                }
                CopyPasteManager.getInstance().setContents(StringSelection(result.json))
                notifyBalloon(
                    ApiHelperBundle.message("toolwindow.notification.openapi.copied", result.operationCount),
                )
            }
        }
    }

    private fun toggleSearch() {
        isSearchVisible = !isSearchVisible
        searchBoxPanel.isVisible = isSearchVisible
        if (isSearchVisible) {
            searchField.textEditor.requestFocusInWindow()
        } else {
            searchField.text = ""
        }
        headerPanel.revalidate()
        headerPanel.repaint()
    }

    private fun activeTree(): EndpointTree = if (activeEndpointType == EndpointTypeFilter.SERVER) controllerTree else feignTree

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

    private inner class FacetFilterToggleAction(
        text: String,
        private val selected: () -> Boolean,
        private val onSelect: () -> Unit,
    ) : ToggleAction(text) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = selected()

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) onSelect()
        }
    }

    private inner class SelectFacetAction(
        private val onSelect: () -> Unit,
    ) : AnAction(ApiHelperBundle.message("toolwindow.filter.select")) {
        override fun actionPerformed(e: AnActionEvent) {
            onSelect()
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

    private fun applyFacetFilters() {
        when {
            selectedFrameworks == setOf(EndpointKind.CONTROLLER) -> selectInterfaceTab(CARD_CONTROLLER)
            selectedFrameworks.isNotEmpty() && EndpointKind.CONTROLLER !in selectedFrameworks -> {
                selectInterfaceTab(CARD_FEIGN)
            }
        }
        controllerTree.setFacetFilters(selectedModules, selectedFrameworks)
        feignTree.setFacetFilters(selectedModules, selectedFrameworks)
        updateIdeTabCounts(0, 0)
        updateSearchBadge()
    }

    private fun updateModuleFilterActions() {
        val modules = (controllerTree.getAvailableModules() + feignTree.getAvailableModules()).distinct().sorted()
        val selectedModulesChanged = selectedModules.any { it !in modules }
        if (selectedModulesChanged) selectedModules = selectedModules.intersect(modules.toSet())

        moduleFilterGroup.removeAll()
        moduleFilterGroup.add(FacetFilterToggleAction(
            ApiHelperBundle.message("toolwindow.filter.all"),
            { selectedModules.isEmpty() },
        ) { selectedModules = emptySet(); applyFacetFilters() })
        moduleFilterGroup.add(SelectFacetAction { showModuleSelection() })
        moduleFilterGroup.addSeparator()
        for (moduleName in modules) {
            moduleFilterGroup.add(FacetFilterToggleAction(
                moduleName,
                { selectedModules == setOf(moduleName) },
            ) { selectedModules = setOf(moduleName); applyFacetFilters() })
        }
        if (selectedModulesChanged) applyFacetFilters()
    }

    private fun showModuleSelection() {
        val anchor = filterButtons[moduleFilterGroup] ?: return
        val modules = (controllerTree.getAvailableModules() + feignTree.getAvailableModules()).distinct().sorted()
        showFacetSelectionPopup(
            ApiHelperBundle.message("toolwindow.filter.select.module"),
            anchor,
            modules,
            selectedModules,
            { it },
        ) {
            selectedModules = it
            applyFacetFilters()
        }
    }

    private fun showFrameworkSelection() {
        val anchor = filterButtons[frameworkFilterGroup] ?: return
        showFacetSelectionPopup(
            ApiHelperBundle.message("toolwindow.filter.select.framework"),
            anchor,
            EndpointKind.entries,
            selectedFrameworks,
            ::frameworkDisplayName,
        ) {
            selectedFrameworks = it
            applyFacetFilters()
        }
    }

    private fun <T> showFacetSelectionPopup(
        title: String,
        anchor: JButton,
        values: List<T>,
        selected: Set<T>,
        displayName: (T) -> String,
        onSelected: (Set<T>) -> Unit,
    ) {
        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        val checkBoxes = values.map { JBCheckBox(displayName(it), it in selected) }
        for (checkBox in checkBoxes) {
            checkBox.addActionListener {
                onSelected(values.filterIndexed { index, _ -> checkBoxes[index].isSelected }.toSet())
            }
            content.add(checkBox)
        }
        val scrollPane = JBScrollPane(content)
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, checkBoxes.firstOrNull() ?: content)
            .setTitle(title)
            .createPopup()
            .showUnderneathOf(anchor)
    }

    private fun frameworkDisplayName(kind: EndpointKind): String = when (kind) {
        EndpointKind.CONTROLLER -> ApiHelperBundle.message("toolwindow.filter.framework.spring.mvc")
        EndpointKind.FEIGN -> ApiHelperBundle.message("toolwindow.filter.framework.openfeign")
        EndpointKind.HTTP_EXCHANGE -> ApiHelperBundle.message("toolwindow.filter.framework.http.exchange")
    }

    private fun hasActiveFacetFilters(): Boolean =
        selectedModules.isNotEmpty() || selectedFrameworks.isNotEmpty()

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
