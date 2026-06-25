package com.lizhuolun.apihelper.ui

/**
 * 端点树搜索条件。
 *
 * 输入会按空白拆成多个关键词，每个关键词都需要命中端点的 URL、HTTP 方法、
 * 方法名、类名或模块名之一。这样可以支持 "GET user"、"/api order" 等组合过滤。
 *
 * @property tokens 归一化后的关键词列表
 */
internal class EndpointSearchQuery private constructor(
    private val tokens: List<String>,
) {

    /**
     * 查询是否为空。
     */
    val isBlank: Boolean = tokens.isEmpty()

    /**
     * 判断端点展示项是否满足当前查询。
     *
     * @param item 端点展示项
     * @return 所有关键词都命中时返回 true
     */
    fun matches(item: EndpointTreeItem): Boolean {
        if (isBlank) return true
        val searchable = listOf(
            item.url,
            item.httpMethod.name,
            item.methodName,
            item.className,
            item.moduleName,
        ).map { it.lowercase() }
        return tokens.all { token ->
            searchable.any { it.contains(token) }
        }
    }

    companion object {

        /**
         * 从输入文本构建搜索条件。
         *
         * @param text 用户输入
         * @return 搜索条件
         */
        fun parse(text: String): EndpointSearchQuery {
            val tokens = text
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
            return EndpointSearchQuery(tokens)
        }
    }
}
