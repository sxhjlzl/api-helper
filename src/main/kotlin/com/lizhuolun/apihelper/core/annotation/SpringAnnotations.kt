package com.lizhuolun.apihelper.core.annotation

import com.lizhuolun.apihelper.core.HttpMethod

/**
 * Spring 生态常用注解的全限定名集中存放。
 *
 * 注意：FQN 在 Spring Boot 2 -> 3 升级 + Spring Cloud 2024.x 中均未变化，
 * 仅依赖路径从 javax.* 切换到 jakarta.*，对插件识别无影响。
 */
object SpringAnnotations {

    // ------------------- 客户端类级注解 -------------------

    /** Spring Cloud OpenFeign 客户端 */
    const val FEIGN_CLIENT = "org.springframework.cloud.openfeign.FeignClient"

    /** Spring 6 声明式 HTTP 客户端 */
    const val HTTP_EXCHANGE = "org.springframework.web.service.annotation.HttpExchange"

    // ------------------- 服务端类级注解 -------------------

    const val CONTROLLER = "org.springframework.stereotype.Controller"
    const val REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"

    // ------------------- 方法级 RequestMapping 全家 -------------------

    const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"
    const val GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping"
    const val POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping"
    const val PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping"
    const val DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping"
    const val PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping"

    // ------------------- 方法级 HttpExchange 全家 -------------------

    const val GET_EXCHANGE = "org.springframework.web.service.annotation.GetExchange"
    const val POST_EXCHANGE = "org.springframework.web.service.annotation.PostExchange"
    const val PUT_EXCHANGE = "org.springframework.web.service.annotation.PutExchange"
    const val DELETE_EXCHANGE = "org.springframework.web.service.annotation.DeleteExchange"
    const val PATCH_EXCHANGE = "org.springframework.web.service.annotation.PatchExchange"

    // ------------------- 参数级注解 -------------------

    const val PATH_VARIABLE = "org.springframework.web.bind.annotation.PathVariable"
    const val REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam"
    const val REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader"
    const val COOKIE_VALUE = "org.springframework.web.bind.annotation.CookieValue"
    const val REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody"

    // ------------------- 方法级注解 FQN -> 默认 HttpMethod 映射 -------------------

    /**
     * 方法级注解 FQN 到 HTTP 方法的默认映射。
     *
     * REQUEST_MAPPING / HTTP_EXCHANGE 没有内置 HTTP 方法，需要进一步看 method 属性，
     * 这里默认是 ANY。
     */
    val METHOD_ANNOTATION_TO_HTTP: Map<String, HttpMethod> = mapOf(
        REQUEST_MAPPING to HttpMethod.ANY,
        GET_MAPPING to HttpMethod.GET,
        POST_MAPPING to HttpMethod.POST,
        PUT_MAPPING to HttpMethod.PUT,
        DELETE_MAPPING to HttpMethod.DELETE,
        PATCH_MAPPING to HttpMethod.PATCH,
        HTTP_EXCHANGE to HttpMethod.ANY,
        GET_EXCHANGE to HttpMethod.GET,
        POST_EXCHANGE to HttpMethod.POST,
        PUT_EXCHANGE to HttpMethod.PUT,
        DELETE_EXCHANGE to HttpMethod.DELETE,
        PATCH_EXCHANGE to HttpMethod.PATCH,
    )

    /**
     * 所有方法级 REST 注解 FQN，按数组返回便于在 PsiModifierList.hasAnnotation 中遍历。
     */
    val ALL_METHOD_ANNOTATIONS: Array<String> = METHOD_ANNOTATION_TO_HTTP.keys.toTypedArray()
}
