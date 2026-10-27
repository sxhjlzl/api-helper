package com.lizhuolun.apihelper.ui.contract

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.contract.ContractIssue
import com.lizhuolun.apihelper.core.contract.ContractReport
import com.lizhuolun.apihelper.core.contract.ContractSeverity
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * “契约体检”独立面板（对应工具窗口第 6 个一级标签页）。
 *
 * 采用与请求历史一致的上下结构：
 * - 顶部：标题、统计徽章与操作按钮（重新体检 / 复制报告）
 * - 中部：按规则分组的问题树，双击直达源码
 * - 底部：状态行，展示本次体检的端点数量与过期提示
 *
 * @param project 当前工程
 * @author lizhuolun
 * @date 2026/10/27
 */
class ContractReportPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = ContractScanService.of(project)

    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = IssueRenderer()
        border = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(4), JBUI.scale(2), JBUI.scale(4))
    }

    private val errorLabel = JBLabel().apply {
        font = ApiHelperUiStyle.smallFont(bold = true)
        foreground = JBColorHolder.ERROR
    }

    private val warningLabel = JBLabel().apply {
        font = ApiHelperUiStyle.smallFont(bold = true)
        foreground = JBColorHolder.WARNING
    }

    private val statusLabel = JBLabel().apply {
        font = ApiHelperUiStyle.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private val emptyLabel = JBLabel(ApiHelperBundle.message("contract.empty.healthy")).apply {
        horizontalAlignment = JLabel.CENTER
        foreground = UIUtil.getContextHelpForeground()
        font = ApiHelperUiStyle.labelFont()
    }

    private val refreshButton = JButton(ApiHelperBundle.message("contract.action.refresh")).apply {
        ApiHelperUiStyle.applyOutlinedButton(this)
        addActionListener { refresh() }
    }

    private val copyButton = JButton(ApiHelperBundle.message("contract.action.copy.report")).apply {
        ApiHelperUiStyle.applyOutlinedButton(this)
        addActionListener { copyReport() }
    }

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()

        cardPanel.isOpaque = false
        cardPanel.add(JBScrollPane(tree), CARD_TREE)
        cardPanel.add(buildEmptyPanel(), CARD_EMPTY)

        add(buildHeader(), BorderLayout.NORTH)
        add(cardPanel, BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)

        installInteractions()
        showCard(CARD_EMPTY)
        statusLabel.text = ApiHelperBundle.message("contract.status.idle")
    }

    /**
     * 触发一次体检并刷新界面。
     */
    fun refresh() {
        statusLabel.text = ApiHelperBundle.message("contract.status.checking")
        service.refresh { report -> render(report) }
    }

    /**
     * 判断报告是否需要用户重新体检。
     */
    fun isStale(): Boolean = service.isStale()

    private fun buildHeader(): JPanel {
        val titleLabel = JBLabel(ApiHelperBundle.message("contract.title")).apply {
            font = ApiHelperUiStyle.labelFont(bold = true)
        }

        val statsBox = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0)).apply {
            isOpaque = false
            add(errorLabel)
            add(warningLabel)
        }

        val leftBox = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.WEST)
            add(statsBox, BorderLayout.CENTER)
        }

        val actionsBox = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(refreshButton)
            add(copyButton)
        }

        return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(0, JBUI.scale(8), 0, JBUI.scale(8)),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(leftBox, BorderLayout.CENTER)
            add(actionsBox, BorderLayout.EAST)
        }
    }

    private fun buildEmptyPanel(): JPanel =
        JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(emptyLabel)
        }

    private fun buildStatusBar(): JPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(8), JBUI.scale(4), JBUI.scale(8)),
            )
            add(statusLabel, BorderLayout.WEST)
        }

    private fun installInteractions() {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    navigateSelectedIssue()
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) showPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showPopup(e)
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) navigateSelectedIssue()
            }
        })
    }

    private fun showPopup(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        tree.selectionPath = path
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val issue = (node.userObject as? IssueNode)?.issue ?: return

        val popup = javax.swing.JPopupMenu()
        popup.add(javax.swing.JMenuItem(ApiHelperBundle.message("contract.action.navigate.source")).apply {
            addActionListener { navigateTo(issue) }
        })
        popup.show(tree, e.x, e.y)
    }

    private fun navigateSelectedIssue() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val issue = (node.userObject as? IssueNode)?.issue ?: return
        navigateTo(issue)
    }

    private fun navigateTo(issue: ContractIssue) {
        val mapping = issue.client ?: issue.controllers.firstOrNull() ?: return
        navigateToMapping(mapping)
    }

    private fun navigateToMapping(mapping: HttpMappingInfo) {
        ApplicationManager.getApplication().runReadAction(Computable {
            val method = mapping.resolveMethod() ?: return@Computable null
            val file = method.containingFile?.virtualFile ?: return@Computable null
            OpenFileDescriptor(project, file, method.textOffset)
        })?.navigate(true)
    }

    private fun render(report: ContractReport) {
        rebuildTree(report)
        errorLabel.text = ApiHelperBundle.message("contract.badge.errors", report.errorCount)
        warningLabel.text = ApiHelperBundle.message("contract.badge.warnings", report.warningCount)

        statusLabel.text = if (service.isStale()) {
            ApiHelperBundle.message("contract.status.stale", report.endpointCount)
        } else {
            ApiHelperBundle.message("contract.status.done", report.endpointCount)
        }
        showCard(if (report.isHealthy) CARD_EMPTY else CARD_TREE)
    }

    private fun rebuildTree(report: ContractReport) {
        rootNode.removeAllChildren()
        for ((ruleId, issues) in report.issuesByRule()) {
            val groupNode = DefaultMutableTreeNode(RuleGroupNode(ruleId, issues))
            issues.forEach { issue -> groupNode.add(DefaultMutableTreeNode(IssueNode(issue))) }
            rootNode.add(groupNode)
        }
        treeModel.reload()
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    private fun copyReport() {
        val report = service.report() ?: return
        val text = ContractMarkdownExporter.export(report)
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                ApiHelperBundle.message("contract.notification.copied"),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private fun showCard(card: String) {
        cardLayout.show(cardPanel, card)
    }

    /**
     * 树中的规则分组节点。
     */
    private class RuleGroupNode(
        val ruleId: String,
        val issues: List<ContractIssue>,
    ) {
        /**
         * 分组内最高严重级，用于选图标。
         */
        val severity: ContractSeverity
            get() = issues.minByOrNull { it.severity.ordinal }?.severity ?: ContractSeverity.INFO
    }

    /**
     * 树中的具体问题节点。
     */
    private class IssueNode(val issue: ContractIssue)

    /**
     * 问题树渲染器：分组加粗，叶子展示完整文案。
     */
    private class IssueRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            when (val payload = node.userObject) {
                is RuleGroupNode -> {
                    icon = iconFor(payload.severity)
                    append(
                        ApiHelperBundle.message("contract.rule.${payload.ruleId}"),
                        SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES,
                    )
                    append("  ${payload.issues.size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is IssueNode -> {
                    icon = iconFor(payload.issue.severity)
                    append(payload.issue.message, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }

                else -> append(value?.toString().orEmpty(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }

        private fun iconFor(severity: ContractSeverity) = when (severity) {
            ContractSeverity.ERROR -> AllIcons.General.Error
            ContractSeverity.WARNING -> AllIcons.General.Warning
            ContractSeverity.INFO -> AllIcons.General.Information
        }
    }

    /**
     * 严重级对应的主题色，避免在渲染器与标签间重复定义。
     */
    private object JBColorHolder {
        val ERROR: java.awt.Color = com.intellij.ui.JBColor(
            java.awt.Color(0xC0, 0x39, 0x2B),
            java.awt.Color(0xF7, 0x6E, 0x6E),
        )
        val WARNING: java.awt.Color = com.intellij.ui.JBColor(
            java.awt.Color(0xB8, 0x7A, 0x00),
            java.awt.Color(0xE0, 0xA5, 0x4A),
        )
    }

    private companion object {
        const val CARD_TREE = "tree"
        const val CARD_EMPTY = "empty"
        const val NOTIFICATION_GROUP = "ApiHelper"
    }
}