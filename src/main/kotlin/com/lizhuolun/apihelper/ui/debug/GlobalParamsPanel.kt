package com.lizhuolun.apihelper.ui.debug

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * “全局参数”独立管理面板（对应顶部一级标签页：全局参数）。
 *
 * 采用与调试页一致的胶囊式子标签切换栏（Headers / Query / Cookies）+ 带启用复选框的网格参数表：
 * - 支持分别管理全局公共 Header、全局公共 Query 参数与全局公共 Cookie；
 * - 支持单独勾选/取消勾选某一项参数，未勾选项自动保留在表格中但不会随请求发送；
 * - 修改后实时写回 [ApiHelperSettings]，发送调试请求与生成 cURL 时自动合并生效。
 *
 * @param autoPersist 表格变更时是否立即写入全局设置（作为设置页子组件嵌入时可传 false）
 * @author lizhuolun
 * @date 2026/10/16
 */
class GlobalParamsPanel(private val autoPersist: Boolean = true) : JPanel(BorderLayout()) {

    private val headerTableModel = DebugParameterTableModel(
        nameColumnKey = "debug.table.name",
        valueColumnKey = "debug.table.value",
        showDeleteColumn = true,
        showEnabledColumn = true,
    )
    private val queryTableModel = DebugParameterTableModel(
        nameColumnKey = "debug.table.name",
        valueColumnKey = "debug.table.value",
        showDeleteColumn = true,
        showEnabledColumn = true,
    )
    private val cookieTableModel = DebugParameterTableModel(
        nameColumnKey = "debug.table.name",
        valueColumnKey = "debug.table.value",
        showDeleteColumn = true,
        showEnabledColumn = true,
    )
    private val variableTableModel = DebugParameterTableModel(
        nameColumnKey = "debug.table.name",
        valueColumnKey = "debug.table.value",
        showDeleteColumn = true,
        showEnabledColumn = true,
    )

