package com.lizhuolun.apihelper.ui.component

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ui.debug.DebugParameterTableModel
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * ApiHelper UI 风格规范，统一小字号紧凑字体、输入框、下拉框、胶囊标签与带复选框网格表格的视觉细节。
 *
 * @author lizhuolun
 * @date 2026/6/16
 */
object ApiHelperUiStyle {

    private const val CONTROL_HEIGHT = 28
    private const val HEADER_HEIGHT = 28
    private const val CORNER_ARC = 6
    const val PRIMARY_FONT_SIZE = 11f
    const val SMALL_FONT_SIZE = 10.5f

    /**
     * 统一文本输入框样式。
     *
     * @param field 文本输入框
     */
    fun applyTextField(field: JBTextField) {
        field.border = fieldBorder()
        field.font = labelFont()
        setControlHeight(field)
    }

    /**
     * 统一下拉框样式。
     *
     * @param combo 下拉框
     * @param width 期望宽度，null 表示保留当前宽度
     * @param bold 是否使用加粗字体
     */
    fun applyComboBox(combo: JComboBox<*>, width: Int? = null, bold: Boolean = false) {
        combo.font = labelFont(bold)
        setControlHeight(combo, width)
    }

    /**
     * 统一次级描边按钮样式。
     *
     * @param button 按钮
     * @param width 期望宽度，null 表示保留当前宽度
     * @param bold 是否使用加粗字体
     */
    fun applyOutlinedButton(button: AbstractButton, width: Int? = null, bold: Boolean = false) {
        button.isFocusPainted = false
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.font = labelFont(bold)
        button.border = BorderFactory.createCompoundBorder(
            RoundedCornerBorder(arc = CORNER_ARC),
            BorderFactory.createEmptyBorder(2, 8, 2, 8),
        )
        setControlHeight(button, width)
    }

