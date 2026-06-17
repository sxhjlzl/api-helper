package com.lizhuolun.apihelper.config

/**
 * 解析当前激活的 Spring profile 列表。
 *
 * 优先级（从高到低）：
 * 1. 用户在 ApiHelper 设置里手动指定
 * 2. spring.profiles.active（properties 或 yml 顶层 key）
 * 3. spring.profiles.default
 */
object ProfileResolver {

    private const val ACTIVE_KEY = "spring.profiles.active"
    private const val DEFAULT_KEY = "spring.profiles.default"

    /**
     * 解析激活的 profile 列表，可能为空。
     *
     * @param defaultProperties 默认配置（无 profile）中读到的所有键值
     * @param manualOverride 用户在 ApiHelper 设置里手动指定的 profile（逗号分隔）
     */
    fun resolveActiveProfiles(
        defaultProperties: Map<String, Any?>,
        manualOverride: String?,
    ): List<String> {
        if (!manualOverride.isNullOrBlank()) {
            return splitProfiles(manualOverride)
        }
        defaultProperties[ACTIVE_KEY]?.toString()?.let { active ->
            if (active.isNotBlank()) return splitProfiles(active)
        }
        defaultProperties[DEFAULT_KEY]?.toString()?.let { fallback ->
            if (fallback.isNotBlank()) return splitProfiles(fallback)
        }
        return emptyList()
    }

    /**
     * 把 "dev,test" / "dev ,prod" 拆成单元素列表，去重去空白。
     */
    private fun splitProfiles(raw: String): List<String> =
        raw.split(',', ' ', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}
