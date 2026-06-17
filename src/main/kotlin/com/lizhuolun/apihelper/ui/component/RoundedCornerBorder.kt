package com.lizhuolun.apihelper.ui.component

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import javax.swing.UIManager
import javax.swing.border.Border

/**
 * 圆角边框，用于搜索框等输入组件。
 *
 * @param strokeColor 边框颜色
 * @param fillColor 填充背景色，null 表示透明
 * @param arc 圆角半径
 * @author lizhuolun
 * @date 2026/6/16
 */
class RoundedCornerBorder(
    private val strokeColor: Color? = null,
    private val fillColor: Color? = null,
    private val arc: Int = 8,
) : Border {

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val scaledArc = JBUI.scale(arc)
        val inset = JBUI.scale(1)
        val x0 = x + inset
        val y0 = y + inset
        val w = width - inset * 2 - 1
        val h = height - inset * 2 - 1

        fillColor?.let {
            g2.color = it
            g2.fillRoundRect(x0, y0, w, h, scaledArc, scaledArc)
        }

        g2.color = strokeColor ?: UIManager.getColor("Component.borderColor") ?: UIUtil.getBoundsColor()
        g2.drawRoundRect(x0, y0, w, h, scaledArc, scaledArc)
    }

    override fun getBorderInsets(c: Component): Insets {
        val top = JBUI.scale(3)
        val left = JBUI.scale(8)
        val bottom = JBUI.scale(3)
        val right = JBUI.scale(8)
        return JBUI.insets(top, left, bottom, right)
    }

    override fun isBorderOpaque(): Boolean = false
}
