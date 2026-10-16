package com.lizhuolun.apihelper.ui

import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.provider.RestIcons
import com.lizhuolun.apihelper.ui.component.HttpMethodBadge
import com.lizhuolun.apihelper.ui.component.RoundedBadgeLabel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.UIManager
import javax.swing.tree.TreeCellRenderer

/**
 * IDE 现代风格的端点树渲染器。
 *
 * - 类/模块分组：左侧文件类型图标 + 弱化包路径 + 加粗类名 + 右侧圆角数量胶囊
 * - 端点叶子：左侧等宽 HTTP 方法彩色胶囊 + 中间 URL + 右侧配对方向图标与方法名
 * - 选中行采用带左右呼吸留白的圆角高亮条，贴合 JetBrains New UI 视觉风格
 *
 * @author lizhuolun
 * @date 2026/6/16
 */
class EndpointTreeCellRenderer : JPanel(BorderLayout()), TreeCellRenderer {

    private val methodBadge = HttpMethodBadge(HttpMethod.GET)
    private val urlLabel = JLabel().apply {
        font = UIManager.getFont("Label.font")?.deriveFont(Font.PLAIN, 11f) ?: font.deriveFont(Font.PLAIN, 11f)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    }
    private val counterpartIconLabel = JLabel().apply {
        border = BorderFactory.createEmptyBorder(0, 0, 0, JBUI.scale(4))
        isVisible = false
    }
    private val methodNameLabel = JLabel().apply {
        font = UIManager.getFont("Label.font")?.deriveFont(Font.PLAIN, 10.5f) ?: font.deriveFont(Font.PLAIN, 10.5f)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        horizontalAlignment = JLabel.RIGHT
    }
    private val endpointRightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
        isOpaque = false
        add(counterpartIconLabel)
        add(methodNameLabel)
    }

    private val typeIconLabel = JLabel()
    private val packageLabel = JLabel().apply {
        font = UIManager.getFont("Label.font")?.deriveFont(Font.PLAIN, 10.5f) ?: font.deriveFont(Font.PLAIN, 10.5f)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    }
    private val classNameLabel = JLabel().apply {
        font = UIManager.getFont("Label.font")?.deriveFont(Font.BOLD, 11f) ?: font.deriveFont(Font.BOLD, 11f)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    }
    private val classPathPanel = JPanel(BorderLayout(0, 0)).apply {
        isOpaque = false
        add(packageLabel, BorderLayout.WEST)
        add(classNameLabel, BorderLayout.CENTER)
    }
    private val groupCountBadge = RoundedBadgeLabel()

    private val endpointPanel = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(JBUI.scale(3), JBUI.scale(8), JBUI.scale(3), JBUI.scale(10))
        add(methodBadge, BorderLayout.WEST)
        add(urlLabel, BorderLayout.CENTER)
        add(endpointRightPanel, BorderLayout.EAST)
    }

    private val groupPanel = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(JBUI.scale(3), JBUI.scale(8), JBUI.scale(3), JBUI.scale(10))
        add(typeIconLabel, BorderLayout.WEST)
        add(classPathPanel, BorderLayout.CENTER)
        add(groupCountBadge, BorderLayout.EAST)
    }
    private var selectedRow = false

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    }

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        val node = value as? EndpointNode ?: return this
        selectedRow = selected
        val bg = backgroundColor(selected, hasFocus)
        background = bg
        endpointPanel.background = bg
        groupPanel.background = bg
        classPathPanel.background = bg

        if (node.item == null) {
            renderGroupNode(node)
            removeAll()
            add(groupPanel, BorderLayout.CENTER)
        } else {
            renderEndpointNode(node)
            removeAll()
            add(endpointPanel, BorderLayout.CENTER)
        }

        updateLabelColors(selected)
        revalidate()
        repaint()
        return this
    }

    private fun renderGroupNode(node: EndpointNode) {
        val title = node.title
        val countMatch = Regex("^(.*) \\((\\d+)\\)$").find(title)
        val className: String
        val count: String
        if (countMatch != null) {
            val (name, c) = countMatch.destructured
            className = name
            count = c
        } else {
            className = title
            count = ""
        }

        if (node.isModuleGroup) {
            packageLabel.text = ""
            classNameLabel.text = className
            groupCountBadge.text = count
            typeIconLabel.icon = AllIcons.Nodes.Module
            toolTipText = className
            return
        }

        val lastDot = className.lastIndexOf('.')
        if (lastDot > 0) {
            packageLabel.text = className.substring(0, lastDot + 1)
            classNameLabel.text = className.substring(lastDot + 1)
        } else {
            packageLabel.text = ""
            classNameLabel.text = className
        }
        groupCountBadge.text = count
        typeIconLabel.icon = typeIconOf(className)
        toolTipText = className
    }

    private fun renderEndpointNode(node: EndpointNode) {
        val item = node.item ?: return
        methodBadge.method = item.httpMethod
        urlLabel.text = item.url
        methodNameLabel.text = item.methodName
        counterpartIconLabel.isVisible = item.hasCounterpart
        if (item.hasCounterpart) {
            counterpartIconLabel.icon = if (item.kind == EndpointKind.CONTROLLER) {
                RestIcons.JUMP_CONTROLLER_TO_FEIGN
            } else {
                RestIcons.JUMP_FEIGN_TO_CONTROLLER
            }
        }
        toolTipText = listOfNotNull(
            "${item.httpMethod.name} ${item.url}",
            item.className,
            item.moduleName.takeIf { it.isNotBlank() }?.let { "module: $it" },
            if (item.hasCounterpart) ApiHelperBundle.message("toolwindow.tooltip.paired") else null,
        ).joinToString("<br>", prefix = "<html>", postfix = "</html>")
    }

    private fun updateLabelColors(selected: Boolean) {
        val fg = if (selected) {
            UIManager.getColor("Tree.selectionForeground") ?: UIUtil.getLabelForeground()
        } else {
            UIManager.getColor("Tree.textForeground") ?: UIUtil.getLabelForeground()
        }
        val muted = if (selected) fg else UIUtil.getContextHelpForeground()
        val packageFg = if (selected) fg else readableMutedForeground()

        urlLabel.foreground = fg
        groupNameForeground = fg
        methodNameLabel.foreground = muted
        packageLabel.foreground = packageFg
        typeIconLabel.foreground = fg

        val countBg = UIManager.getColor("Button.background") ?: UIUtil.getPanelBackground()
        val countFg = if (selected) fg else UIUtil.getContextHelpForeground()
        groupCountBadge.background = countBg
        groupCountBadge.foreground = countFg
    }

    private var groupNameForeground: Color? = null
        set(value) {
            field = value
            classNameLabel.foreground = value
        }

    private fun backgroundColor(selected: Boolean, hasFocus: Boolean): Color {
        return when {
            selected && hasFocus -> UIManager.getColor("Tree.selectionBackground") ?: UIUtil.getPanelBackground()
            selected && !hasFocus -> UIManager.getColor("Tree.selectionInactiveBackground") ?: UIUtil.getPanelBackground()
            else -> UIManager.getColor("Tree.textBackground") ?: UIUtil.getPanelBackground()
        }
    }

    override fun paintComponent(g: Graphics) {
        if (!selectedRow) {
            super.paintComponent(g)
            return
        }
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            val insetX = JBUI.scale(4)
            val insetY = JBUI.scale(1)
            val arc = JBUI.scale(6)
            g2.fillRoundRect(
                insetX,
                insetY,
                (width - insetX * 2).coerceAtLeast(1),
                (height - insetY * 2).coerceAtLeast(1),
                arc,
                arc,
            )
        } finally {
            g2.dispose()
        }
    }

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        return Dimension(size.width.coerceAtLeast(JBUI.scale(120)), size.height.coerceAtLeast(JBUI.scale(24)))
    }

    companion object {

        /**
         * 根据类全限定名返回文件类型图标。
         *
         * @param className 类全限定名
         * @return 图标
         */
        private fun typeIconOf(className: String): Icon {
            val simple = className.substringAfterLast('.')
            return when {
                simple.contains("Feign", ignoreCase = true) ||
                    simple.contains("HttpExchange", ignoreCase = true) ||
                    simple.contains("Client", ignoreCase = true) ||
                    simple.contains("Api", ignoreCase = true) -> AllIcons.Nodes.Interface
                simple.contains("Controller", ignoreCase = true) -> AllIcons.Nodes.Class
                else -> AllIcons.Nodes.Class
            }
        }

        /**
         * 返回弱化但仍可读的辅助文字色。
         *
         * @return 辅助文字色
         */
        private fun readableMutedForeground(): Color {
            val label = UIManager.getColor("Tree.textForeground") ?: UIUtil.getLabelForeground()
            val hint = UIUtil.getContextHelpForeground()
            return mix(label, hint, 0.52f)
        }

        /**
         * 混合两个颜色。
         *
         * @param first 第一个颜色
         * @param second 第二个颜色
         * @param secondWeight 第二个颜色权重
         * @return 混合后的颜色
         */
        private fun mix(first: Color, second: Color, secondWeight: Float): Color {
            val weight = secondWeight.coerceIn(0f, 1f)
            val firstWeight = 1f - weight
            return Color(
                (first.red * firstWeight + second.red * weight).toInt(),
                (first.green * firstWeight + second.green * weight).toInt(),
                (first.blue * firstWeight + second.blue * weight).toInt(),
            )
        }
    }
}
