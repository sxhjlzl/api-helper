package com.lizhuolun.apihelper.config

/**
 * Spring 风格占位符解析器，支持 ${key} 与 ${key:default} 两种语法。
 *
 * 仅做单层替换 + 最多 8 轮递归（与 Spring 默认 PropertyPlaceholderHelper 对齐，
 * 防止占位符内部又引用占位符的死循环），不解析 SpEL 表达式 #{...}。
 *
 * @author lizhuolun
 * @date 2026/6/9
 */
object PlaceholderResolver {

    /**
     * 占位符匹配正则，禁止内部再嵌套 $ 或大括号，避免回溯失控
     **/
    private val PLACEHOLDER_REGEX = Regex("""\$\{([^${'$'}{}]+)\}""")

    /**
     * 最大递归轮次，与 Spring PropertyPlaceholderHelper 默认值保持一致
     **/
    private const val MAX_RECURSION = 8

    /**
     * 把字符串中的所有 ${key} / ${key:default} 替换为 properties 中的值。
     * 找不到且无默认值时保留原始 ${key} 字面量，便于上层人工排查。
     *
     * @param raw 待解析的原始字符串，可为空
     * @param properties 来自 application.yml / properties 的扁平化键值表
     * @return 替换后的字符串；raw 为空时返回 ""
     */
    fun resolve(raw: String?, properties: Map<String, Any?>): String {
        var current = raw?.takeIf { it.isNotEmpty() } ?: return ""
        repeat(MAX_RECURSION) {
            val replaced = PLACEHOLDER_REGEX.replace(current) { match ->
                val expression = match.groupValues[1]
                val colonIdx = expression.indexOf(':')
                val key: String
                val default: String?
                if (colonIdx >= 0) {
                    key = expression.substring(0, colonIdx).trim()
                    default = expression.substring(colonIdx + 1)
                } else {
                    key = expression.trim()
                    default = null
                }
                val value = properties[key]?.toString()
                value ?: default ?: match.value
            }
            if (replaced == current) return current
            current = replaced
        }
        return current
    }
}
