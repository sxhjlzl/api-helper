package com.lizhuolun.apihelper.ui.debug

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 项目级接口调试草稿与请求历史缓存服务。
 *
 * 1. 按端点标识保存用户在调试面板中修改过的请求参数草稿；
 * 2. 保存当前工程会话内最近发送的调试请求历史记录（最多 30 条），支持一键回放请求与响应。
 *
 * @author lizhuolun
 * @date 2026/10/16
 */
@Service(Service.Level.PROJECT)
class DebugDraftService {

    private val drafts = ConcurrentHashMap<String, EndpointDebugDraft>()
    private val history = CopyOnWriteArrayList<DebugHistoryEntry>()

    /**
     * 获取指定端点已保存的调试草稿。
     *
     * @param endpointKey 端点唯一标识
     * @return 已保存草稿，未保存过时返回 null
     */
    fun getDraft(endpointKey: String): EndpointDebugDraft? = drafts[endpointKey]

    /**
     * 保存或覆盖指定端点的调试草稿。
     *
     * @param endpointKey 端点唯一标识
     * @param draft 调试草稿快照
     */
    fun saveDraft(endpointKey: String, draft: EndpointDebugDraft) {
        if (endpointKey.isBlank()) return
        drafts[endpointKey] = draft
    }

    /**
     * 清除指定端点的调试草稿，以便恢复到从源码解析的初始参数。
     *
     * @param endpointKey 端点唯一标识
     */
    fun removeDraft(endpointKey: String) {
        if (endpointKey.isBlank()) return
        drafts.remove(endpointKey)
    }

    /**
     * 新增一条调试请求历史记录，按时间倒序保留最近 [MAX_HISTORY_SIZE] 条。
     *
     * @param entry 历史记录条目
     */
    fun addHistory(entry: DebugHistoryEntry) {
        history.add(0, entry)
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
    }

    /**
     * 获取全部调试请求历史记录（最新在前）。
     *
     * @return 历史记录列表快照
     */
    fun getHistory(): List<DebugHistoryEntry> = history.toList()

    /**
     * 清空调试请求历史记录。
     */
    fun clearHistory() {
        history.clear()
    }

    companion object {
        private const val MAX_HISTORY_SIZE = 30
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        /**
         * 生成当前时间的 HH:mm:ss 文本。
         */
        fun currentTimeText(): String = LocalTime.now().format(TIME_FORMATTER)

        /**
         * 获取当前工程的调试草稿服务实例。
         *
         * @param project 当前工程
         * @return 项目级调试草稿服务
         */
        fun of(project: Project): DebugDraftService =
            project.getService(DebugDraftService::class.java)
    }
}

/**
 * 单个端点的调试面板草稿快照。
 *
 * @property method HTTP 方法名
 * @property url 完整请求 URL
 * @property query Query 参数键值对
 * @property path Path 参数键值对
 * @property headers Header 参数键值对
 * @property cookies Cookie 参数键值对
 * @property bodyTypeName 请求体模式枚举名
 * @property bodyText 文本请求体内容
 * @property binaryPath 二进制文件路径
 * @property formData form-data 参数行列表
 * @property urlEncoded x-www-form-urlencoded 参数键值对
 */
data class EndpointDebugDraft(
    val method: String,
    val url: String,
    val query: List<Pair<String, String>>,
    val path: List<Pair<String, String>>,
    val headers: List<Pair<String, String>>,
    val cookies: List<Pair<String, String>>,
    val bodyTypeName: String,
    val bodyText: String,
    val binaryPath: String,
    val formData: List<DebugParameterRow>,
    val urlEncoded: List<Pair<String, String>>,
)

/**
 * 单次调试请求与响应的历史记录。
 *
 * @property timestampText 请求发送时间（HH:mm:ss）
 * @property draft 请求参数快照
 * @property statusCode HTTP 响应状态码，请求异常时为 null
 * @property elapsedMs 耗时毫秒数
 * @property responseBody 响应体文本
 * @property responseHeaders 响应头文本
 * @property responseCookies 响应 Cookie 文本
 */
data class DebugHistoryEntry(
    val timestampText: String,
    val draft: EndpointDebugDraft,
    val statusCode: Int?,
    val elapsedMs: Long,
    val responseBody: String,
    val responseHeaders: String,
    val responseCookies: String,
) {
    override fun toString(): String {
        val statusPart = statusCode?.toString() ?: "ERR"
        return "[$timestampText] ${draft.method} ${draft.url}  ($statusPart, ${elapsedMs}ms)"
    }
}
