package com.lizhuolun.apihelper.ui.debug

import com.lizhuolun.apihelper.ApiHelperBundle
import javax.swing.table.AbstractTableModel

/**
 * 接口调试参数表格模型。
 *
 * 支持左侧启用复选框列与右侧删除列，始终保留一行空白输入行，
 * 用户填写最后一行后自动追加新行。
 *
 * @property nameColumnKey 参数名列国际化 key
 * @property valueColumnKey 参数值列国际化 key
 * @property showDeleteColumn 是否显示最右侧删除图标列
 * @property showEnabledColumn 是否显示最左侧启用复选框列
 * @author lizhuolun
 * @date 2026/6/16
 */
class DebugParameterTableModel(
    private val nameColumnKey: String = "debug.table.name",
    private val valueColumnKey: String = "debug.table.value",
    private val showDeleteColumn: Boolean = false,
    val showEnabledColumn: Boolean = true,
) : AbstractTableModel() {

    private val rows = mutableListOf(DebugParameterRow())

    private val nameCol: Int get() = if (showEnabledColumn) 1 else 0
    private val valueCol: Int get() = if (showEnabledColumn) 2 else 1

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int {
        var count = 2
        if (showEnabledColumn) count++
        if (showDeleteColumn) count++
        return count
    }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (showEnabledColumn && columnIndex == 0) Boolean::class.javaObjectType else String::class.java

    override fun getColumnName(column: Int): String =
        when (column) {
            nameCol -> ApiHelperBundle.message(nameColumnKey)
            valueCol -> ApiHelperBundle.message(valueColumnKey)
            else -> ""
        }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when {
            showEnabledColumn && columnIndex == 0 -> row.enabled
            columnIndex == nameCol -> row.name
            columnIndex == valueCol -> row.value
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        (showEnabledColumn && columnIndex == 0) || columnIndex == nameCol || columnIndex == valueCol

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows[rowIndex]
        when {
            showEnabledColumn && columnIndex == 0 -> row.enabled = aValue as? Boolean ?: false
            columnIndex == nameCol -> row.name = aValue?.toString().orEmpty()
            columnIndex == valueCol -> row.value = aValue?.toString().orEmpty()
        }
        fireTableCellUpdated(rowIndex, columnIndex)
        ensureTrailingBlankRow()
    }

    /**
     * 全选或取消全选所有行的启用状态。
     *
     * @param enabled 是否启用
     */
    fun setAllEnabled(enabled: Boolean) {
        for (row in rows) {
            row.enabled = enabled
        }
        fireTableDataChanged()
    }

    /**
     * 判断当前是否所有非空行均处于启用状态。
     */
    fun isAllEnabled(): Boolean =
        rows.all { it.enabled }

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
     * 使用指定键值对重置表格。
     *
     * @param pairs 参数键值对
     */
    fun setPairs(pairs: List<Pair<String, String>>) {
        rows.clear()
        rows.addAll(
            pairs
                .filter { it.first.isNotBlank() }
                .distinctBy { it.first }
                .map { DebugParameterRow(name = it.first, value = it.second) },
        )
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
     * 使用包含启用状态的参数行列表重置表格。
     *
     * @param newRows 参数行列表
     */
    fun setRows(newRows: List<DebugParameterRow>) {
        rows.clear()
        rows.addAll(
            newRows
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name }
                .map { it.copy() },
        )
        ensureTrailingBlankRow()
        fireTableDataChanged()
    }

    /**
     * 返回所有参数名非空的参数行（含未勾选的禁用行，用于持久化保存）。
     *
     * @return 参数行列表
     */
    fun toAllRows(): List<DebugParameterRow> =
        rows.filter { it.name.isNotBlank() }.map { it.copy() }

    /**
     * 返回启用且参数名非空的有效参数键值对。
     *
     * @return 参数名非空的键值对列表
     */
    fun toPairs(): List<Pair<String, String>> =
        rows.mapNotNull { row ->
            if (showEnabledColumn && !row.enabled) return@mapNotNull null
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
