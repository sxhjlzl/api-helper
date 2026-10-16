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
import java.awt.Component
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
 * “全局参数”独立管理面板（对应顶部 Tab：全局参数）。
 *
 * 提供可视化表格管理全局公共 Header（如 Authorization、X-Tenant-Id），
 * 修改后实时写回 [ApiHelperSettings]，发送调试请求与生成 cURL 时自动合并生效。
 *
 * @author lizhuolun
 * @date 2026/10/16
 */
class GlobalParamsPanel : JPanel(BorderLayout()) {

    private val headerTableModel = DebugParameterTableModel(
        nameColumnKey = "debug.table.name",
        valueColumnKey = "debug.table.value",
        showDeleteColumn = true,
    )

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
            add(JBLabel(ApiHelperBundle.message("toolwindow.global.headers.hint")).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = font?.deriveFont(Font.PLAIN, 12f)
            }, BorderLayout.WEST)
            val toolbar = JToolBar().apply {
                isFloatable = false
                isOpaque = false
                border = BorderFactory.createEmptyBorder()
                add(JButton(ApiHelperBundle.message("debug.action.clear")).apply {
                    addActionListener { headerTableModel.clear() }
                })
            }
            add(toolbar, BorderLayout.EAST)
        }

        val table = JTable(headerTableModel).apply {
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
                        headerTableModel.removeRow(r)
                    }
                }
            })
        }

        headerTableModel.addTableModelListener {
            if (!updatingUi) {
                val pairs = headerTableModel.toPairs().filter { it.first.isNotBlank() }
                ApiHelperSettings.getInstance().state.globalHeadersText =
                    pairs.joinToString("\n") { (k, v) -> "${k.trim()}: $v" }
            }
        }

        add(topBar, BorderLayout.NORTH)
        add(JBScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)

        loadFromSettings()
    }

    /**
     * 从全局设置同步公共 Header 到表格。
     */
    fun loadFromSettings() {
        updatingUi = true
        try {
            val pairs = ApiHelperSettings.getInstance().parseGlobalHeaders()
            headerTableModel.setPairs(pairs)
        } finally {
            updatingUi = false
        }
    }

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
