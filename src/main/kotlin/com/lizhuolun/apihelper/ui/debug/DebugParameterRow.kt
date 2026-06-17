package com.lizhuolun.apihelper.ui.debug

/**
 * 接口调试参数行。
 *
 * @property name 参数名
 * @property value 参数值
 * @author lizhuolun
 * @date 2026/6/16
 */
data class DebugParameterRow(
    var enabled: Boolean = true,
    var name: String = "",
    var value: String = "",
    var type: String = "text",
)
