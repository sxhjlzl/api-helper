package com.lizhuolun.apihelper.ui.debug

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.settings.ApiHelperSettings
import com.lizhuolun.apihelper.ui.EndpointToolWindowService
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JToolBar
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellRenderer

/**
 * “前置 URL”独立管理面板（对应顶部 Tab：前置 URL）。
 *
 * 支持通过可视化表格直接增删改多环境名称与 Base URL，并可切换当前激活环境，
 * 修改后实时同步到 [ApiHelperSettings] 与调试面板。
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/10/16
 */
class EnvironmentManagePanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tableModel = DebugParameterTableModel(
        nameColumnKey = "toolwindow.env.column.name",
        valueColumnKey = "toolwindow.env.column.url",
        showDeleteColumn = true,
    )

    private val activeEnvBox = ComboBox<String>().apply {
        ApiHelperUiStyle.applyComboBox(this, width = 160, bold = false)
        addActionListener {
            if (!updatingUi) {
                val selected = selectedItem as? String ?: ""
                val envName = if (selected == autoLabel()) "" else selected
                ApiHelperSettings.getInstance().state.selectedEnvironmentName = envName
                EndpointToolWindowService.of(project).reloadDebugEnvironments()
            }
        }
    }

    private var updatingUi = false

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        border = BorderFactory.createEmptyBorder()

        val topBar = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(6, 10, 6, 10),
            )
            val left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                isOpaque = false
                add(JBLabel(ApiHelperBundle.message("toolwindow.env.active.label")))
                add(activeEnvBox)
            }
            val right = JToolBar().apply {
                isFloatable = false
                isOpaque = false
                border = BorderFactory.createEmptyBorder()
                add(JButton(ApiHelperBundle.message("debug.action.clear")).apply {
                    addActionListener { tableModel.clear() }
                })
            }
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }

        val table = JTable(tableModel).apply {
            fillsViewportHeight = true
            rowHeight = JBUI.scale(30)
            font = font?.deriveFont(12f)
            tableHeader.font = tableHeader.font?.deriveFont(Font.PLAIN, 12f)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableHeader.reorderingAllowed = false
            putClientProperty("terminateEditOnFocusLost", true)
            val deleteCol = columnModel.getColumn(columnCount - 1)
            deleteCol.maxWidth = JBUI.scale(48)
            deleteCol.preferredWidth = JBUI.scale(48)
            deleteCol.cellRenderer = DeleteCellRenderer()
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val r = rowAtPoint(e.point)
                    val c = columnAtPoint(e.point)
                    if (r >= 0 && c == columnCount - 1) {
                        tableModel.removeRow(r)
                    }
                }
            })
        }

        tableModel.addTableModelListener {
            if (!updatingUi) {
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
     * 从全局设置加载环境配置到表格与下拉框。
     */
    fun loadFromSettings() {
        updatingUi = true
        try {
            val settings = ApiHelperSettings.getInstance()
            val envs = settings.parseEnvironments()
            tableModel.setPairs(envs.map { it.name to it.baseUrl })
            refreshActiveEnvBox(envs.map { it.name }, settings.state.selectedEnvironmentName)
        } finally {
            updatingUi = false
        }
    }

    private fun persistToSettings() {
        val settings = ApiHelperSettings.getInstance()
        val pairs = tableModel.toPairs().filter { it.first.isNotBlank() && it.second.isNotBlank() }
        settings.state.environmentsText = pairs.joinToString("\n") { (name, url) -> "${name.trim()}=${url.trim()}" }
        updatingUi = true
        try {
            refreshActiveEnvBox(pairs.map { it.first }, settings.state.selectedEnvironmentName)
        } finally {
            updatingUi = false
        }
        EndpointToolWindowService.of(project).reloadDebugEnvironments()
    }

    private fun refreshActiveEnvBox(names: List<String>, selectedName: String) {
        activeEnvBox.removeAllItems()
        val auto = autoLabel()
        activeEnvBox.addItem(auto)
        var target = auto
        for (name in names) {
            activeEnvBox.addItem(name)
            if (name == selectedName) {
                target = name
            }
        }
        activeEnvBox.selectedItem = target
    }

    private fun autoLabel(): String = ApiHelperBundle.message("debug.env.auto")

    private class DeleteCellRenderer : JLabel(AllIcons.Actions.GC, CENTER), TableCellRenderer {
        init {
            horizontalAlignment = CENTER
            verticalAlignment = CENTER
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
