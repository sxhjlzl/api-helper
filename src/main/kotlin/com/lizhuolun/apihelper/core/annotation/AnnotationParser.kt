package com.lizhuolun.apihelper.core.annotation

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.lizhuolun.apihelper.core.HttpMethod

/**
 * 注解识别与属性提取工具。
 *
 * 所有 hasAnnotation 调用都走 PsiModifierList.hasAnnotation(FQN)，
 * IDEA 内部会缓存查询结果，性能远高于自行遍历 annotations。
 */
object AnnotationParser {

    // ============ 类型识别 ============

    /**
     * 是否为 OpenFeign 客户端接口。
     */
    fun isFeignInterface(psiClass: PsiClass): Boolean {
        if (!psiClass.isInterface) return false
        val modifierList = psiClass.modifierList ?: return false
        return modifierList.hasAnnotation(SpringAnnotations.FEIGN_CLIENT)
    }

    /**
     * 是否为 Spring 6 @HttpExchange 声明式客户端接口。
     */
    fun isHttpExchangeInterface(psiClass: PsiClass): Boolean {
        if (!psiClass.isInterface) return false
        val modifierList = psiClass.modifierList ?: return false
        return modifierList.hasAnnotation(SpringAnnotations.HTTP_EXCHANGE)
    }

    /**
     * 是否为客户端接口（Feign 或 HttpExchange）。
     */
    fun isClientInterface(psiClass: PsiClass): Boolean =
        isFeignInterface(psiClass) || isHttpExchangeInterface(psiClass)

    /**
     * 是否为 Spring MVC Controller / RestController。
     * 注意：Controller 必须是 class 而不是 interface。
     */
    fun isControllerClass(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface) return false
        val modifierList = psiClass.modifierList ?: return false
        return modifierList.hasAnnotation(SpringAnnotations.REST_CONTROLLER) ||
                modifierList.hasAnnotation(SpringAnnotations.CONTROLLER)
    }

    // ============ 方法上的 REST 注解定位 ============

    /**
     * 查找方法上的 REST 注解（RequestMapping 全家 或 HttpExchange 全家）。
     *
     * Gutter 必须挂在该注解元素上而非方法签名上，
     * 这样在用户敲回车时 PSI 不会丢失 Gutter（注解节点比方法节点稳定）。
     */
    fun findRestfulAnnotation(method: PsiMethod): PsiAnnotation? {
        for (annotation in method.annotations) {
            val fqn = annotation.qualifiedName ?: continue
            if (SpringAnnotations.METHOD_ANNOTATION_TO_HTTP.containsKey(fqn)) {
                return annotation
            }
        }
        return null
    }

    // ============ 类级路径提取 ============

    /**
     * 提取类级别声明的 URL 前缀。
     *
     * - Controller 类读 @RequestMapping 的 value/path
     * - Feign 接口读 @FeignClient 的 path（不读 value/name，因为它们是服务名）
     * - HttpExchange 接口读 @HttpExchange 的 value/url
     */
    fun extractClassLevelPath(psiClass: PsiClass): String {
        val modifierList = psiClass.modifierList ?: return ""

        modifierList.findAnnotation(SpringAnnotations.REQUEST_MAPPING)?.let {
            return readAnnotationAttribute(it, "value")
                ?: readAnnotationAttribute(it, "path")
                ?: ""
        }
        modifierList.findAnnotation(SpringAnnotations.FEIGN_CLIENT)?.let {
            return readAnnotationAttribute(it, "path") ?: ""
        }
        modifierList.findAnnotation(SpringAnnotations.HTTP_EXCHANGE)?.let {
            return readAnnotationAttribute(it, "value")
                ?: readAnnotationAttribute(it, "url")
                ?: ""
        }
        return ""
    }

    // ============ 方法级路径与 HTTP 方法提取 ============

    /**
     * 提取注解的 value 或 path 属性，作为方法级 URL 路径片段。
     */
    fun extractMethodPath(annotation: PsiAnnotation): String {
        return readAnnotationAttribute(annotation, "value")
            ?: readAnnotationAttribute(annotation, "path")
            ?: ""
    }

    /**
     * 提取注解对应的 HTTP 方法。
     *
     * - @GetMapping/@PostMapping 等直接由 FQN 决定
     * - @RequestMapping 看 method 属性（PsiReferenceExpression 引用 RequestMethod 枚举）
     * - @HttpExchange 看 method 属性（字面量字符串）
     */
    fun extractHttpMethod(annotation: PsiAnnotation): HttpMethod {
        val fqn = annotation.qualifiedName ?: return HttpMethod.ANY
        val defaultMethod = SpringAnnotations.METHOD_ANNOTATION_TO_HTTP[fqn] ?: HttpMethod.ANY
        if (defaultMethod != HttpMethod.ANY) return defaultMethod

        val methodAttr = annotation.findAttributeValue("method") ?: return HttpMethod.ANY
        return when (methodAttr) {
            is PsiReferenceExpression -> HttpMethod.fromName(methodAttr.referenceName)
            is PsiLiteralExpression -> HttpMethod.fromName(methodAttr.value as? String)
            is PsiArrayInitializerMemberValue -> {
                val first = methodAttr.initializers.firstOrNull() ?: return HttpMethod.ANY
                if (first is PsiReferenceExpression) {
                    HttpMethod.fromName(first.referenceName)
                } else if (first is PsiLiteralExpression) {
                    HttpMethod.fromName(first.value as? String)
                } else HttpMethod.ANY
            }
            else -> HttpMethod.ANY
        }
    }

    // ============ 通用属性读取 ============

    /**
     * 读取注解的字符串属性，支持字面量、字符串常量引用、字符串数组的第一个元素。
     *
     * 返回 null 表示属性不存在或类型无法识别（调用方需要继续尝试别名）。
     */
    private fun readAnnotationAttribute(annotation: PsiAnnotation, attributeName: String): String? {
        val rawValue = annotation.findAttributeValue(attributeName) ?: return null
        return resolveStringMemberValue(rawValue)
    }

    /**
     * 把任意 PsiAnnotationMemberValue 解析成字符串。
     * 字符串常量引用、PsiField 初始化器、数组首元素都会被尝试解析。
     */
    private fun resolveStringMemberValue(value: PsiAnnotationMemberValue): String? {
        return when (value) {
            is PsiLiteralExpression -> value.value as? String
            is PsiReferenceExpression -> {
                val resolved = value.resolve()
                if (resolved is PsiField) {
                    resolved.computeConstantValue() as? String
                } else null
            }
            is PsiArrayInitializerMemberValue -> {
                val first = value.initializers.firstOrNull() ?: return null
                resolveStringMemberValue(first)
            }
            else -> null
        }
    }
}
