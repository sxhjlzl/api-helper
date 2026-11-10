package com.lizhuolun.apihelper.cache

import com.lizhuolun.apihelper.core.HttpMappingInfo

/**
 * 双边映射的一次性只读快照。
 *
 * 同一次批量操作共享快照，避免反复读取缓存时拿到不同版本的数据。
 *
 * @property clients 客户端侧（Feign / HttpExchange）全部映射
 * @property controllers 服务端侧全部映射
 * @property clientsByMatchUrl 客户端按归一化路径的倒排索引
 * @property controllersByMatchUrl 服务端按归一化路径的倒排索引
 * @property version 快照版本号，缓存发生任何变更时自增
 * @author lizhuolun
 * @date 2026/10/27
 */
data class BilateralSnapshot(
    val clients: List<HttpMappingInfo>,
    val controllers: List<HttpMappingInfo>,
    val clientsByMatchUrl: Map<String, List<HttpMappingInfo>>,
    val controllersByMatchUrl: Map<String, List<HttpMappingInfo>>,
    val version: Long,
)