    /**
     * 统一紧凑无边框图标操作按钮（带悬停柔和圆角底色）。
     *
     * @param button 按钮
     * @param size 按钮正方形边长（未缩放）
     */
    fun applyIconActionButton(button: AbstractButton, size: Int = 26) {
        button.isFocusPainted = false
        button.isContentAreaFilled = false
        button.isBorderPainted = false
        button.isOpaque = false
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val scaled = JBUI.scale(size)
        val dim = Dimension(scaled, scaled)
        button.preferredSize = dim
        button.minimumSize = dim
        button.maximumSize = dim
        button.border = BorderFactory.createEmptyBorder()
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                button.putClientProperty(KEY_HOVER, true)
                button.repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                button.putClientProperty(KEY_HOVER, false)
                button.repaint()
            }
        })
    }

    /**
     * 绘制图标按钮的悬停圆角背景。
     */
    fun paintIconButtonHover(component: JComponent, g: Graphics) {
        val hovered = component.getClientProperty(KEY_HOVER) == true
        if (!hovered || !component.isEnabled) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = hoverBackgroundColor()
            val arc = JBUI.scale(CORNER_ARC)
            g2.fillRoundRect(1, 1, component.width - 2, component.height - 2, arc, arc)
        } finally {
            g2.dispose()
        }
    }

    /**
     * 统一工具条内轻量胶囊小按钮样式（如格式化、清空、复制）。
     */
    fun applyToolbarPillButton(button: AbstractButton) {
        button.isFocusPainted = false
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.font = smallFont(bold = false)
        button.border = BorderFactory.createCompoundBorder(
            RoundedCornerBorder(arc = CORNER_ARC),
            BorderFactory.createEmptyBorder(1, 7, 1, 7),
        )
        val h = JBUI.scale(22)
        val w = button.preferredSize.width.coerceAtLeast(JBUI.scale(48))
        button.preferredSize = Dimension(w, h)
        button.minimumSize = Dimension(w, h)
    }

    /**
     * 统一配置精致的参数表格样式：支持表头全选复选框、横竖细网格线、舒适留白与占位提示。
     *
     * @param table 目标表格
     * @param nameColumnIndex 参数名所在列索引
     * @param valueColumnIndex 参数值所在列索引
     * @param namePlaceholder 末尾空白行的参数名占位提示
     * @param valuePlaceholder 末尾空白行的参数值占位提示
     */
    fun styleParameterTable(
        table: JTable,
        nameColumnIndex: Int = 0,
        valueColumnIndex: Int = 1,
        namePlaceholder: String = "",
        valuePlaceholder: String = "",
    ) {
        table.fillsViewportHeight = true
        table.rowHeight = JBUI.scale(27)
        table.font = labelFont()
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.tableHeader.reorderingAllowed = false
        table.tableHeader.preferredSize = Dimension(table.tableHeader.preferredSize.width, JBUI.scale(27))
        table.tableHeader.font = smallFont(bold = false)
        table.tableHeader.foreground = UIUtil.getContextHelpForeground()
        table.tableHeader.background = UIUtil.getPanelBackground()
        table.tableHeader.border = BorderFactory.createMatteBorder(0, 0, 1, 0, subtleGridColor())
        table.showHorizontalLines = true
        table.showVerticalLines = true
        table.gridColor = subtleGridColor()
        table.intercellSpacing = Dimension(1, 1)
        table.putClientProperty("terminateEditOnFocusLost", true)

        val defaultHeaderRenderer = table.tableHeader.defaultRenderer
        table.tableHeader.setDefaultRenderer { tbl, value, isSelected, hasFocus, row, column ->
            if (nameColumnIndex == 1 && column == 0) {
                val allSelected = (tbl.model as? DebugParameterTableModel)?.isAllEnabled() ?: true
                JCheckBox().apply {
                    horizontalAlignment = SwingConstants.CENTER
                    this.isSelected = allSelected
                    isOpaque = true
                    background = UIUtil.getPanelBackground()
                    border = BorderFactory.createMatteBorder(0, 0, 0, 1, subtleGridColor())
                }
            } else {
                val comp = defaultHeaderRenderer.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column)
                if (comp is JLabel) {
                    comp.font = smallFont(bold = false)
                    comp.foreground = UIUtil.getLabelForeground()
                    comp.background = UIUtil.getPanelBackground()
                    comp.horizontalAlignment = SwingConstants.LEFT
                    comp.border = BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 1, subtleGridColor()),
                        BorderFactory.createEmptyBorder(0, JBUI.scale(8), 0, JBUI.scale(8)),
                    )
                }
                comp
            }
        }

        if (nameColumnIndex == 1 && table.columnCount > 0) {
            val checkCol = table.columnModel.getColumn(0)
            checkCol.maxWidth = JBUI.scale(34)
            checkCol.preferredWidth = JBUI.scale(34)
            checkCol.minWidth = JBUI.scale(34)
            table.tableHeader.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val col = table.columnAtPoint(e.point)
                    if (col == 0) {
                        val model = table.model as? DebugParameterTableModel ?: return
                        model.setAllEnabled(!model.isAllEnabled())
                        table.tableHeader.repaint()
                    }
                }
            })
        }

        if (nameColumnIndex in 0 until table.columnCount) {
            table.columnModel.getColumn(nameColumnIndex).cellRenderer =
                PaddedPlaceholderCellRenderer(namePlaceholder, isKeyColumn = true)
        }
        if (valueColumnIndex in 0 until table.columnCount) {
            table.columnModel.getColumn(valueColumnIndex).cellRenderer =
                PaddedPlaceholderCellRenderer(valuePlaceholder, isKeyColumn = false)
        }
    }

    /**
     * 统一顶部条带高度。
     *
     * @param component 条带组件
     */
    fun applyHeaderHeight(component: JComponent) {
        setFixedHeight(component, HEADER_HEIGHT)
    }

    /**
     * 返回统一输入框边框。
     *
     * @return 边框
     */
    fun fieldBorder(): Border = RoundedCornerBorder(arc = CORNER_ARC)

    /**
     * 返回统一边框色。
     *
     * @return 边框色
     */
    fun borderColor(): Color =
        UIManager.getColor("Component.borderColor")
            ?: UIManager.getColor("Borders.color")
            ?: UIUtil.getBoundsColor()

    /**
     * 返回分组标题栏的微色差背景色。
     */
    fun sectionHeaderBackground(): Color =
        JBColor(Color(0xF3F5F8), Color(0x232730))

    /**
     * 返回悬停态柔和背景色。
     */
    fun hoverBackgroundColor(): Color =
        UIManager.getColor("ActionButton.hoverBackground")
            ?: JBColor(Color(0xE8ECF2), Color(0x2C313C))

    /**
     * 返回表格柔和网格线颜色。
     */
    fun subtleGridColor(): Color =
        JBColor(Color(0xE2E8F0), Color(0x2D313B))

    /**
     * 返回胶囊 Tab 选中填充色（贴合截图深蓝圆角胶囊风格）。
     */
    fun activeTabPillBackground(): Color =
        JBColor(Color(0xDBEAFE), Color(0x263B5E))

    /**
     * 返回胶囊 Tab 选中描边色。
     */
    fun activeTabPillBorder(): Color =
        JBColor(Color(0x93C5FD), Color(0x3B5E9E))

    /**
     * 返回胶囊 Tab 选中文字色。
     */
    fun activeTabPillForeground(): Color =
        JBColor(Color(0x1D4ED8), Color(0xE2E8F0))

    /**
     * 返回主操作按钮背景色（蓝调）。
     */
    fun primaryActionBackground(): Color =
        JBColor(Color(0x2563EB), Color(0x3574F0))

    /**
     * 返回停止/危险操作按钮背景色（红琥珀调）。
     */
    fun stopActionBackground(): Color =
        JBColor(Color(0xDC2626), Color(0xC93B3B))

    /**
     * 返回统一主字体（11f）。
     *
     * @param bold 是否加粗
     * @return 字体
     */
    fun labelFont(bold: Boolean = false): Font {
        val base = UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 11)
        return base.deriveFont(if (bold) Font.BOLD else Font.PLAIN, PRIMARY_FONT_SIZE)
    }

    /**
     * 返回统一辅助小字体（10.5f）。
     *
     * @param bold 是否加粗
     * @return 字体
     */
    fun smallFont(bold: Boolean = false): Font {
        val base = UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 11)
        return base.deriveFont(if (bold) Font.BOLD else Font.PLAIN, SMALL_FONT_SIZE)
    }

    /**
     * 返回统一等宽代码字体（11f）。
     */
    fun codeFont(): Font = Font(Font.MONOSPACED, Font.PLAIN, 11)

    private fun setControlHeight(component: JComponent, width: Int? = null) {
        setFixedHeight(component, CONTROL_HEIGHT, width)
    }

    private fun setFixedHeight(component: JComponent, height: Int, width: Int? = null) {
        val scaledHeight = JBUI.scale(height)
        val currentWidth = width?.let(JBUI::scale)
            ?: component.preferredSize.width.coerceAtLeast(JBUI.scale(64))
        val size = Dimension(currentWidth, scaledHeight)
        component.preferredSize = size
        component.minimumSize = size
    }

    private const val KEY_HOVER = "ApiHelper.Hover"

    private class PaddedPlaceholderCellRenderer(
        private val placeholder: String,
        private val isKeyColumn: Boolean,
    ) : DefaultTableCellRenderer() {

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            comp.border = BorderFactory.createEmptyBorder(0, JBUI.scale(8), 0, JBUI.scale(8))
            val str = value?.toString().orEmpty()
            val isTrailingBlank = row == table.rowCount - 1 && str.isEmpty()
            if (isTrailingBlank && placeholder.isNotEmpty()) {
                comp.text = placeholder
                comp.foreground = UIUtil.getContextHelpForeground()
                comp.font = labelFont(bold = false)
            } else {
                comp.text = str
                comp.foreground = if (isSelected) table.selectionForeground else table.foreground
                comp.font = labelFont(bold = isKeyColumn && str.isNotEmpty())
            }
            comp.background = if (isSelected) table.selectionBackground else table.background
            return comp
        }
    }
}
