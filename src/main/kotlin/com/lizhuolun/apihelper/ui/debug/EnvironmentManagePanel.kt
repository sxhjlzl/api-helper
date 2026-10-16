package com.lizhuolun.apihelper.ui.debug

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.EndpointToolWindowService
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import com.lizhuolun.apihelper.ui.component.RoundedBadgeLabel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * “前置 URL”独立管理面板（对应顶部一级标签页：前置 URL）。
 *
 * 采用与调试页一致的带复选框网格表格：
 * - 第一列复选框表示“当前生效环境”（互斥单选，勾选某一环境即激活该环境，全部取消勾选时自动回退为本地端口推断）；
 * - 顶部栏展示当前生效环境胶囊徽章，并提供“添加预设”与“清空”快捷按钮；
 * - 修改后实时同步至 [ApiHelperSettings] 与调试面板（若作为设置页子组件嵌入时可设为仅手动提交）。
 *
 * @param project 当前工程，在应用级 Settings 页中嵌入时可传 null
 * @param autoPersist 表格变更时是否立即写入全局设置并广播通知
 * @author lizhuolun
 * @date 2026/10/16
 */
class EnvironmentManagePanel(
    private val project: Project? = null,
    private val autoPersist: Boolean = true,
) : JPanel(BorderLayout()) {

    private val tableModel = DebugParameterTableModel(
        nameColumnKey = "toolwindow.env.column.name",
        valueColumnKey = "toolwindow.env.column.url",
        showDeleteColumn = true,
        showEnabledColumn = true,
    )

    private val activeEnvBadge = RoundedBadgeLabel()
    private var updatingUi = false

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        val leftInfo = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false
            add(JBLabel(ApiHelperBundle.message("toolwindow.env.active.label")).apply {
                font = ApiHelperUiStyle.smallFont(bold = true)
                foreground = UIUtil.getContextHelpForeground()
            })
            add(activeEnvBadge)
        }

        val rightActions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), JBUI.scale(2))).apply {
            isOpaque = false
            add(JButton(ApiHelperBundle.message("toolwindow.env.action.preset")).apply {
                ApiHelperUiStyle.applyToolbarPillButton(this)
                addActionListener { addDefaultPresets() }
            })
            add(HeaderIconButton(AllIcons.Actions.GC, ApiHelperBundle.message("debug.action.clear")).apply {
                addActionListener { tableModel.clear() }
            })
        }

        val topBar = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.subtleGridColor()),
                BorderFactory.createEmptyBorder(2, 6, 2, 6),
            )
            add(leftInfo, BorderLayout.WEST)
            add(rightActions, BorderLayout.EAST)
        }

        val table = JTable(tableModel).apply {
            ApiHelperUiStyle.styleParameterTable(
                table = this,
                nameColumnIndex = 1,
                valueColumnIndex = 2,
                namePlaceholder = "+ ${ApiHelperBundle.message("toolwindow.env.column.name")} (e.g. Dev)",
                valuePlaceholder = "http://dev.example.com:8080",
            )
            columnModel.getColumn(1).preferredWidth = JBUI.scale(110)
            columnModel.getColumn(1).maxWidth = JBUI.scale(180)
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
                        tableModel.removeRow(r)
                    }
                }
            })
        }

        tableModel.addTableModelListener { event ->
            if (updatingUi) return@addTableModelListener
            // 若用户勾选了某一行的启用复选框，互斥取消其余行的勾选，实现单选切换当前环境
            if (event.column == 0 && event.firstRow >= 0 && event.firstRow < tableModel.rowCount) {
                val checked = tableModel.getValueAt(event.firstRow, 0) as? Boolean ?: false
                if (checked) {
                    updatingUi = true
                    try {
                        for (i in 0 until tableModel.rowCount) {
                            if (i != event.firstRow && (tableModel.getValueAt(i, 0) as? Boolean == true)) {
                                tableModel.setValueAt(false, i, 0)
                            }
                        }
                    } finally {
                        updatingUi = false
                    }
                }
            }
            updateActiveBadge()
            if (autoPersist) {
                persistToSettings()
            }
        }

        add(topBar, BorderLayout.NORTH)
        add(JBScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)

        loadFromSettings()
    }

    /**
     * 从全局设置加载环境配置到表格。
     */
    fun loadFromSettings() {
        val settings = ApiHelperSettings.getInstance()
        loadFromText(settings.state.environmentsText, settings.state.selectedEnvironmentName)
    }

    /**
     * 使用给定环境文本与选中环境名填充表格。
     */
    fun loadFromText(environmentsText: String, selectedEnvName: String) {
        updatingUi = true
        try {
            val rows = environmentsText
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val eqIdx = line.indexOf('=')
                    if (eqIdx > 0) {
                        val name = line.substring(0, eqIdx).trim()
                        val url = line.substring(eqIdx + 1).trim().trimEnd('/')
                        if (name.isNotEmpty() && url.isNotEmpty()) {
                            DebugParameterRow(enabled = (name == selectedEnvName), name = name, value = url)
                        } else {
                            null
                        }
                    } else {
                        val url = line.trimEnd('/')
                        DebugParameterRow(enabled = (url == selectedEnvName), name = url, value = url)
                    }
                }
                .distinctBy { it.name }
                .toList()
            tableModel.setRows(rows)
            updateActiveBadge()
        } finally {
            updatingUi = false
        }
    }

    /**
     * 导出当前表格中的环境配置文本。
     */
    fun exportEnvironmentsText(): String =
        tableModel.toAllRows()
            .filter { it.name.isNotBlank() && it.value.isNotBlank() }
            .joinToString("\n") { "${it.name.trim()}=${it.value.trim().trimEnd('/')}" }

    /**
     * 导出当前表格中勾选激活的环境名称（未勾选任何行时返回空串，代表自动本地端口）。
     */
    fun exportSelectedEnvironmentName(): String =
        tableModel.toAllRows()
            .firstOrNull { it.enabled && it.name.isNotBlank() && it.value.isNotBlank() }
            ?.name
            ?.trim()
            .orEmpty()

    /**
     * 将当前表格中的环境配置与选中环境写入 [ApiHelperSettings] 并通知工具窗口刷新。
     */
    fun persistToSettings() {
        val settings = ApiHelperSettings.getInstance()
        settings.state.environmentsText = exportEnvironmentsText()
        settings.state.selectedEnvironmentName = exportSelectedEnvironmentName()
        project?.let { EndpointToolWindowService.of(it).reloadDebugEnvironments() }
    }

    private fun addDefaultPresets() {
        val existing = tableModel.toAllRows().associateBy { it.name }.toMutableMap()
        val presets = listOf(
            "Local" to "http://localhost:8080",
            "Dev" to "http://127.0.0.1:8080",
            "Test" to "http://test.example.com",
        )
        for ((name, url) in presets) {
            if (name !in existing) {
                existing[name] = DebugParameterRow(enabled = false, name = name, value = url)
            }
        }
        updatingUi = true
        try {
            tableModel.setRows(existing.values.toList())
            updateActiveBadge()
        } finally {
            updatingUi = false
        }
        if (autoPersist) {
            persistToSettings()
        }
    }

    private fun updateActiveBadge() {
        val activeRow = tableModel.toAllRows().firstOrNull { it.enabled && it.name.isNotBlank() && it.value.isNotBlank() }
        if (activeRow != null) {
            activeEnvBadge.text = "${activeRow.name} (${activeRow.value})"
            activeEnvBadge.foreground = ApiHelperUiStyle.activeTabPillForeground()
        } else {
            activeEnvBadge.text = ApiHelperBundle.message("debug.env.auto")
            activeEnvBadge.foreground = UIUtil.getContextHelpForeground()
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
}
