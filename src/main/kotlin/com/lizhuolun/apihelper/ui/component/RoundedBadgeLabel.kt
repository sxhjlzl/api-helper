package com.lizhuolun.apihelper.ui.component

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.UIManager

/**
 * 通用圆角徽章标签，用于展示数量、状态等辅助信息。
 *
 * 背景与文字颜色默认跟随 IDE 当前主题。
 *
 * @param text 初始文本
 * @param backgroundColor 背景色，null 表示使用 IDE 默认按钮背景
 * @param foregroundColor 文字色，null 表示使用 IDE 默认标签文字色
 * @author lizhuolun
 * @date 2026/6/16
 */
class RoundedBadgeLabel(
    text: String = "",
    private val backgroundColor: Color? = null,
    private val foregroundColor: Color? = null,
) : JLabel(text, SwingConstants.CENTER) {

    init {
        isOpaque = false
        font = UIManager.getFont("Label.font")?.deriveFont(Font.PLAIN, 11f) ?: font.deriveFont(Font.PLAIN, 11f)
        foreground = foregroundColor ?: UIUtil.getLabelForeground()
        border = JBUI.Borders.empty(1, 6)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g2.color = background ?: backgroundColor ?: UIManager.getColor("Button.background") ?: UIUtil.getPanelBackground()
        val arc = JBUI.scale(8)
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)

        super.paintComponent(g)
    }
}
