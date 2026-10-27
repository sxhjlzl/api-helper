package com.lizhuolun.apihelper.core.contract

/**
 * 路径模板：把 URL 拆成段列表与路径变量名列表。
 *
 * 与 [com.lizhuolun.apihelper.core.annotation.PathBuilder.normalizeForMatch] 的区别：
 * 归一化后的 matchUrl 会把 `{id}` 与 `{userId}` 都抹成 `{}`，用于两端配对；
 * 而契约体检需要**保留原始变量名**，才能判断「URL 里声明了 {orderId}，但方法参数里没有对应的 @PathVariable」。
 *
 * @property segments 去掉空段后的路径段，路径变量段统一表示为 `{}`，例如 `["user", "{}", "detail"]`
 * @property variableNames 按出现顺序保留原始路径变量名，例如 `["orderId"]`
 * @author lizhuolun
 * @date 2026/10/27
 */
data class PathTemplate(
    val segments: List<String>,
    val variableNames: List<String>,
) {

    /**
     * 是否包含路径变量。
     */
    val hasVariables: Boolean
        get() = variableNames.isNotEmpty()

    companion object {

        /**
         * 匹配 `{name}` 形式的路径变量，允许空名（归一化后的 `{}`）。
         */
        private val VARIABLE_REGEX = Regex("\\{([^}/]*)}")

        /**
         * 解析任意 URL 片段为路径模板。
         *
         * 传入的可以是带 context-path 的完整路径，也可以是相对路径；
         * 变量名解析与段切分都不依赖前缀，因此调用方无需关心。
         *
         * @param url 原始 URL，例如 `/api/user/{orderId}/detail`
         * @return 解析后的路径模板，空字符串或根路径返回空段列表
         */
        fun parse(url: String): PathTemplate {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return EMPTY
            val withLeadingSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
            val variableNames = VARIABLE_REGEX.findAll(withLeadingSlash)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .toList()
            val segments = withLeadingSlash.split('/')
                .filter { it.isNotEmpty() }
                .map { segment ->
                    if (segment.startsWith("{") && segment.endsWith("}") && VARIABLE_REGEX.matches(segment)) {
                        "{}"
                    } else {
                        segment
                    }
                }
            return PathTemplate(segments = segments, variableNames = variableNames)
        }

        /**
         * 空模板，表示无段无变量的路径。
         */
        val EMPTY = PathTemplate(segments = emptyList(), variableNames = emptyList())
    }
}