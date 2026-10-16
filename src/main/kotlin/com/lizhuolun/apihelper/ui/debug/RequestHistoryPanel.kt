package com.lizhuolun.apihelper.ui.debug

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import com.lizhuolun.apihelper.ui.component.HttpMethodBadge
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * “请求历史”独立管理面板（对应顶部一级标签页：请求历史）。
 *
 * 采用上下分栏设计：
 * - 顶部：搜索过滤框 + 图标操作按钮（回放选中请求 / 复制 cURL / 删除选中 / 清空全部）
 * - 上半区：历史请求列表（支持关键词过滤、右键菜单、Delete 删除单条、双击或按回车回放到调试页）
 * - 下半区：即时预览区，单击选中任意历史记录即可直接查看该次请求的 URL、状态耗时、请求体与响应体详情
 *
 * @param project 当前工程
 * @param onReplaySelected 选中一条历史记录触发回放时的回调
 * @author lizhuolun
 * @date 2026/10/16
 */
class RequestHistoryPanel(
    private val project: Project,
    private val onReplaySelected: (DebugHistoryEntry) -> Unit,
) : JPanel(BorderLayout()) {

    private var allEntries: List<DebugHistoryEntry> = emptyList()
    private val listModel = DefaultListModel<DebugHistoryEntry>()
    private val historyList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = HistoryListCellRenderer()
        emptyText.text = ApiHelperBundle.message("debug.history.empty")
    }

    private val searchField = SearchTextField(false).apply {
        textEditor.emptyText.text = ApiHelperBundle.message("toolwindow.history.search.placeholder")
        ApiHelperUiStyle.applyTextField(textEditor)
    }

    private val previewTitleLabel = JBLabel(ApiHelperBundle.message("toolwindow.history.preview.empty")).apply {
        font = ApiHelperUiStyle.smallFont(bold = true)
        foreground = UIUtil.getContextHelpForeground()
    }

    private val previewArea = JBTextArea().apply {
        isEditable = false
        font = ApiHelperUiStyle.codeFont()
        lineWrap = false
        border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(8))
    }

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        val actionsBox = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply {
            isOpaque = false
            add(HeaderIconButton(AllIcons.Actions.Execute, ApiHelperBundle.message("toolwindow.history.action.replay")).apply {
                addActionListener { replaySelected() }
            })
            add(HeaderIconButton(AllIcons.Actions.Copy, ApiHelperBundle.message("toolwindow.action.copy.curl")).apply {
                addActionListener { copySelectedCurl() }
            })
            add(HeaderIconButton(AllIcons.Actions.Cancel, ApiHelperBundle.message("toolwindow.history.action.delete")).apply {
                addActionListener { deleteSelected() }
            })
            add(HeaderIconButton(AllIcons.Actions.GC, ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener {
                    DebugDraftService.of(project).clearHistory()
                    refreshHistory()
                }
            })
        }

        val topBar = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8),
            )
            add(searchField, BorderLayout.CENTER)
            add(actionsBox, BorderLayout.EAST)
        }

        val previewHeader = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(4, 8, 4, 8),
            )
            add(previewTitleLabel, BorderLayout.WEST)
        }

        val previewPane = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(previewHeader, BorderLayout.NORTH)
            add(JBScrollPane(previewArea).apply {
                border = BorderFactory.createEmptyBorder()
            }, BorderLayout.CENTER)
        }

        val splitter = OnePixelSplitter(true, "ApiHelper.HistorySplitter", 0.55f).apply {
            firstComponent = JBScrollPane(historyList).apply {
                border = BorderFactory.createEmptyBorder()
            }
            secondComponent = previewPane
        }

        setupListeners()

        add(topBar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })

        historyList.addListSelectionListener {
            updatePreview(historyList.selectedValue)
        }

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val selected = historyList.selectedValue ?: return false
                onReplaySelected(selected)
                return true
            }
        }.installOn(historyList)

        historyList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        replaySelected()
                        e.consume()
                    }
                    KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
                        deleteSelected()
                        e.consume()
                    }
                }
            }
        })

        historyList.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)

            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val idx = historyList.locationToIndex(e.point)
                if (idx >= 0) {
                    historyList.selectedIndex = idx
                    buildPopupMenu().show(historyList, e.x, e.y)
                }
            }
        })
    }

    private fun buildPopupMenu(): JPopupMenu {
        val group = DefaultActionGroup().apply {
            add(object : AnAction(ApiHelperBundle.message("toolwindow.history.action.replay"), null, AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) = replaySelected()
            })
            add(object : AnAction(ApiHelperBundle.message("toolwindow.action.copy.curl"), null, AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) = copySelectedCurl()
            })
            addSeparator()
            add(object : AnAction(ApiHelperBundle.message("toolwindow.history.action.delete"), null, AllIcons.Actions.Cancel) {
                override fun actionPerformed(e: AnActionEvent) = deleteSelected()
            })
        }
        return ActionManager.getInstance()
            .createActionPopupMenu(ActionPlaces.POPUP, group)
            .component
    }

    private fun replaySelected() {
        val selected = historyList.selectedValue ?: return
        onReplaySelected(selected)
    }

    private fun deleteSelected() {
        val selected = historyList.selectedValue ?: return
        DebugDraftService.of(project).removeHistory(selected)
        refreshHistory()
    }

    private fun copySelectedCurl() {
        val selected = historyList.selectedValue ?: return
        val draft = selected.draft
        val settings = ApiHelperSettings.getInstance()
        val mergedHeaders = CurlCommandBuilder.mergeHeaders(settings.parseGlobalHeaders(), draft.headers)
        val mergedQueries = CurlCommandBuilder.mergeHeaders(settings.parseGlobalQueries(), draft.query)
        val mergedCookies = CurlCommandBuilder.mergeHeaders(settings.parseGlobalCookies(), draft.cookies)
        val curl = CurlCommandBuilder.build(
            method = draft.method,
            rawUrl = draft.url,
            pathParams = draft.path,
            queryParams = mergedQueries,
            headers = mergedHeaders,
            cookies = mergedCookies,
            bodyText = draft.bodyText,
            formDataRows = draft.formData,
            binaryFilePath = draft.binaryPath,
        )
        CopyPasteManager.getInstance().setContents(StringSelection(curl))
        if (!project.isDisposed) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("ApiHelper")
                .createNotification(
                    ApiHelperBundle.message("debug.notification.curl.copied"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
    }

    /**
     * 从 [DebugDraftService] 刷新历史记录列表。
     */
    fun refreshHistory() {
        allEntries = DebugDraftService.of(project).getHistory()
        applyFilter()
    }

    private fun applyFilter() {
        val query = searchField.text.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                entry.draft.url.lowercase().contains(query) ||
                    entry.draft.method.lowercase().contains(query) ||
                    (entry.statusCode?.toString()?.contains(query) == true)
            }
        }
        listModel.clear()
        filtered.forEach(listModel::addElement)
        if (filtered.isNotEmpty()) {
            historyList.selectedIndex = 0
        } else {
            updatePreview(null)
        }
    }

    private fun updatePreview(entry: DebugHistoryEntry?) {
        if (entry == null) {
            previewTitleLabel.text = ApiHelperBundle.message("toolwindow.history.preview.empty")
            previewArea.text = ""
            return
        }
        val statusText = entry.statusCode?.toString() ?: "ERR"
        previewTitleLabel.text = "[${entry.timestampText}] ${entry.draft.method} ${entry.draft.url}  ·  $statusText (${entry.elapsedMs} ms)"
        previewArea.text = buildString {
            if (entry.draft.bodyText.isNotBlank()) {
                appendLine("=== Request Body ===")
                appendLine(entry.draft.bodyText)
                appendLine()
            }
            if (entry.responseHeaders.isNotBlank()) {
                appendLine("=== Response Headers ===")
                appendLine(entry.responseHeaders)
                appendLine()
            }
            appendLine("=== Response Body ===")
            append(entry.responseBody)
        }
        previewArea.caretPosition = 0
    }

    private class HeaderIconButton(icon: Icon, tooltip: String) : JButton(icon) {
        init {
            toolTipText = tooltip
            ApiHelperUiStyle.applyIconActionButton(this)
        }

        override fun paintComponent(g: Graphics) {
            ApiHelperUiStyle.paintIconButtonHover(this, g)
            super.paintComponent(g)
        }
    }

    private class HistoryListCellRenderer : JPanel(BorderLayout(JBUI.scale(8), 0)), ListCellRenderer<DebugHistoryEntry> {

        private val methodBadge = HttpMethodBadge(HttpMethod.GET)
        private val urlLabel = JLabel().apply {
            font = ApiHelperUiStyle.labelFont()
        }
        private val statusLabel = JLabel().apply {
            font = ApiHelperUiStyle.smallFont(bold = true)
        }
        private val timeLabel = JLabel().apply {
            font = ApiHelperUiStyle.smallFont()
        }
        private val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(statusLabel)
            add(timeLabel)
        }

        init {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(10)),
            )
            add(methodBadge, BorderLayout.WEST)
            add(urlLabel, BorderLayout.CENTER)
            add(rightPanel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out DebugHistoryEntry>,
            value: DebugHistoryEntry?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            if (value == null) return this
            isOpaque = true
            background = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else UIUtil.getLabelForeground()
            val muted = if (isSelected) list.selectionForeground else UIUtil.getContextHelpForeground()

            methodBadge.method = HttpMethod.fromName(value.draft.method)
            urlLabel.text = value.draft.url
            urlLabel.foreground = fg

            val code = value.statusCode
            statusLabel.text = if (code != null) "$code (${value.elapsedMs}ms)" else "ERR (${value.elapsedMs}ms)"
            statusLabel.foreground = if (isSelected) {
                list.selectionForeground
            } else {
                when (code) {
                    in 200..299 -> JBColor(Color(0x177245), Color(0x7ED69C))
                    in 300..399 -> JBColor(Color(0x966313), Color(0xE0B56B))
                    else -> UIUtil.getErrorForeground()
                }
            }
            timeLabel.text = value.timestampText
            timeLabel.foreground = muted
            return this
        }
    }
}
