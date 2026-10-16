package com.lizhuolun.apihelper.ui.debug

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * cURL 命令生成工具。
 *
 * 将接口请求参数（HTTP 方法、URL、Path/Query 参数、全局与接口级 Header、Cookie、各类 Body）
 * 组装为可直接复制到终端执行的标准 cURL 命令。
 *
 * @author lizhuolun
 * @date 2026/10/16
 */
object CurlCommandBuilder {

    /**
     * 构建单条标准 cURL 命令。
     *
     * @param method HTTP 方法，如 GET / POST
     * @param rawUrl 原始 URL（可含 {pathVar} 占位符）
     * @param pathParams Path 参数键值对
     * @param queryParams Query 参数键值对
     * @param headers 已合并全局 Header 后的请求头列表
     * @param cookies Cookie 参数键值对
     * @param contentType 默认 Content-Type（当 headers 未显式指定时生效）
     * @param bodyText 文本请求体（JSON / XML / raw / urlencoded）
     * @param formDataRows form-data 参数行列表
     * @param binaryFilePath 二进制上传文件路径
     * @return 标准 cURL 命令字符串
     */
    fun build(
        method: String,
        rawUrl: String,
        pathParams: List<Pair<String, String>> = emptyList(),
        queryParams: List<Pair<String, String>> = emptyList(),
        headers: List<Pair<String, String>> = emptyList(),
        cookies: List<Pair<String, String>> = emptyList(),
        contentType: String = "",
        bodyText: String = "",
        formDataRows: List<DebugParameterRow> = emptyList(),
        binaryFilePath: String = "",
    ): String {
        val effectiveMethod = method.ifBlank { "GET" }.uppercase()
        val resolvedUrl = appendQuery(applyPathParams(rawUrl.trim(), pathParams), queryParams)
        val lines = mutableListOf<String>()
        lines += "curl -X $effectiveMethod ${shellQuote(resolvedUrl)}"

        val mergedHeaders = LinkedHashMap<String, String>()
        for ((name, value) in headers) {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                mergedHeaders[trimmed] = value
            }
        }
        if (contentType.isNotBlank() &&
            effectiveMethod != "GET" &&
            effectiveMethod != "HEAD" &&
            formDataRows.isEmpty() &&
            mergedHeaders.keys.none { it.equals("Content-Type", ignoreCase = true) }
        ) {
            mergedHeaders["Content-Type"] = contentType
        }

        val cookieHeader = cookies
            .filter { it.first.isNotBlank() }
            .joinToString("; ") { "${it.first.trim()}=${it.second}" }
        if (cookieHeader.isNotBlank() && mergedHeaders.keys.none { it.equals("Cookie", ignoreCase = true) }) {
            mergedHeaders["Cookie"] = cookieHeader
        }

        for ((name, value) in mergedHeaders) {
            lines += "  -H ${shellQuote("$name: $value")}"
        }

        if (effectiveMethod != "GET" && effectiveMethod != "HEAD") {
            when {
                formDataRows.isNotEmpty() -> {
                    for (row in formDataRows) {
                        if (!row.enabled || row.name.isBlank()) continue
                        val fieldSpec = if (row.type == BodyFormDataTableModel.TYPE_FILE) {
                            "${row.name.trim()}=@${row.value.trim()}"
                        } else {
                            "${row.name.trim()}=${row.value}"
                        }
                        lines += "  -F ${shellQuote(fieldSpec)}"
                    }
                }
                binaryFilePath.isNotBlank() -> {
                    lines += "  --data-binary ${shellQuote("@${binaryFilePath.trim()}")}"
                }
                bodyText.isNotBlank() -> {
                    lines += "  -d ${shellQuote(bodyText)}"
                }
            }
        }

        return lines.joinToString(" \\\n")
    }

    /**
     * 合并全局参数与当前接口参数，同名（忽略大小写）时当前接口参数优先。
     *
     * @param globalHeaders 全局公共参数（Header / Query / Cookie）
     * @param endpointHeaders 当前接口参数
     * @return 合并后的参数列表
     */
    fun mergeHeaders(
        globalHeaders: List<Pair<String, String>>,
        endpointHeaders: List<Pair<String, String>>,
    ): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>(globalHeaders.size + endpointHeaders.size)
        for ((gName, gVal) in globalHeaders) {
            if (gName.isBlank()) continue
            if (endpointHeaders.none { it.first.equals(gName, ignoreCase = true) }) {
                result += gName to gVal
            }
        }
        for ((eName, eVal) in endpointHeaders) {
            if (eName.isNotBlank()) {
                result += eName to eVal
            }
        }
        return result
    }

    private fun applyPathParams(rawUrl: String, params: List<Pair<String, String>>): String {
        var url = rawUrl
        for ((name, value) in params) {
            if (name.isBlank() || value.isBlank()) continue
            url = url.replace("{$name}", encode(value))
        }
        return url
    }

    private fun appendQuery(rawUrl: String, query: List<Pair<String, String>>): String {
        val pairs = query.filter { it.first.isNotBlank() }
        if (pairs.isEmpty()) return rawUrl
        val separator = when {
            rawUrl.contains("?") && !rawUrl.endsWith("?") && !rawUrl.endsWith("&") -> "&"
            rawUrl.endsWith("?") || rawUrl.endsWith("&") -> ""
            else -> "?"
        }
        val queryText = pairs.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
        return "$rawUrl$separator$queryText"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\''")}'"
}
