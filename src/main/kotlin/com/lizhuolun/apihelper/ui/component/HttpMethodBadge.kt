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
 * HTTP 方法徽章。
 *
 * 使用浅底描边区分 HTTP 方法，便于接口列表快速扫描。
 *
 * @param method HTTP 方法
 * @author lizhuolun
 * @date 2026/6/16
 */
class HttpMethodBadge(method: HttpMethod) : JLabel(method.name, SwingConstants.CENTER) {

    var method: HttpMethod = method
        set(value) {
            field = value
            text = value.name
            foreground = colors(value).foreground
            repaint()
        }

    init {
        isOpaque = false
        font = UIManager.getFont("Label.font")?.deriveFont(Font.BOLD, 11f) ?: font.deriveFont(Font.BOLD, 11f)
        foreground = colors(method).foreground
        val size = JBUI.size(METHOD_BADGE_WIDTH, METHOD_BADGE_HEIGHT)
        preferredSize = Dimension(size.width, size.height)
        minimumSize = Dimension(size.width, size.height)
        maximumSize = Dimension(size.width, size.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val arc = JBUI.scale(METHOD_BADGE_ARC)
        val colors = colors(method)
        g2.color = colors.background
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
        g2.color = colors.border
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

        super.paintComponent(g)
    }

    companion object {

        private const val METHOD_BADGE_WIDTH = 40
        private const val METHOD_BADGE_HEIGHT = 18
        private const val METHOD_BADGE_ARC = 8

        /**
         * 返回方法徽章颜色。
         *
         * @param method HTTP 方法
         * @return 徽章颜色
         */
        private fun colors(method: HttpMethod): MethodColors =
            when (method) {
                HttpMethod.GET -> MethodColors(
                    background = JBColor(Color(0xEAF7EE), Color(0x20352A)),
                    foreground = JBColor(Color(0x177245), Color(0x7ED69C)),
                    border = JBColor(Color(0xB7DEC4), Color(0x355C43)),
                )
                HttpMethod.POST -> MethodColors(
                    background = JBColor(Color(0xFFF3DE), Color(0x3A2D1C)),
                    foreground = JBColor(Color(0x966313), Color(0xE0B56B)),
                    border = JBColor(Color(0xE6C999), Color(0x614D2E)),
                )
                HttpMethod.PUT -> MethodColors(
                    background = JBColor(Color(0xEDF2FF), Color(0x222D43)),
                    foreground = JBColor(Color(0x315EAF), Color(0x9DB9F2)),
                    border = JBColor(Color(0xC5D4F6), Color(0x3F5278)),
                )
                HttpMethod.DELETE -> MethodColors(
                    background = JBColor(Color(0xFDEDED), Color(0x3B2424)),
                    foreground = JBColor(Color(0xB33A3A), Color(0xF09A9A)),
                    border = JBColor(Color(0xE8B8B8), Color(0x674141)),
                )
                HttpMethod.PATCH -> MethodColors(
                    background = JBColor(Color(0xF3EEFB), Color(0x332A3E)),
                    foreground = JBColor(Color(0x7552A8), Color(0xC2A4EF)),
                    border = JBColor(Color(0xD6C7EC), Color(0x58466D)),
                )
                HttpMethod.HEAD -> MethodColors(
                    background = JBColor(Color(0xEEF2F3), Color(0x283236)),
                    foreground = JBColor(Color(0x506870), Color(0xA4BDC5)),
                    border = JBColor(Color(0xCBD6DA), Color(0x485C63)),
                )
                HttpMethod.OPTIONS -> MethodColors(
                    background = JBColor(Color(0xE8F5F3), Color(0x203735)),
                    foreground = JBColor(Color(0x287269), Color(0x84D7CC)),
                    border = JBColor(Color(0xB8DDD8), Color(0x3C605C)),
                )
                HttpMethod.ANY -> MethodColors(
                    background = UIManager.getColor("Button.background") ?: UIUtil.getPanelBackground(),
                    foreground = UIUtil.getLabelForeground(),
                    border = UIUtil.getBoundsColor(),
                )
            }

        private data class MethodColors(
            val background: Color,
            val foreground: Color,
            val border: Color,
        )
    }
}
