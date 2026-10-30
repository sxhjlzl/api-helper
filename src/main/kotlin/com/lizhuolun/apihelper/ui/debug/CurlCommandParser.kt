package com.lizhuolun.apihelper.ui.debug

import java.util.Base64

/**
 * cURL 命令解析器：把从浏览器 DevTools / 文档复制的 curl 命令还原为调试面板可用的结构化请求。
 *
 * 支持常见选项：`-X`、`-H`、`-d` / `--data-raw` / `--data-binary`、`-F`、`-b`、`-u`、`-G`，
 * 以及单双引号与反斜杠续行；无法识别的选项静默忽略，不让一条命令整体解析失败。
 *
 * @author lizhuolun
 * @date 2026/10/29
 */
object CurlCommandParser {

    /**
     * 解析结果。
     *
     * @property method HTTP 方法
     * @property url 不含 Query 串的 URL
     * @property query URL Query 串拆出的参数
     * @property headers 请求头
     * @property cookies Cookie 键值对（含 Cookie 请求头与 -b 选项）
     * @property bodyText 文本请求体
     * @property formData form-data 参数行（文件字段以 `=@路径` 形式存储在 value 中）
     * @property binaryFilePath `--data-binary @file` 的文件路径
     */
    data class ParsedCurlRequest(
        val method: String = "GET",
        val url: String = "",
        val query: List<Pair<String, String>> = emptyList(),
        val headers: List<Pair<String, String>> = emptyList(),
        val cookies: List<Pair<String, String>> = emptyList(),
        val bodyText: String = "",
        val formData: List<DebugParameterRow> = emptyList(),
        val binaryFilePath: String = "",
    )

    /**
     * 解析 curl 命令文本。
     *
     * @param command 原始命令，支持多行续行与引号
     * @return 结构化请求；文本不是 curl 命令时返回 null
     */
    fun parse(command: String): ParsedCurlRequest? {
        val tokens = tokenize(command)
        if (tokens.size < 2 || tokens.first() != "curl") {
            return null
        }

        var method = ""
        var url = ""
        val headers = ArrayList<Pair<String, String>>()
        val cookies = ArrayList<Pair<String, String>>()
        val bodyParts = ArrayList<String>()
        val formData = ArrayList<DebugParameterRow>()
        var binaryFilePath = ""
        var dataWithGet = false

        var i = 1
        while (i < tokens.size) {
            val token = tokens[i]
            when (token) {
                "-X", "--request" -> method = tokens.getOrNull(++i).orEmpty().uppercase()
                "-H", "--header" -> {
                    val header = tokens.getOrNull(++i).orEmpty()
                    val idx = header.indexOf(':')
                    if (idx > 0) {
                        val name = header.substring(0, idx).trim()
                        val value = header.substring(idx + 1).trim()
                        if (name.equals("Cookie", ignoreCase = true)) {
                            cookies += parseCookiePairs(value)
                        } else {
                            headers += name to value
                        }
                    }
                }
                "-d", "--data", "--data-raw", "--data-ascii" -> bodyParts += tokens.getOrNull(++i).orEmpty()
                "--data-binary" -> {
                    val value = tokens.getOrNull(++i).orEmpty()
                    if (value.startsWith("@")) {
                        binaryFilePath = value.removePrefix("@")
                    } else {
                        bodyParts += value
                    }
                }
                "-F", "--form" -> {
                    val field = tokens.getOrNull(++i).orEmpty()
                    val idx = field.indexOf('=')
                    if (idx > 0) {
                        val name = field.substring(0, idx)
                        val value = field.substring(idx + 1)
                        formData += if (value.startsWith("@")) {
                            DebugParameterRow(name = name, value = value.removePrefix("@"), type = BodyFormDataTableModel.TYPE_FILE)
                        } else {
                            DebugParameterRow(name = name, value = value, type = BodyFormDataTableModel.TYPE_TEXT)
                        }
                    }
                }
                "-b", "--cookie" -> cookies += parseCookiePairs(tokens.getOrNull(++i).orEmpty())
                "-u", "--user" -> {
                    val credential = tokens.getOrNull(++i).orEmpty()
                    if (credential.isNotBlank()) {
                        val encoded = Base64.getEncoder().encodeToString(credential.toByteArray(Charsets.UTF_8))
                        headers += "Authorization" to "Basic $encoded"
                    }
                }
                "-G", "--get" -> dataWithGet = true
                else -> {
                    // 忽略其他选项的参数（如 --connect-timeout 10），无短横线开头的视为 URL
                    if (!token.startsWith("-") && url.isEmpty()) {
                        url = token
                    } else if (token.startsWith("--") && tokens.getOrNull(i + 1)?.startsWith("-") == false) {
                        i++
                    }
                }
            }
            i++
        }

        if (url.isEmpty()) return null

        val queryIndex = url.indexOf('?')
        val queryPairs = if (queryIndex >= 0) {
            val queryText = url.substring(queryIndex + 1)
            url = url.substring(0, queryIndex)
            queryText.split('&')
                .filter { it.isNotBlank() }
                .map { pair ->
                    val idx = pair.indexOf('=')
                    if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1) else pair to ""
                }
        } else {
            emptyList()
        }

        if (dataWithGet && bodyParts.isNotEmpty()) {
            val extra = bodyParts.joinToString("&").split('&')
                .filter { it.isNotBlank() }
                .map { pair ->
                    val idx = pair.indexOf('=')
                    if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1) else pair to ""
                }
            return ParsedCurlRequest(
                method = "GET",
                url = url,
                query = queryPairs + extra,
                headers = headers.distinctBy { it.first.lowercase() },
                cookies = cookies.distinctBy { it.first },
            )
        }

        val effectiveMethod = when {
            method.isNotBlank() -> method
            bodyParts.isNotEmpty() || formData.isNotEmpty() || binaryFilePath.isNotBlank() -> "POST"
            else -> "GET"
        }

        return ParsedCurlRequest(
            method = effectiveMethod,
            url = url,
            query = queryPairs,
            headers = headers.distinctBy { it.first.lowercase() },
            cookies = cookies.distinctBy { it.first },
            bodyText = bodyParts.joinToString("&"),
            formData = formData,
            binaryFilePath = binaryFilePath,
        )
    }

    /**
     * 按 Shell 习惯切分命令：支持反斜杠续行、单引号（原样）、双引号（保留转义）。
     */
    private fun tokenize(command: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var hasContent = false

        val text = command.replace(Regex("\\\\\\r?\\n"), " ")
        for (ch in text) {
            when {
                inSingle -> {
                    if (ch == '\'') inSingle = false else current.append(ch)
                }
                inDouble -> {
                    when (ch) {
                        '"' -> inDouble = false
                        '\\' -> current.append(ch)
                        else -> current.append(ch)
                    }
                }
                ch == '\'' -> {
                    inSingle = true
                    hasContent = true
                }
                ch == '"' -> {
                    inDouble = true
                    hasContent = true
                }
                ch.isWhitespace() -> {
                    if (current.isNotEmpty() || hasContent) {
                        tokens += current.toString()
                        current.clear()
                        hasContent = false
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty() || hasContent) {
            tokens += current.toString()
        }
        return tokens
    }

    /**
     * 拆分 `a=1; b=2` 形式的 Cookie 串。
     */
    private fun parseCookiePairs(raw: String): List<Pair<String, String>> =
        raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) pair.substring(0, idx).trim() to pair.substring(idx + 1).trim() else pair to ""
            }
}
