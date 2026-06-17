package com.lizhuolun.apihelper.ui.debug

import com.lizhuolun.apihelper.ApiHelperBundle
import javax.swing.table.AbstractTableModel

/**
 * 接口调试参数表格模型。
 *
 * 始终保留一行空白输入行，用户填写最后一行后自动追加新行。
 *
 * @property nameColumnKey 参数名列国际化 key
 * @property valueColumnKey 参数值列国际化 key
 * @author lizhuolun
 * @date 2026/6/16
 */
class DebugParameterTableModel(
    private val nameColumnKey: String = "debug.table.name",
    private val valueColumnKey: String = "debug.table.value",
    private val showDeleteColumn: Boolean = false,
) : AbstractTableModel() {

    private val rows = mutableListOf(DebugParameterRow())

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = if (showDeleteColumn) 3 else 2

    override fun getColumnName(column: Int): String =
        when (column) {
            0 -> ApiHelperBundle.message(nameColumnKey)
            1 -> ApiHelperBundle.message(valueColumnKey)
            else -> ""
        }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.name
            1 -> row.value
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex < 2

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val value = aValue?.toString().orEmpty()
        val row = rows[rowIndex]
        if (columnIndex == 0) {
            row.name = value
        } else {
            row.value = value
        }
        fireTableCellUpdated(rowIndex, columnIndex)
        ensureTrailingBlankRow()
    }

    /**
     * 使用指定参数名重置表格，主要用于从 URL 路径变量预填 Path 参数。
     *
     * @param names 参数名列表
     */
    fun setNames(names: List<String>) {
        rows.clear()
        rows.addAll(names.distinct().map { DebugParameterRow(name = it) })
        ensureTrailingBlankRow()
        fireTableDataChanged()
    }

    /**
     * 清空表格内容。
     */
    fun clear() {
        rows.clear()
        rows.add(DebugParameterRow())
        fireTableDataChanged()
    }

    /**
     * 删除指定行。
     *
     * @param rowIndex 行号
     */
    fun removeRow(rowIndex: Int) {
        if (rowIndex !in rows.indices) return
        rows.removeAt(rowIndex)
        ensureTrailingBlankRow()
        fireTableDataChanged()
    }

    /**
     * 返回有效参数键值对。
     *
     * @return 参数名非空的键值对列表
     */
    fun toPairs(): List<Pair<String, String>> =
        rows.mapNotNull { row ->
            val name = row.name.trim()
            if (name.isEmpty()) null else name to row.value
        }

    private fun ensureTrailingBlankRow() {
        if (rows.none { it.name.isBlank() && it.value.isBlank() } ||
            rows.lastOrNull()?.let { it.name.isNotBlank() || it.value.isNotBlank() } == true
        ) {
            rows.add(DebugParameterRow())
            fireTableRowsInserted(rows.lastIndex, rows.lastIndex)
        }
    }
}
