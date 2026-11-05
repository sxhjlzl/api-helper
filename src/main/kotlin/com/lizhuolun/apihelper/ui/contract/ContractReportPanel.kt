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
 * - 顶部：左侧标题，右侧「错误/警告计数 + 操作按钮」，计数为 0 时自动隐藏
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
        iconTextGap = JBUI.scale(4)
        isVisible = false
    }

    private val warningLabel = JBLabel().apply {
        font = ApiHelperUiStyle.smallFont(bold = true)
        iconTextGap = JBUI.scale(4)
        isVisible = false
    }

    private val statusLabel = JBLabel().apply {
        font = ApiHelperUiStyle.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private val emptyLabel = JBLabel(ApiHelperBundle.message("contract.empty.healthy")).apply {
        icon = AllIcons.General.InspectionsOK
        iconTextGap = JBUI.scale(8)
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
        errorLabel.isVisible = false
        warningLabel.isVisible = false
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

        // 计数与按钮共用一行，统一 10px 间距，避免多层 FlowLayout 累积出不对称的内边距
        val rightBox = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
            isOpaque = false
            add(errorLabel)
            add(warningLabel)
            add(refreshButton)
            add(copyButton)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ApiHelperUiStyle.borderColor()),
                BorderFactory.createEmptyBorder(0, JBUI.scale(10), 0, 0),
            )
            ApiHelperUiStyle.applyHeaderHeight(this)
            add(titleLabel, BorderLayout.CENTER)
            add(rightBox, BorderLayout.EAST)
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
        updateStat(errorLabel, report.errorCount, AllIcons.General.Error, "contract.badge.errors")
        updateStat(warningLabel, report.warningCount, AllIcons.General.Warning, "contract.badge.warnings")

        statusLabel.text = if (service.isStale()) {
            ApiHelperBundle.message("contract.status.stale", report.endpointCount)
        } else {
            ApiHelperBundle.message("contract.status.done", report.endpointCount)
        }
        showCard(if (report.isHealthy) CARD_EMPTY else CARD_TREE)
    }

    /**
     * 更新单个计数标签：数量为 0 时整块收起，避免出现「0 个错误」这种噪音。
     *
     * @param label 目标标签
     * @param count 问题数量
     * @param icon 与严重级匹配的图标
     * @param messageKey 带 `{0}` 占位符的文案 key
     */
    private fun updateStat(
        label: JBLabel,
        count: Int,
        icon: javax.swing.Icon,
        messageKey: String,
    ) {
        label.isVisible = count > 0
        label.icon = icon
        label.text = ApiHelperBundle.message(messageKey, count)
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

    private companion object {
        const val CARD_TREE = "tree"
        const val CARD_EMPTY = "empty"
        const val NOTIFICATION_GROUP = "ApiHelper"
    }
}