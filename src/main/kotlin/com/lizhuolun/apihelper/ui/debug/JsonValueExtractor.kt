package com.lizhuolun.apihelper.ui.debug

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/**
 * JSON 值提取器：按点分路径从 JSON 文本中取出字段值，用于把响应字段（如 token）保存为调试变量。
 *
 * 路径语法：`data.token`、`data.list[0].id`，数组下标从 0 开始；
 * 提取结果为对象或数组时返回其紧凑 JSON 文本。
 *
 * @author lizhuolun
 * @date 2026/10/29
 */
object JsonValueExtractor {

    private val SEGMENT = Regex("([^\\[\\]]*)((?:\\[\\d+])*)")
    private val INDEX = Regex("\\[(\\d+)]")

    /**
     * 从 JSON 文本中按路径提取值。
     *
     * @param json 原始 JSON 文本
     * @param path 点分字段路径，数组元素用 `[n]` 表示
     * @return 提取到的字符串值；路径不存在或 JSON 非法时返回 null
     */
    fun extract(json: String, path: String): String? {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || path.isBlank()) return null
        var current = runCatching { JsonParser.parseString(trimmed) }.getOrNull() ?: return null
        for (segment in path.trim().split('.')) {
            if (segment.isEmpty()) return null
            val matcher = SEGMENT.matchEntire(segment) ?: return null
            val name = matcher.groupValues[1]
            if (name.isNotEmpty()) {
                if (!current.isJsonObject) return null
                current = current.asJsonObject.get(name) ?: return null
            }
            for (indexMatch in INDEX.findAll(matcher.groupValues[2])) {
                if (!current.isJsonArray) return null
                val array = current.asJsonArray
                val index = indexMatch.groupValues[1].toIntOrNull() ?: return null
                if (index !in 0 until array.size()) return null
                current = array.get(index)
            }
        }
        return render(current)
    }

    private fun render(element: JsonElement): String? =
        when {
            element.isJsonNull -> null
            element.isJsonPrimitive -> element.asString
            else -> element.toString()
        }
}
