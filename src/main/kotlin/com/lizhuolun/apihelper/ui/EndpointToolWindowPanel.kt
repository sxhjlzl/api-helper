package com.lizhuolun.apihelper.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
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
import com.lizhuolun.apihelper.ui.debug.EndpointDebugPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * ApiHelper 工具窗口主面板，采用紧凑的 IDE 原生风格实现。
 *
 * 顶部包含 IDE 工具栏与端点类型 Tab，主体通过卡片布局切换"接口列表"与"接口调试"两个工作区。
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

    private val interfaceCardLayout = CardLayout()
    private val interfaceContentPanel = JPanel(interfaceCardLayout).apply {
        isOpaque = false
    }

    private val mainCardLayout = CardLayout()
    private val mainContentPanel = JPanel(mainCardLayout).apply {
        isOpaque = false
    }

    private val searchField = JBTextField().apply {
        emptyText.text = ApiHelperBundle.message("toolwindow.filter.placeholder")
        ApiHelperUiStyle.applyTextField(this)
    }
    private val searchBoxPanel = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
            BorderFactory.createEmptyBorder(6, 10, 6, 10),
        )
        add(JBLabel(AllIcons.Actions.Search).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
        }, BorderLayout.WEST)
        add(searchField, BorderLayout.CENTER)
    }

    private val searchBadgeLabel = JLabel("").apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private val mainNavButtons = mutableListOf<JLabel>()
    private val mainNavBar = buildMainNavBar()
    private val toolbarComponent = buildToolbar()
    private val ideTabButtons = mutableListOf<JLabel>()
    private val ideTabBar = buildIdeTabBar()

    private val headerPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(mainNavBar, BorderLayout.NORTH)
        add(toolbarComponent, BorderLayout.CENTER)
        add(ideTabBar, BorderLayout.SOUTH)
    }

    private var isSearchVisible: Boolean = false
    private var activeMainCard: String = CARD_INTERFACE
    private var activeInterfaceTab: String = CARD_CONTROLLER

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        interfaceContentPanel.add(controllerTree, CARD_CONTROLLER)
        interfaceContentPanel.add(feignTree, CARD_FEIGN)

        val interfacePanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(searchBoxPanel, BorderLayout.NORTH)
            add(interfaceContentPanel, BorderLayout.CENTER)
        }

        mainContentPanel.add(interfacePanel, CARD_INTERFACE)
        mainContentPanel.add(debugPanel, CARD_DEBUG)

        add(headerPanel, BorderLayout.NORTH)
        add(mainContentPanel, BorderLayout.CENTER)

        controllerTree.onCountsChanged = { total, filtered ->
            updateIdeTabCounts(total, filtered)
        }
        feignTree.onCountsChanged = { total, filtered ->
            updateIdeTabCounts(total, filtered)
        }
        controllerTree.onDebugRequested = { showDebugPanel(it) }
        feignTree.onDebugRequested = { showDebugPanel(it) }

        searchBoxPanel.isVisible = false
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onSearchTextChanged()
            override fun removeUpdate(e: DocumentEvent?) = onSearchTextChanged()
            override fun changedUpdate(e: DocumentEvent?) = onSearchTextChanged()
        })

        showInterfaceList()
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
    fun refresh() {
        if (project.isDisposed) return
        val cache = BilateralMappingCacheService.of(project)
        var controllers = cache.getAllControllerMappings()
        var clients = cache.getAllClientMappings()

        if (controllers.isEmpty() || clients.isEmpty()) {
            val (scannedControllers, scannedClients) = scanInBackground()
            if (controllers.isEmpty()) controllers = scannedControllers
            if (clients.isEmpty()) clients = scannedClients
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            controllerTree.refresh(controllers)
            feignTree.refresh(clients)
        }
    }

    /**
     * 创建工具窗口标题栏动作。
     *
     * @return 标题栏动作列表
     */
    fun createTitleActions(): List<AnAction> {
        return listOf(
            TopBarAction(null, ApiHelperBundle.message("toolwindow.action.settings"), AllIcons.General.Settings) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, ApiHelperConfigurable::class.java)
            },
        )
    }

    private fun buildMainNavBar(): JComponent {
        mainNavButtons.clear()
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(mainNavButton(ApiHelperBundle.message("toolwindow.nav.interfaces"), CARD_INTERFACE))
            add(mainNavButton(ApiHelperBundle.message("toolwindow.nav.debug"), CARD_DEBUG))
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(0, 6, 0, 6),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(left, BorderLayout.WEST)
        }
    }

    private fun mainNavButton(label: String, card: String): JComponent {
        val button = JLabel(label).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = font?.deriveFont(Font.PLAIN, 12f)
            border = BorderFactory.createEmptyBorder(JBUI.scale(5), JBUI.scale(10), JBUI.scale(5), JBUI.scale(10))
            putClientProperty(KEY_CARD, card)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    when (card) {
                        CARD_INTERFACE -> showInterfaceList()
                        CARD_DEBUG -> showDebugPanel()
                    }
                }
            })
        }
        mainNavButtons.add(button)
        return button
    }

    private fun buildToolbar(): JComponent {
        val actionGroup = DefaultActionGroup().apply {
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.refresh"), AllIcons.Actions.Refresh) { refresh() })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.search"), AllIcons.Actions.Search) { toggleSearch() })
            addSeparator()
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.expand.all"), AllIcons.Actions.Expandall) {
                activeTree().expandAll()
            })
            add(ToolbarAction(ApiHelperBundle.message("toolwindow.action.collapse.all"), AllIcons.Actions.Collapseall) {
                activeTree().collapseAll()
            })
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

    private fun buildIdeTabBar(): JComponent {
        ideTabButtons.clear()
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(ideTabButton(ApiHelperBundle.message("toolwindow.tab.controller"), CARD_CONTROLLER))
            add(ideTabButton(ApiHelperBundle.message("toolwindow.tab.feign"), CARD_FEIGN))
        }

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            add(searchBadgeLabel)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(0, 6, 0, 6),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    private fun ideTabButton(label: String, card: String): JComponent {
        val button = JLabel(label).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = font?.deriveFont(Font.PLAIN, 12f)
            border = BorderFactory.createEmptyBorder(JBUI.scale(7), JBUI.scale(10), JBUI.scale(7), JBUI.scale(10))
            putClientProperty(KEY_CARD, card)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
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
            BorderFactory.createMatteBorder(0, 0, 1, 0, if (active) UIUtil.getLabelForeground() else Color(0, 0, 0, 0)),
            BorderFactory.createEmptyBorder(JBUI.scale(7), JBUI.scale(10), JBUI.scale(6), JBUI.scale(10)),
        )
        label.isOpaque = false
    }

    private fun updateIdeTabCounts(total: Int, filtered: Int) {
        ideTabButtons.forEach { btn ->
            when (btn.getClientProperty(KEY_CARD)) {
                CARD_CONTROLLER -> btn.text = controllerTabTitle(controllerTree.getTotalCount())
                CARD_FEIGN -> btn.text = feignTabTitle(feignTree.getTotalCount())
            }
        }
    }

    private fun controllerTabTitle(count: Int): String =
        "${ApiHelperBundle.message("toolwindow.tab.controller")} [$count]"

    private fun feignTabTitle(count: Int): String =
        "${ApiHelperBundle.message("toolwindow.tab.feign")} [$count]"

    private fun showInterfaceList() {
        activeMainCard = CARD_INTERFACE
        mainCardLayout.show(mainContentPanel, CARD_INTERFACE)
        updateMainNavStyle()
        updateInterfaceHeaderVisibility()
    }

    private fun showDebugPanel() {
        activeMainCard = CARD_DEBUG
        mainCardLayout.show(mainContentPanel, CARD_DEBUG)
        updateMainNavStyle()
        updateInterfaceHeaderVisibility()
    }

    private fun showDebugPanel(item: EndpointTreeItem) {
        activeMainCard = CARD_DEBUG
        debugPanel.loadEndpoint(item)
        mainCardLayout.show(mainContentPanel, CARD_DEBUG)
        updateMainNavStyle()
        updateInterfaceHeaderVisibility()
    }

    /**
     * 当前是否展示接口列表。
     */
    fun isInterfaceListVisible(): Boolean = activeMainCard == CARD_INTERFACE

    /**
     * 当前是否展示接口调试。
     */
    fun isDebugVisible(): Boolean = activeMainCard == CARD_DEBUG

    private fun toggleSearch() {
        if (activeMainCard != CARD_INTERFACE) return
        isSearchVisible = !isSearchVisible
        searchBoxPanel.isVisible = isSearchVisible && activeMainCard == CARD_INTERFACE
        if (isSearchVisible) {
            searchField.requestFocusInWindow()
        } else {
            searchField.text = ""
        }
        revalidate()
        repaint()
    }

    private fun updateInterfaceHeaderVisibility() {
        val visible = activeMainCard == CARD_INTERFACE
        toolbarComponent.isVisible = visible
        ideTabBar.isVisible = visible
        searchBoxPanel.isVisible = visible && isSearchVisible
        headerPanel.revalidate()
        headerPanel.repaint()
    }

    private fun updateMainNavStyle() {
        mainNavButtons.forEach { button ->
            val card = button.getClientProperty(KEY_CARD) as? String
            val active = card == activeMainCard
            button.foreground = if (active) UIUtil.getLabelForeground() else UIUtil.getContextHelpForeground()
            button.font = button.font.deriveFont(if (active) Font.BOLD else Font.PLAIN, 12f)
            button.background = if (active) activeNavBackground() else UIUtil.getPanelBackground()
            button.border = BorderFactory.createEmptyBorder(JBUI.scale(5), JBUI.scale(10), JBUI.scale(5), JBUI.scale(10))
            button.isOpaque = active
        }
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

    private fun scanInBackground(): Pair<List<HttpMappingInfo>, List<HttpMappingInfo>> {
        return try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<Pair<List<HttpMappingInfo>, List<HttpMappingInfo>>, RuntimeException>(
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
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.warn("ApiHelper: 工具窗口扫描端点失败, project=${project.name}", e)
            emptyList<HttpMappingInfo>() to emptyList<HttpMappingInfo>()
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
        private const val CARD_INTERFACE = "interface"
        private const val CARD_DEBUG = "debug"
        private const val CARD_CONTROLLER = "controller"
        private const val CARD_FEIGN = "feign"
        private const val KEY_CARD = "card"

        private fun activeNavBackground(): Color =
            UIManager.getColor("Button.background")
                ?: UIManager.getColor("Tree.selectionInactiveBackground")
                ?: UIUtil.getPanelBackground()
    }
}
