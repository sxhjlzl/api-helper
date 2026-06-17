package com.lizhuolun.apihelper.core

/**
 * HTTP 请求方法枚举。
 *
 * ANY 用于 @RequestMapping 未指定 method 时的兜底匹配，
 * 在 Feign / Controller 互相查找时视为通配。
 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, ANY;

    companion object {
        /**
         * 通过名称解析 HTTP 方法，未识别时返回 ANY。
         */
        fun fromName(name: String?): HttpMethod {
            if (name.isNullOrBlank()) return ANY
            return runCatching { valueOf(name.uppercase()) }.getOrDefault(ANY)
        }
    }
}
