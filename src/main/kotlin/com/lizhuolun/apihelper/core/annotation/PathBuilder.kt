package com.lizhuolun.apihelper.core.annotation

/**
 * URL 拼接工具。
 *
 * 关键设计原则：
 * 1. Feign 侧只拼接 (类级 path) + (方法级 path)，不带 server.context-path，
 *    因为 Feign 真正发起调用的目标 URL 就是这样组装的。
 * 2. Controller 侧需要拼接 server.context-path + spring.mvc.path + 类级 + 方法级，
 *    用于展示与复制完整访问地址。
 * 3. 两端互相匹配时使用不含 context-path 的相对路径（见 normalizeForMatch），
 *    否则配置了非空 context-path 的工程两端永远无法匹配。
 * 4. 所有片段都被规范化为以 / 开头、不以 / 结尾，
 *    避免空片段或重复斜杠造成误判。
 */
object PathBuilder {

    private val PATH_VARIABLE_REGEX = Regex("\\{[^}/]*}")

    /**
     * 把任意路径片段标准化：去除首尾空格，确保以 / 开头，不以 / 结尾（根路径除外）。
     * 空字符串返回 ""。
     */
    fun normalize(segment: String?): String {
        if (segment.isNullOrBlank()) return ""
        var trimmed = segment.trim()
        if (!trimmed.startsWith("/")) trimmed = "/$trimmed"
        while (trimmed.length > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.dropLast(1)
        }
        return trimmed
    }

    /**
     * 把多个路径片段顺序拼接为一个 URL，自动规范化每段。
     * 全为空时返回 "/"，否则保证以 / 开头。
     */
    fun join(vararg segments: String?): String {
        val sb = StringBuilder()
        for (seg in segments) {
            val normalized = normalize(seg)
            if (normalized.isNotEmpty() && normalized != "/") {
                sb.append(normalized)
            }
        }
        val joined = sb.toString()
        return if (joined.isEmpty()) "/" else joined
    }

    /**
     * 把路径中的路径变量统一归一化为 {}，用于两端匹配比较。
     * 这样 /user/{id} 与 /user/{userId} 会被视为同一路径，
     * 避免因两端路径变量命名不一致导致匹配失败。
     *
     * @param url 原始路径，通常是不含 context-path 的相对路径
     * @return 归一化后的路径，路径变量占位符统一为 {}
     */
    fun normalizeForMatch(url: String): String = PATH_VARIABLE_REGEX.replace(url, "{}")

    /**
     * Controller 端拼装完整 URL。
     */
    fun buildControllerUrl(
        serverContextPath: String?,
        mvcServletPath: String?,
        classLevelPath: String?,
        methodLevelPath: String?,
    ): String = join(serverContextPath, mvcServletPath, classLevelPath, methodLevelPath)

    /**
     * Feign / HttpExchange 客户端 URL。
     */
    fun buildClientUrl(
        classLevelPath: String?,
        methodLevelPath: String?,
    ): String = join(classLevelPath, methodLevelPath)
}
