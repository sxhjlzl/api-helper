package com.lizhuolun.apihelper.ui.debug

/**
 * 调试变量解析器：把文本中的 `{{var}}` 占位符替换为已定义变量的值。
 *
 * 设计要点：
 * 1. 占位符语法为 `{{name}}`，与 URL 路径变量的 `{name}` 单花括号写法天然隔离，互不影响；
 * 2. 未定义的变量保留原文并记入 unresolved，由调用方决定提示方式，绝不静默替换为空串；
 * 3. 变量值中允许再嵌套占位符（例如 token=Bearer {{rawToken}}），最多展开 3 层，防止循环引用。
 *
 * @author lizhuolun
 * @date 2026/10/29
 */
object VariableResolver {

    private val PLACEHOLDER = Regex("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_-]*)\\s*}}")

    /**
     * 变量名最大嵌套展开层数，超出后视为循环引用并保留原文。
     */
    private const val MAX_DEPTH = 3

    /**
     * 一次解析的结果。
     *
     * @property text 替换后的文本
     * @property unresolved 未能解析的变量名列表（去重、按出现顺序）
     */
    data class ResolveResult(
        val text: String,
        val unresolved: List<String>,
    ) {
        /**
         * 是否所有占位符都成功解析。
         */
        val fullyResolved: Boolean get() = unresolved.isEmpty()
    }

    /**
     * 解析文本中的全部 `{{var}}` 占位符。
     *
     * @param text 原始文本
     * @param variables 已生效的变量表（环境变量覆盖全局变量后的结果）
     * @return 解析结果，含未解析变量名列表
     */
    fun resolve(text: String, variables: Map<String, String>): ResolveResult {
        if (text.isEmpty() || !text.contains("{{")) {
            return ResolveResult(text, emptyList())
        }
        val unresolved = LinkedHashSet<String>()
        val expandedVariables = expandVariables(variables, unresolved)
        val resolved = PLACEHOLDER.replace(text) { match ->
            val name = match.groupValues[1]
            val value = expandedVariables[name]
            if (value == null) {
                unresolved += name
                match.value
            } else {
                value
            }
        }
        return ResolveResult(resolved, unresolved.toList())
    }

    /**
     * 批量解析键值对的值部分，键不做替换。
     *
     * @param pairs 原始键值对
     * @param variables 已生效的变量表
     * @return 替换后的键值对与未解析变量名列表
     */
    fun resolvePairs(
        pairs: List<Pair<String, String>>,
        variables: Map<String, String>,
    ): Pair<List<Pair<String, String>>, List<String>> {
        if (pairs.isEmpty()) return pairs to emptyList()
        val unresolved = LinkedHashSet<String>()
        val resolved = pairs.map { (name, value) ->
            val result = resolve(value, variables)
            unresolved += result.unresolved
            name to result.text
        }
        return resolved to unresolved.toList()
    }

    /**
     * 收集文本中出现过的全部变量名（不论是否已定义），用于界面提示。
     *
     * @param text 原始文本
     * @return 变量名列表（去重、按出现顺序）
     */
    fun extractVariableNames(text: String): List<String> {
        if (!text.contains("{{")) return emptyList()
        return PLACEHOLDER.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }

    /**
     * 预展开变量值之间的嵌套引用，例如 token=Bearer {{rawToken}}。
     * 检测到循环引用或超过最大层数时保留原文。
     */
    private fun expandVariables(
        variables: Map<String, String>,
        unresolved: MutableSet<String>,
    ): Map<String, String> {
        if (variables.isEmpty()) return variables
        val expanded = HashMap(variables)
        repeat(MAX_DEPTH) {
            var changed = false
            for ((name, value) in expanded) {
                if (!value.contains("{{")) continue
                val replaced = PLACEHOLDER.replace(value) { match ->
                    val refName = match.groupValues[1]
                    if (refName == name) {
                        unresolved += refName
                        match.value
                    } else {
                        val refValue = expanded[refName]
                        if (refValue == null) {
                            unresolved += refName
                            match.value
                        } else {
                            changed = true
                            refValue
                        }
                    }
                }
                expanded[name] = replaced
            }
            if (!changed) return expanded
        }
        return expanded
    }
}
