package com.lizhuolun.apihelper.ui.debug

import com.intellij.openapi.project.Project
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lizhuolun.apihelper.ApiHelperBundle
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.ui.component.ApiHelperUiStyle
import com.lizhuolun.apihelper.ui.component.HttpMethodBadge
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

/**
 * “请求历史”独立管理面板（对应顶部 Tab：请求历史）。
 *
 * 集中展示当前会话发送过的全部调试请求记录，支持双击或按回车一键回放到调试面板，以及一键清空历史。
 *
 * @param project 当前工程
 * @param onReplaySelected 选中一条历史记录进行回放时的回调
 * @author lizhuolun
 * @date 2026/10/16
 */
class RequestHistoryPanel(
    private val project: Project,
    private val onReplaySelected: (DebugHistoryEntry) -> Unit,
) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<DebugHistoryEntry>()
    private val historyList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = HistoryListCellRenderer()
        emptyText.text = ApiHelperBundle.message("debug.history.empty")
    }

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
            add(JBLabel(ApiHelperBundle.message("debug.history.title")).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = font?.deriveFont(Font.PLAIN, 12f)
            }, BorderLayout.WEST)
            val toolbar = JToolBar().apply {
                isFloatable = false
                isOpaque = false
                border = BorderFactory.createEmptyBorder()
                add(JButton(ApiHelperBundle.message("debug.action.clear")).apply {
                    addActionListener {
                        DebugDraftService.of(project).clearHistory()
                        refreshHistory()
                    }
                })
            }
            add(toolbar, BorderLayout.EAST)
        }

        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val selected = historyList.selectedValue ?: return false
                onReplaySelected(selected)
                return true
            }
        }.installOn(historyList)

        historyList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    val selected = historyList.selectedValue ?: return
                    onReplaySelected(selected)
                    e.consume()
                }
            }
        })

        add(topBar, BorderLayout.NORTH)
        add(JBScrollPane(historyList).apply {
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.CENTER)
    }

    /**
     * 从 [DebugDraftService] 刷新历史记录列表。
     */
    fun refreshHistory() {
        listModel.clear()
        DebugDraftService.of(project).getHistory().forEach(listModel::addElement)
    }

    private class HistoryListCellRenderer : JPanel(BorderLayout(JBUI.scale(8), 0)), ListCellRenderer<DebugHistoryEntry> {

        private val methodBadge = HttpMethodBadge(HttpMethod.GET)
        private val urlLabel = JLabel().apply {
            font = font?.deriveFont(Font.PLAIN, 12f)
        }
        private val statusLabel = JLabel().apply {
            font = font?.deriveFont(Font.BOLD, 11f)
        }
        private val timeLabel = JLabel().apply {
            font = font?.deriveFont(Font.PLAIN, 11f)
        }
        private val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(statusLabel)
            add(timeLabel)
        }

        init {
            border = BorderFactory.createEmptyBorder(JBUI.scale(6), JBUI.scale(10), JBUI.scale(6), JBUI.scale(10))
            add(methodBadge, BorderLayout.WEST)
            add(urlLabel, BorderLayout.CENTER)
            add(rightPanel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out DebugHistoryEntry>,
            value: DebugHistoryEntry?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            if (value == null) return this
            isOpaque = true
            background = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else UIUtil.getLabelForeground()
            val muted = if (isSelected) list.selectionForeground else UIUtil.getContextHelpForeground()

            methodBadge.method = HttpMethod.fromName(value.draft.method)
            urlLabel.text = value.draft.url
            urlLabel.foreground = fg

            val code = value.statusCode
            statusLabel.text = if (code != null) "$code (${value.elapsedMs}ms)" else "ERR (${value.elapsedMs}ms)"
            statusLabel.foreground = if (isSelected) {
                list.selectionForeground
            } else {
                when (code) {
                    in 200..299 -> JBColor(Color(0x177245), Color(0x7ED69C))
                    in 300..399 -> JBColor(Color(0x966313), Color(0xE0B56B))
                    else -> UIUtil.getErrorForeground()
                }
            }
            timeLabel.text = value.timestampText
            timeLabel.foreground = muted
            return this
        }
    }
}
