package com.lizhuolun.apihelper.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.lizhuolun.apihelper.ui.debug.DebugParameterRow

/**
 * ApiHelper 全局设置，应用级 Service，通过 PersistentStateComponent 持久化到 IDE 配置目录。
 *
 * 业务代码通过 ApiHelperSettings.getInstance().state 读取或修改设置项。
 *
 * @author lizhuolun
 * @date 2026/6/9
 */
@Service(Service.Level.APP)
@State(
    name = "ApiHelperSettings",
    storages = [Storage("ApiHelperSettings.xml")],
)
class ApiHelperSettings : PersistentStateComponent<ApiHelperSettings.SettingsState> {

    /**
     * 可序列化的设置状态。
     *
     * 字段必须是 var、public、有空构造器，XML 序列化器才能正确读写。
     */
    class SettingsState {
        /**
         * 手动覆盖 Spring 激活的 profile，逗号分隔；为空表示自动从配置推断
         **/
        var manualActiveProfile: String = ""

        /**
         * 调试请求的连接超时，单位秒；范围 1-300，越界时由读取方自行夹取
         **/
        var connectTimeoutSeconds: Int = 30

        /**
         * 调试请求的整体超时，单位秒；范围 1-600
         **/
        var requestTimeoutSeconds: Int = 60

        /**
         * 全局公共请求头，多行文本，每行格式为 Name: Value 或 [disabled] Name: Value。
         * 发送调试请求或生成 cURL 时自动合并，单接口同名 Header 优先级更高。
         **/
        var globalHeadersText: String = ""

        /**
         * 全局公共 Query 参数，多行文本，每行格式为 Name=Value 或 [disabled] Name=Value。
         **/
        var globalQueriesText: String = ""

        /**
         * 全局公共 Cookie 参数，多行文本，每行格式为 Name=Value 或 [disabled] Name=Value。
         **/
        var globalCookiesText: String = ""

        /**
         * 调试环境 Base URL 列表，多行文本，每行格式为 环境名=http://host:port 或直接写 http://host:port。
         **/
        var environmentsText: String = ""

        /**
         * 当前选中的调试环境名称，为空表示使用自动推断的本地端口地址。
         **/
        var selectedEnvironmentName: String = ""

        /**
         * 全局变量，多行文本，每行格式为 Name=Value 或 [disabled] Name=Value。
         * 调试请求与 cURL 导出中的 {{Name}} 占位符会被替换为对应值。
         **/
        var globalVariablesText: String = ""

        /**
         * 环境级变量，多行文本，每行格式为 环境名.Name=Value 或 [disabled] 环境名.Name=Value。
         * 仅在选择对应环境时生效，同名时覆盖全局变量。
         **/
        var environmentVariablesText: String = ""
    }

    /**
     * 解析已启用的全局公共 Header 列表。
     *
     * @return 解析后的 (Header名, Header值) 列表
     */
    fun parseGlobalHeaders(): List<Pair<String, String>> =
        parseGlobalHeaderRows().filter { it.enabled }.map { it.name to it.value }

    /**
     * 解析全部全局公共 Header 行（含未勾选禁用项）。
     */
    fun parseGlobalHeaderRows(): List<DebugParameterRow> =
        parseParameterRows(internalState.globalHeadersText, charArrayOf(':', '='))

    /**
     * 解析已启用的全局公共 Query 参数列表。
     */
    fun parseGlobalQueries(): List<Pair<String, String>> =
        parseGlobalQueryRows().filter { it.enabled }.map { it.name to it.value }

    /**
     * 解析全部全局公共 Query 参数行（含未勾选禁用项）。
     */
    fun parseGlobalQueryRows(): List<DebugParameterRow> =
        parseParameterRows(internalState.globalQueriesText, charArrayOf('=', ':'))

    /**
     * 解析已启用的全局公共 Cookie 参数列表。
     */
    fun parseGlobalCookies(): List<Pair<String, String>> =
        parseGlobalCookieRows().filter { it.enabled }.map { it.name to it.value }

    /**
     * 解析全部全局公共 Cookie 参数行（含未勾选禁用项）。
     */
    fun parseGlobalCookieRows(): List<DebugParameterRow> =
        parseParameterRows(internalState.globalCookiesText, charArrayOf('=', ':'))

    /**
     * 解析全部全局变量行（含未勾选禁用项）。
     */
    fun parseGlobalVariableRows(): List<DebugParameterRow> =
        parseParameterRows(internalState.globalVariablesText, charArrayOf('='))

    /**
     * 解析全部环境级变量行（含未勾选禁用项），参数名形如 `环境名.变量名`。
     */
    fun parseEnvironmentVariableRows(): List<DebugParameterRow> =
        parseParameterRows(internalState.environmentVariablesText, charArrayOf('='))

