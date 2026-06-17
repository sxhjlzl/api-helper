package com.lizhuolun.apihelper.core

import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

/**
 * HTTP 映射元数据，对应一个 Feign / Controller / HttpExchange 方法。
 *
 * 重要：qualifier 等需要 PSI 读取才能计算的字段，必须在构造 HttpMappingInfo 时
 * （仍处于 read action 内）一次性算好，不再以 getter 形式延迟访问 PSI，
 * 避免后续在协程外触发 "Read access is allowed from inside read-action only" 异常。
 *
 * @property url 已经拼接好的完整 URL（不含 host），例如 /api/user/info
 * @property httpMethod HTTP 方法
 * @property methodPointer 关联方法的智能指针；解析指针时调用方必须自行持有 read action
 * @property kind 来源类型，决定后续匹配的对端
 * @property qualifier 类全限定名 + # + 方法名，用于缓存去重，构造时一次性计算
 * @author lizhuolun
 * @date 2026/6/9
 */
data class HttpMappingInfo(
    val url: String,
    val httpMethod: HttpMethod,
    val methodPointer: SmartPsiElementPointer<PsiMethod>,
    val kind: EndpointKind,
    val qualifier: String,
) {

    /**
     * 解析当前有效的 PsiMethod。
     *
     * @return 指针对应的方法；方法已删除或失效时返回 null
     */
    fun resolveMethod(): PsiMethod? = methodPointer.element?.takeIf { it.isValid }

    /**
     * 判断两个映射是否可以匹配，HTTP 方法兼容 + URL 完全一致。
     *
     * @param other 另一侧映射
     * @return 两侧 URL 相同且 HTTP 方法兼容时返回 true
     */
    fun matches(other: HttpMappingInfo): Boolean {
        if (url != other.url) return false
        return httpMethod == HttpMethod.ANY ||
                other.httpMethod == HttpMethod.ANY ||
                httpMethod == other.httpMethod
    }

    companion object {

        /**
         * 计算方法的 qualifier，全局统一使用 "类全限定名#方法名(参数类型)" 形式。
         * 参数签名用于区分 Java/Kotlin 重载方法。
         * 必须在 read action 内调用。
         *
         * @param method 目标方法
         * @return 该方法的 qualifier，类不可解析时使用文件路径或类名作为 owner
         */
        fun qualifierOf(method: PsiMethod): String {
            val owner = method.containingClass?.qualifiedName
                ?: method.containingFile?.virtualFile?.path
                ?: method.containingClass?.name
                .orEmpty()
            val parameterTypes = method.parameterList.parameters.joinToString(",") {
                it.type.canonicalText
            }
            return "$owner#${method.name}($parameterTypes)"
        }

        /**
         * 工厂方法，用 PsiMethod 构造 HttpMappingInfo，必须在 read action 内调用。
         * 这里集中计算 qualifier，避免在使用侧重复处理 PSI。
         *
         * @param url 已拼接好的完整 URL
         * @param httpMethod HTTP 方法
         * @param method 关联方法
         * @param kind 端点类别
         * @return 使用智能指针持有方法引用的 HttpMappingInfo
         */
        fun create(
            url: String,
            httpMethod: HttpMethod,
            method: PsiMethod,
            kind: EndpointKind,
        ): HttpMappingInfo = HttpMappingInfo(
            url = url,
            httpMethod = httpMethod,
            methodPointer = SmartPointerManager.getInstance(method.project)
                .createSmartPsiElementPointer(method),
            kind = kind,
            qualifier = qualifierOf(method),
        )
    }
}

/**
 * 接口类别，决定 LineMarker 应该指向哪一侧。
 */
enum class EndpointKind {
    /**
     * OpenFeign 客户端接口
     **/
    FEIGN,

    /**
     * Spring 6 @HttpExchange 声明式 HTTP 客户端
     **/
    HTTP_EXCHANGE,

    /**
     * Spring MVC RestController / Controller
     **/
    CONTROLLER,
    ;

    /**
     * 客户端类型（Feign 或 HttpExchange）查找的对端始终是 Controller，
     * Controller 查找的对端是任何客户端类型。
     *
     * @return 是否为客户端侧端点
     */
    fun isClientSide(): Boolean = this != CONTROLLER
}
