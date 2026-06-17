package com.lizhuolun.apihelper.ui.component

import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.UIManager
import javax.swing.border.Border

/**
 * ApiHelper UI 风格规范，统一输入控件、下拉框、按钮和工具栏条带的尺寸。
 *
 * @author lizhuolun
 * @date 2026/6/16
 */
object ApiHelperUiStyle {

    private const val CONTROL_HEIGHT = 30
    private const val HEADER_HEIGHT = 30
    private const val CORNER_ARC = 6

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
     * 统一次级按钮样式。
     *
     * @param button 按钮
     * @param width 期望宽度，null 表示保留当前宽度
     * @param bold 是否使用加粗字体
     */
    fun applyOutlinedButton(button: AbstractButton, width: Int? = null, bold: Boolean = false) {
        button.isFocusPainted = false
        button.font = labelFont(bold)
        button.border = BorderFactory.createCompoundBorder(
            RoundedCornerBorder(arc = CORNER_ARC),
            BorderFactory.createEmptyBorder(2, 10, 2, 10),
        )
        setControlHeight(button, width)
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
     * 返回统一标签字体。
     *
     * @param bold 是否加粗
     * @return 字体
     */
    fun labelFont(bold: Boolean = false): Font {
        val base = UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 12)
        return base.deriveFont(if (bold) Font.BOLD else Font.PLAIN, 12f)
    }

    private fun setControlHeight(component: JComponent, width: Int? = null) {
        setFixedHeight(component, CONTROL_HEIGHT, width)
    }

    private fun setFixedHeight(component: JComponent, height: Int, width: Int? = null) {
        val scaledHeight = JBUI.scale(height)
        val currentWidth = width?.let(JBUI::scale)
            ?: component.preferredSize.width.coerceAtLeast(JBUI.scale(72))
        val size = Dimension(currentWidth, scaledHeight)
        component.preferredSize = size
        component.minimumSize = size
    }
}