    private val hintLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        font = ApiHelperUiStyle.smallFont()
    }

    private val tabPills = LinkedHashMap<String, GlobalSubTabPill>()
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout).apply {
        isOpaque = false
    }
    private var activeTabKey: String = TAB_HEADERS
    private var updatingUi = false

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        val leftTabs = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false
            addSubTabPill(this, TAB_HEADERS, ApiHelperBundle.message("debug.tab.headers"))
            addSubTabPill(this, TAB_QUERY, ApiHelperBundle.message("debug.tab.query"))
            addSubTabPill(this, TAB_COOKIES, ApiHelperBundle.message("debug.tab.cookies"))
            addSubTabPill(this, TAB_VARIABLES, ApiHelperBundle.message("debug.tab.variables"))
        }

        val rightActions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(3))).apply {
            isOpaque = false
            add(hintLabel)
            add(HeaderIconButton(AllIcons.Actions.GC, ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener { activeModel().clear() }
            })
        }

        val topBar = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(0, 4, 0, 6),
            )
            add(leftTabs, BorderLayout.WEST)
            add(rightActions, BorderLayout.EAST)
        }

        cardPanel.add(buildTableScroll(headerTableModel, "Authorization", "Bearer <token>"), TAB_HEADERS)
        cardPanel.add(buildTableScroll(queryTableModel, "tenantId", "1001"), TAB_QUERY)
        cardPanel.add(buildTableScroll(cookieTableModel, "SESSION", "xyz"), TAB_COOKIES)
        cardPanel.add(buildTableScroll(variableTableModel, "token 或 Dev.token", "abc123"), TAB_VARIABLES)

        val listener: (javax.swing.event.TableModelEvent) -> Unit = {
            if (!updatingUi) {
                updateTabCounts()
                if (autoPersist) {
                    persistToSettings()
                }
            }
        }
        headerTableModel.addTableModelListener(listener)
        queryTableModel.addTableModelListener(listener)
        cookieTableModel.addTableModelListener(listener)
        variableTableModel.addTableModelListener(listener)

        add(topBar, BorderLayout.NORTH)
        add(cardPanel, BorderLayout.CENTER)

        selectSubTab(TAB_HEADERS)
        loadFromSettings()
    }

    private fun addSubTabPill(container: JPanel, key: String, title: String) {
        val pill = GlobalSubTabPill(title) { selectSubTab(key) }
        tabPills[key] = pill
        container.add(pill)
    }

    private fun selectSubTab(key: String) {
        activeTabKey = key
        tabPills.forEach { (k, pill) -> pill.selected = (k == key) }
        hintLabel.text = ApiHelperBundle.message(
            if (key == TAB_VARIABLES) "toolwindow.global.variables.hint" else "toolwindow.global.headers.hint",
        )
        cardLayout.show(cardPanel, key)
    }

    private fun activeModel(): DebugParameterTableModel =
        when (activeTabKey) {
            TAB_QUERY -> queryTableModel
            TAB_COOKIES -> cookieTableModel
            TAB_VARIABLES -> variableTableModel
            else -> headerTableModel
        }

    private fun buildTableScroll(
        model: DebugParameterTableModel,
        exampleKey: String,
        exampleVal: String,
    ): JComponent {
        val table = JTable(model).apply {
            ApiHelperUiStyle.styleParameterTable(
                table = this,
                nameColumnIndex = 1,
                valueColumnIndex = 2,
                namePlaceholder = "+ ${ApiHelperBundle.message("debug.table.name")} (e.g. $exampleKey)",
                valuePlaceholder = exampleVal,
            )
            columnModel.getColumn(1).preferredWidth = JBUI.scale(140)
            val deleteCol = columnModel.getColumn(columnCount - 1)
            deleteCol.maxWidth = JBUI.scale(34)
            deleteCol.preferredWidth = JBUI.scale(34)
            deleteCol.minWidth = JBUI.scale(34)
            deleteCol.cellRenderer = DeleteCellRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val r = rowAtPoint(e.point)
                    val c = columnAtPoint(e.point)
                    if (r >= 0 && c == columnCount - 1) {
                        model.removeRow(r)
                    }
                }
            })
        }
        return JBScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
        }
    }

    /**
     * 从全局设置同步公共 Headers / Query / Cookies / 变量到表格。
     */
    fun loadFromSettings() {
        val settings = ApiHelperSettings.getInstance()
        loadFromTexts(
            headersText = settings.state.globalHeadersText,
            queriesText = settings.state.globalQueriesText,
            cookiesText = settings.state.globalCookiesText,
        )
        loadVariablesFromSettings()
    }

    /**
     * 从全局设置同步变量表：全局变量在前，环境级变量（`环境名.变量名`）在后。
     */
    fun loadVariablesFromSettings() {
        val settings = ApiHelperSettings.getInstance()
        updatingUi = true
        try {
            val rows = settings.parseGlobalVariableRows() + settings.parseEnvironmentVariableRows()
            variableTableModel.setRows(rows)
            updateTabCounts()
        } finally {
            updatingUi = false
        }
    }

    /**
     * 使用指定的三组原始配置文本填充表格。
     */
    fun loadFromTexts(headersText: String, queriesText: String, cookiesText: String) {
        updatingUi = true
        try {
            headerTableModel.setRows(ApiHelperSettings.parseParameterRows(headersText, charArrayOf(':', '=')))
            queryTableModel.setRows(ApiHelperSettings.parseParameterRows(queriesText, charArrayOf('=', ':')))
            cookieTableModel.setRows(ApiHelperSettings.parseParameterRows(cookiesText, charArrayOf('=', ':')))
            updateTabCounts()
        } finally {
            updatingUi = false
        }
    }

    /**
     * 导出当前全局 Headers 文本（含 `[disabled]` 标记）。
     */
    fun exportGlobalHeadersText(): String =
        ApiHelperSettings.formatParameterRows(headerTableModel.toAllRows(), separator = ": ")

    /**
     * 导出当前全局 Query 参数文本（含 `[disabled]` 标记）。
     */
    fun exportGlobalQueriesText(): String =
        ApiHelperSettings.formatParameterRows(queryTableModel.toAllRows(), separator = "=")

    /**
     * 导出当前全局 Cookie 参数文本（含 `[disabled]` 标记）。
     */
    fun exportGlobalCookiesText(): String =
        ApiHelperSettings.formatParameterRows(cookieTableModel.toAllRows(), separator = "=")

    /**
     * 导出当前全局变量文本（含 `[disabled]` 标记），名称含 `.` 的环境级变量不在其列。
     */
    fun exportGlobalVariablesText(): String =
        ApiHelperSettings.formatParameterRows(
            variableTableModel.toAllRows().filter { '.' !in it.name },
            separator = "=",
        )

    /**
     * 导出当前环境级变量文本（含 `[disabled]` 标记），即名称形如 `环境名.变量名` 的行。
     */
    fun exportEnvironmentVariablesText(): String =
        ApiHelperSettings.formatParameterRows(
            variableTableModel.toAllRows().filter { '.' in it.name },
            separator = "=",
        )

    /**
     * 将当前表格中的三组全局参数与变量写回 [ApiHelperSettings]。
     */
    fun persistToSettings() {
        val state = ApiHelperSettings.getInstance().state
        state.globalHeadersText = exportGlobalHeadersText()
        state.globalQueriesText = exportGlobalQueriesText()
        state.globalCookiesText = exportGlobalCookiesText()
        state.globalVariablesText = exportGlobalVariablesText()
        state.environmentVariablesText = exportEnvironmentVariablesText()
    }

    private fun updateTabCounts() {
        tabPills[TAB_HEADERS]?.text = formatCountTitle(ApiHelperBundle.message("debug.tab.headers"), headerTableModel.toPairs().size)
        tabPills[TAB_QUERY]?.text = formatCountTitle(ApiHelperBundle.message("debug.tab.query"), queryTableModel.toPairs().size)
        tabPills[TAB_COOKIES]?.text = formatCountTitle(ApiHelperBundle.message("debug.tab.cookies"), cookieTableModel.toPairs().size)
        tabPills[TAB_VARIABLES]?.text = formatCountTitle(ApiHelperBundle.message("debug.tab.variables"), variableTableModel.toPairs().size)
    }

    private fun formatCountTitle(base: String, count: Int): String =
        if (count > 0) "$base ($count)" else base

    private class GlobalSubTabPill(
        text: String,
        onClick: () -> Unit,
    ) : JLabel(text, CENTER) {

        var selected: Boolean = false
            set(value) {
                field = value
                font = ApiHelperUiStyle.labelFont(bold = value)
                foreground = if (value) {
                    ApiHelperUiStyle.activeTabPillForeground()
                } else {
                    UIUtil.getLabelForeground()
                }
                repaint()
            }

        private var hovered: Boolean = false

        init {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(10), JBUI.scale(4), JBUI.scale(10))
            selected = false
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = onClick()
                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    repaint()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            if (selected || hovered) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val arc = JBUI.scale(6)
                    if (selected) {
                        g2.color = ApiHelperUiStyle.activeTabPillBackground()
                        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                        g2.color = ApiHelperUiStyle.activeTabPillBorder()
                        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
                    } else {
                        g2.color = ApiHelperUiStyle.hoverBackgroundColor()
                        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
                    }
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }
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

    private class DeleteCellRenderer : JLabel(AllIcons.Actions.GC, CENTER), TableCellRenderer {
        init {
            horizontalAlignment = CENTER
            verticalAlignment = CENTER
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder()
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            background = if (isSelected) table.selectionBackground else table.background
            isOpaque = true
            return this
        }
    }

    companion object {
        private const val TAB_HEADERS = "headers"
        private const val TAB_QUERY = "query"
        private const val TAB_COOKIES = "cookies"
        private const val TAB_VARIABLES = "variables"
    }
}