    /**
     * 计算指定环境下生效的变量表：全局变量为底，当前环境的同名变量覆盖全局值。
     *
     * @param environmentName 当前选中的环境名称，为空表示未选择环境（仅全局变量生效）
     * @return 变量名到变量值的有序映射
     */
    fun resolveVariables(environmentName: String): Map<String, String> {
        val resolved = LinkedHashMap<String, String>()
        for (row in parseGlobalVariableRows()) {
            if (row.enabled) {
                resolved[row.name.trim()] = row.value
            }
        }
        if (environmentName.isNotBlank()) {
            val prefix = "${environmentName.trim()}."
            for (row in parseEnvironmentVariableRows()) {
                val name = row.name.trim()
                if (row.enabled && name.startsWith(prefix) && name.length > prefix.length) {
                    resolved[name.substring(prefix.length)] = row.value
                }
            }
        }
        return resolved
    }

    /**
     * 写入或更新一个变量；同名变量覆盖原值并保持启用状态。
     *
     * @param name 变量名
     * @param value 变量值
     * @param environmentName 环境名称；非空时写入环境级变量（`环境名.变量名`），为空时写入全局变量
     */
    fun upsertVariable(name: String, value: String, environmentName: String = "") {
        val key = name.trim()
        if (key.isEmpty()) return
        if (environmentName.isBlank()) {
            internalState.globalVariablesText = upsertRowText(internalState.globalVariablesText, key, value)
        } else {
            internalState.environmentVariablesText =
                upsertRowText(internalState.environmentVariablesText, "${environmentName.trim()}.$key", value)
        }
    }

    /**
     * 在多行键值文本中覆盖或追加一行。
     */
    private fun upsertRowText(raw: String, key: String, value: String): String {
        val rows = parseParameterRows(raw, charArrayOf('=')).toMutableList()
        val index = rows.indexOfFirst { it.name.trim() == key }
        if (index >= 0) {
            rows[index] = rows[index].copy(value = value, enabled = true)
        } else {
            rows += DebugParameterRow(enabled = true, name = key, value = value)
        }
        return formatParameterRows(rows, separator = "=")
    }

    /**
     * 解析调试环境列表。
     *
     * @return 解析后的调试环境配置列表
     */
    fun parseEnvironments(): List<DebugEnvironment> =
        internalState.environmentsText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val eqIndex = line.indexOf('=')
                if (eqIndex > 0) {
                    val name = line.substring(0, eqIndex).trim()
                    val baseUrl = line.substring(eqIndex + 1).trim().trimEnd('/')
                    if (name.isNotEmpty() && baseUrl.isNotEmpty()) {
                        DebugEnvironment(name, baseUrl)
                    } else {
                        null
                    }
                } else {
                    val baseUrl = line.trimEnd('/')
                    DebugEnvironment(baseUrl, baseUrl)
                }
            }
            .distinctBy { it.name }
            .toList()

    private var internalState: SettingsState = SettingsState()

    override fun getState(): SettingsState = internalState

    override fun loadState(state: SettingsState) {
        XmlSerializerUtil.copyBean(state, internalState)
    }

    companion object {
        private const val DISABLED_PREFIX = "[disabled]"

        fun getInstance(): ApiHelperSettings =
            ApplicationManager.getApplication().getService(ApiHelperSettings::class.java)

        /**
         * 解析多行键值对配置文本（仅返回已启用项），忽略空行与 # 注释行。
         *
         * @param raw 原始多行文本
         * @param delimiters 允许的键值分隔符
         * @return 键值对列表
         */
        internal fun parseKeyValueLines(raw: String, delimiters: CharArray): List<Pair<String, String>> =
            parseParameterRows(raw, delimiters)
                .filter { it.enabled }
                .map { it.name to it.value }

        /**
         * 解析带启用状态的参数行列表，支持 `[disabled] key: value` 标记未勾选行。
         */
        internal fun parseParameterRows(raw: String, delimiters: CharArray): List<DebugParameterRow> =
            raw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { rawLine ->
                    val enabled = !rawLine.startsWith(DISABLED_PREFIX, ignoreCase = true)
                    val line = if (enabled) rawLine else rawLine.substring(DISABLED_PREFIX.length).trim()
                    val idx = line.indexOfAny(delimiters)
                    if (idx <= 0) return@mapNotNull null
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isEmpty()) null else DebugParameterRow(enabled = enabled, name = key, value = value)
                }
                .toList()

        /**
         * 将参数行列表序列化为多行持久化文本，保留未勾选行的 `[disabled]` 前缀。
         */
        internal fun formatParameterRows(rows: List<DebugParameterRow>, separator: String = ": "): String =
            rows.filter { it.name.isNotBlank() }
                .joinToString("\n") { row ->
                    val prefix = if (row.enabled) "" else "$DISABLED_PREFIX "
                    "$prefix${row.name.trim()}$separator${row.value}"
                }
    }
}

/**
 * 调试环境配置项。
 *
 * @property name 环境展示名称，例如 Local / Dev / Test
 * @property baseUrl 环境基础地址，例如 http://127.0.0.1:8080
 */
data class DebugEnvironment(
    val name: String,
    val baseUrl: String,
)
