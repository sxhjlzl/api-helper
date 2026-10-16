package com.lizhuolun.apihelper.ui.component

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.core.HttpMethod
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.UIManager

/**
 * HTTP 方法胶囊徽章。
 *
 * 使用柔和底色与描边区分不同 HTTP 方法，统一固定宽度以保证接口列表中所有 URL 严格垂直对齐，
 * 同时兼容 DELETE / OPTIONS 等较长方法名不截断。
 *
 * @param method HTTP 方法
 * @author lizhuolun
 * @date 2026/6/16
 */
class HttpMethodBadge(method: HttpMethod) : JLabel(displayLabel(method), SwingConstants.CENTER) {

    var method: HttpMethod = method
        set(value) {
            field = value
            text = displayLabel(value)
            foreground = colors(value).foreground
            repaint()
        }

    init {
        isOpaque = false
        font = (UIManager.getFont("Label.font") ?: font).deriveFont(Font.BOLD, 10f)
        foreground = colors(method).foreground
        val size = JBUI.size(METHOD_BADGE_WIDTH, METHOD_BADGE_HEIGHT)
        preferredSize = Dimension(size.width, size.height)
        minimumSize = Dimension(size.width, size.height)
        maximumSize = Dimension(size.width, size.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(METHOD_BADGE_ARC)
            val colors = colors(method)
            g2.color = colors.background
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
            g2.color = colors.border
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    companion object {

        private const val METHOD_BADGE_WIDTH = 44
        private const val METHOD_BADGE_HEIGHT = 16
        private const val METHOD_BADGE_ARC = 5

        private fun displayLabel(method: HttpMethod): String =
            when (method) {
                HttpMethod.OPTIONS -> "OPT"
                else -> method.name
            }

        /**
         * 返回方法徽章颜色。
         *
         * @param method HTTP 方法
         * @return 徽章颜色
         */
        fun colors(method: HttpMethod): MethodColors =
            when (method) {
                HttpMethod.GET -> MethodColors(
                    background = JBColor(Color(0xE8F8EE), Color(0x1D3328)),
                    foreground = JBColor(Color(0x157347), Color(0x6EE7B7)),
                    border = JBColor(Color(0xB6E2C6), Color(0x2E5440)),
                )
                HttpMethod.POST -> MethodColors(
                    background = JBColor(Color(0xFFF4E0), Color(0x382B19)),
                    foreground = JBColor(Color(0x9A6700), Color(0xFCD34D)),
                    border = JBColor(Color(0xEBD0A4), Color(0x5C4628)),
                )
                HttpMethod.PUT -> MethodColors(
                    background = JBColor(Color(0xEBF2FF), Color(0x1E2B42)),
                    foreground = JBColor(Color(0x2563EB), Color(0x93C5FD)),
                    border = JBColor(Color(0xC2D6FA), Color(0x374D75)),
                )
                HttpMethod.DELETE -> MethodColors(
                    background = JBColor(Color(0xFEECEC), Color(0x3A2123)),
                    foreground = JBColor(Color(0xC92A2A), Color(0xFCA5A5)),
                    border = JBColor(Color(0xF1C0C0), Color(0x63373A)),
                )
                HttpMethod.PATCH -> MethodColors(
                    background = JBColor(Color(0xF3EDFC), Color(0x30263D)),
                    foreground = JBColor(Color(0x6D41A1), Color(0xC4B5FD)),
                    border = JBColor(Color(0xD8C9F2), Color(0x524069)),
                )
                HttpMethod.HEAD -> MethodColors(
                    background = JBColor(Color(0xEEF2F5), Color(0x263036)),
                    foreground = JBColor(Color(0x475569), Color(0x94A3B8)),
                    border = JBColor(Color(0xCBD5E1), Color(0x42535E)),
                )
                HttpMethod.OPTIONS -> MethodColors(
                    background = JBColor(Color(0xE6F6F4), Color(0x1E3533)),
                    foreground = JBColor(Color(0x0D9488), Color(0x5EEAD4)),
                    border = JBColor(Color(0xB3E2DC), Color(0x355C58)),
                )
                HttpMethod.ANY -> MethodColors(
                    background = UIManager.getColor("Button.background") ?: UIUtil.getPanelBackground(),
                    foreground = UIUtil.getLabelForeground(),
                    border = UIUtil.getBoundsColor(),
                )
            }

        data class MethodColors(
            val background: Color,
            val foreground: Color,
            val border: Color,
        )
    }
}
