package com.lizhuolun.apihelper.ui.debug

import com.lizhuolun.apihelper.ApiHelperBundle
import javax.swing.table.AbstractTableModel

/**
 * Body form-data 参数表格模型。
 *
 * 始终保留一行空白输入行，用户填写最后一行后自动追加新行。
 *
 * @author lizhuolun
 * @date 2026/6/16
 */
class BodyFormDataTableModel : AbstractTableModel() {

    private val rows = mutableListOf(DebugParameterRow())

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 5

    override fun getColumnName(column: Int): String = when (column) {
        0 -> ""
        1 -> ApiHelperBundle.message("debug.table.name")
        2 -> ApiHelperBundle.message("debug.table.value")
        3 -> ApiHelperBundle.message("debug.table.type")
        else -> ""
    }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 0) Boolean::class.javaObjectType else String::class.java

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.enabled
            1 -> row.name
            2 -> row.value
            3 -> row.type
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex != 4

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows[rowIndex]
        when (columnIndex) {
            0 -> row.enabled = aValue as? Boolean ?: false
            1 -> row.name = aValue?.toString().orEmpty()
            2 -> row.value = aValue?.toString().orEmpty()
            3 -> row.type = aValue?.toString().orEmpty().ifBlank { TYPE_TEXT }
        }
        fireTableCellUpdated(rowIndex, columnIndex)
        ensureTrailingBlankRow()
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
     * 获取指定行数据。
     *
     * @param rowIndex 行号
     * @return 参数行
     */
    fun rowAt(rowIndex: Int): DebugParameterRow? =
        rows.getOrNull(rowIndex)

    /**
     * 更新指定行的参数值。
     *
     * @param rowIndex 行号
     * @param value 参数值
     */
    fun updateValue(rowIndex: Int, value: String) {
        val row = rows.getOrNull(rowIndex) ?: return
        row.value = value
        fireTableCellUpdated(rowIndex, 2)
        ensureTrailingBlankRow()
    }

    /**
     * 返回启用且参数名非空的参数行。
     *
     * @return form-data 参数列表
     */
    fun toRows(): List<DebugParameterRow> =
        rows.filter { it.enabled && it.name.isNotBlank() }

    private fun ensureTrailingBlankRow() {
        if (rows.isEmpty() || rows.last().name.isNotBlank() || rows.last().value.isNotBlank()) {
            rows.add(DebugParameterRow())
            fireTableRowsInserted(rows.lastIndex, rows.lastIndex)
        }
    }

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_FILE = "file"
    }
}
