package com.lizhuolun.apihelper.core.contract

import com.lizhuolun.apihelper.core.HttpMappingInfo

/**
 * 双边映射的一次性只读快照。
 *
 * 契约体检需要在一次遍历中同时访问客户端侧与服务端侧，
 * 若反复调用 getService() 并逐条查询，会不断进出 read action 且拿到彼此不一致的视图。
 * 因此 [com.lizhuolun.apihelper.cache.BilateralMappingCacheService] 提供该快照，
 * 保证一次体检看到的是同一版本的数据。
 *
 * @property clients 客户端侧（Feign / HttpExchange）全部映射
 * @property controllers 服务端侧全部映射
 * @property clientsByMatchUrl 客户端按归一化路径的倒排索引，供服务端侧反查调用方
 * @property controllersByMatchUrl 服务端按归一化路径的倒排索引，供 O(1) 配对
 * @property version 快照版本号，缓存发生任何变更时自增，用于判断报告是否过期
 * @author lizhuolun
 * @date 2026/10/27
 */
data class BilateralSnapshot(
    val clients: List<HttpMappingInfo>,
    val controllers: List<HttpMappingInfo>,
    val clientsByMatchUrl: Map<String, List<HttpMappingInfo>>,
    val controllersByMatchUrl: Map<String, List<HttpMappingInfo>>,
    val version: Long,
) {

    /**
     * 工程内是否存在任何服务端端点。
     *
     * 纯客户端模块（例如只依赖外部服务的 SDK 工程）不应触发孤儿接口告警，
     * 规则层用该标志整体跳过。
     */
    val hasControllers: Boolean
        get() = controllers.isNotEmpty()

    companion object {

        /**
         * 空快照，用于缓存尚未预热完成的场景。
         */
        val EMPTY = BilateralSnapshot(
            clients = emptyList(),
            controllers = emptyList(),
            clientsByMatchUrl = emptyMap(),
            controllersByMatchUrl = emptyMap(),
            version = 0L,
        )
    }
}