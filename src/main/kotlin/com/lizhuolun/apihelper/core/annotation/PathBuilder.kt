package com.lizhuolun.apihelper.core.annotation

/**
 * URL 拼接工具。
 *
 * 关键设计原则：
 * 1. Feign 侧只拼接 (类级 path) + (方法级 path)，不带 server.context-path，
 *    因为 Feign 真正发起调用的目标 URL 就是这样组装的。
 * 2. Controller 侧需要拼接 server.context-path + spring.mvc.path + 类级 + 方法级，
 *    才能与 Feign 完全匹配。
 * 3. 所有片段都被规范化为以 / 开头、不以 / 结尾，
 *    避免空片段或重复斜杠造成误判。
 */
object PathBuilder {

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
