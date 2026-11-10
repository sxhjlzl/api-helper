package com.lizhuolun.apihelper.ui.debug

/**
 * IDEA HTTP Client 请求文件生成工具。
 *
 * @author lizhuolun
 * @date 2026/11/9
 */
object HttpRequestFileBuilder {

    /**
     * 把结构化请求转换为 HTTP Client 可执行的单条请求文本。
     *
     * @param request 从端点生成的结构化请求
     * @return .http 文件内容
     */
    internal fun build(request: EndpointRequestData): String = buildString {
        val method = request.method.ifBlank { "GET" }.uppercase()
        val url = CurlCommandBuilder.appendQuery(
            CurlCommandBuilder.applyPathParams(request.rawUrl.trim(), request.pathParams),
            request.queryParams,
        )
        append(method)
        append(' ')
        appendLine(url)

        val headers = LinkedHashMap<String, String>()
        for ((name, value) in request.headers) {
            if (name.isNotBlank()) headers[name] = value
        }
        if (request.contentType.isNotBlank() &&
            method != "GET" &&
            method != "HEAD" &&
            headers.keys.none { it.equals("Content-Type", ignoreCase = true) }
        ) {
            headers["Content-Type"] = request.contentType
        }
        val cookie = request.cookies
            .filter { it.first.isNotBlank() }
            .joinToString("; ") { (name, value) -> "$name=$value" }
        if (cookie.isNotBlank() && headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
            headers["Cookie"] = cookie
        }
        for ((name, value) in headers) {
            append(name)
            append(": ")
            appendLine(value)
        }

        if (method != "GET" && method != "HEAD" && request.bodyText.isNotBlank()) {
            appendLine()
            append(request.bodyText)
            appendLine()
        }
    }
}

/**
 * 端点请求的结构化数据，供 cURL 与 HTTP Client 两种格式共用。
 *
 * @property method HTTP 方法
 * @property rawUrl 原始 URL
 * @property pathParams Path 参数
 * @property queryParams Query 参数
 * @property headers 请求头
 * @property cookies Cookie 参数
 * @property contentType 默认 Content-Type
 * @property bodyText 文本请求体
 */
internal data class EndpointRequestData(
    val method: String,
    val rawUrl: String,
    val pathParams: List<Pair<String, String>> = emptyList(),
    val queryParams: List<Pair<String, String>> = emptyList(),
    val headers: List<Pair<String, String>> = emptyList(),
    val cookies: List<Pair<String, String>> = emptyList(),
    val contentType: String = "",
    val bodyText: String = "",
)
