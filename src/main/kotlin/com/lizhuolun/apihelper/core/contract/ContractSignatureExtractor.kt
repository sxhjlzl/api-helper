package com.lizhuolun.apihelper.core.contract

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.annotation.AnnotationParser
import com.lizhuolun.apihelper.core.annotation.SpringAnnotations

/**
 * 从 PSI 方法提取契约签名。
 *
 * 设计约束：
 * 1. 本类只在契约体检流程中调用，不在 gutter 渲染或全工程扫描的热路径上；
 * 2. 所有方法都假定调用方已持有 read action，本类内部不再嵌套加锁；
 * 3. 无法识别的参数一律归为 [ContractParamKind.UNKNOWN]，宁可漏报也不误报。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
object ContractSignatureExtractor {

    /**
     * 简单类型白名单：这些类型在 Spring MVC 中不加注解也会被当作请求参数处理。
     */
    private val SIMPLE_TYPE_FQNS = setOf(
        "java.lang.String",
        "java.lang.CharSequence",
        "java.lang.Boolean",
        "java.lang.Byte",
        "java.lang.Short",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Float",
        "java.lang.Double",
        "java.lang.Character",
        "java.lang.Number",
        "java.math.BigDecimal",
        "java.math.BigInteger",
        "java.util.Date",
        "java.util.UUID",
        "java.util.Locale",
    )

    /**
     * 简单类型的短名集合，用于类型解析失败时的文本兜底。
     */
    private val SIMPLE_TYPE_NAMES = setOf(
        "String", "CharSequence", "Boolean", "Byte", "Short", "Integer", "Long",
        "Float", "Double", "Character", "Number", "BigDecimal", "BigInteger",
        "Date", "UUID", "Locale", "Object",
    )

    /**
     * 字段重命名注解：FQN 到读取名字的属性名。
     */
    private val FIELD_NAME_ANNOTATIONS = listOf(
        "com.fasterxml.jackson.annotation.JsonProperty" to "value",
        "com.google.gson.annotations.SerializedName" to "value",
        "com.alibaba.fastjson.annotation.JSONField" to "name",
        "com.alibaba.fastjson2.annotation.JSONField" to "name",
    )

    /**
     * 提取方法签名。
     *
     * @param method 目标方法，必须有效
     * @return 契约签名
     */
    fun extract(method: PsiMethod): ContractSignature {
        val params = ArrayList<ContractParam>()
        for (parameter in method.parameterList.parameters) {
            params += extractParameter(parameter, method)
        }
        return ContractSignature(
            qualifier = HttpMappingInfo.qualifierOf(method),
            params = params,
            bodyTypeText = params.firstOrNull { it.kind == ContractParamKind.BODY }?.typeText,
            returnTypeText = method.returnType?.canonicalText,
        )
    }

    /**
     * 提取单个参数的契约描述。
     *
     * @param parameter 方法参数
     * @param method 参数所属方法，用于判断客户端侧还是服务端侧
     * @return 一个或多个契约参数（`@SpringQueryMap` 会展开成多个）
     */
    private fun extractParameter(parameter: PsiParameter, method: PsiMethod): List<ContractParam> {
        val modifierList = parameter.modifierList
        if (modifierList != null) {
            if (modifierList.hasAnnotation(SpringAnnotations.REQUEST_BODY)) {
                return listOf(simpleParam(parameter, ContractParamKind.BODY))
            }
            if (modifierList.hasAnnotation(REQUEST_PART)) {
                return listOf(annotatedParam(parameter, ContractParamKind.FORM, REQUEST_PART))
            }
            if (modifierList.hasAnnotation(SpringAnnotations.PATH_VARIABLE)) {
                return listOf(annotatedParam(parameter, ContractParamKind.PATH, SpringAnnotations.PATH_VARIABLE))
            }
            if (modifierList.hasAnnotation(SpringAnnotations.REQUEST_PARAM)) {
                return listOf(annotatedParam(parameter, ContractParamKind.QUERY, SpringAnnotations.REQUEST_PARAM))
            }
            if (modifierList.hasAnnotation(SpringAnnotations.REQUEST_HEADER)) {
                return listOf(annotatedParam(parameter, ContractParamKind.HEADER, SpringAnnotations.REQUEST_HEADER))
            }
            if (modifierList.hasAnnotation(SpringAnnotations.COOKIE_VALUE)) {
                return listOf(annotatedParam(parameter, ContractParamKind.COOKIE, SpringAnnotations.COOKIE_VALUE))
            }
            if (modifierList.hasAnnotation(SpringAnnotations.SPRING_QUERY_MAP) ||
                modifierList.hasAnnotation(SpringAnnotations.FEIGN_QUERY_MAP)
            ) {
                return expandQueryMap(parameter)
            }
        }

        // 无注解参数按 Spring 语义推断：客户端侧的 DTO 参数是请求体，
        // 服务端侧的简单类型参数等价于 @RequestParam。
        return when {
            isClientSide(method) && isDtoType(parameter.type) ->
                listOf(simpleParam(parameter, ContractParamKind.BODY))

            !isClientSide(method) && isSimpleType(parameter.type) ->
                listOf(simpleParam(parameter, ContractParamKind.QUERY))

            else -> listOf(simpleParam(parameter, ContractParamKind.UNKNOWN))
        }
    }

    /**
     * 构造不带注解名字的契约参数。
     */
    private fun simpleParam(parameter: PsiParameter, kind: ContractParamKind): ContractParam =
        ContractParam(
            kind = kind,
            explicitName = null,
            sourceName = parameter.name,
            required = true,
            typeText = parameter.type.canonicalText,
        )

    /**
     * 读取注解上的显式名字与 required 属性。
     *
     * 名字解析顺序为 `value` 再 `name`；两者都为空时返回 null，
     * 表示该参数依赖编译参数 `-parameters` 的隐式命名。
     */
    private fun annotatedParam(
        parameter: PsiParameter,
        kind: ContractParamKind,
        annotationFqn: String,
    ): ContractParam {
        val annotation = parameter.modifierList?.findAnnotation(annotationFqn)
        val explicitName = annotation?.let { readExplicitName(it) }
        val required = annotation?.let { readBooleanAttribute(it, "required") } ?: true
        // 只认源码里显式写出的 required 属性，注解缺省值不参与「必填性不一致」判定
        val requiredDeclared = annotation?.findDeclaredAttributeValue("required") != null
        return ContractParam(
            kind = kind,
            explicitName = explicitName,
            sourceName = parameter.name,
            required = required,
            typeText = parameter.type.canonicalText,
            requiredDeclared = requiredDeclared,
        )
    }

    /**
     * 展开 `@SpringQueryMap` / `@QueryMap` 参数的字段。
     *
     * 只展开一层，嵌套 DTO 不再递归，避免大对象产生噪音。
     */
    private fun expandQueryMap(parameter: PsiParameter): List<ContractParam> {
        val psiClass = resolveClass(parameter.type) ?: return listOf(simpleParam(parameter, ContractParamKind.UNKNOWN))
        return psiClass.fields
            .filter { !it.hasModifierProperty(PsiModifier.STATIC) }
            .map { field ->
                ContractParam(
                    kind = ContractParamKind.QUERY,
                    explicitName = serializedName(field),
                    sourceName = field.name,
                    required = isNonNull(field.type),
                    typeText = field.type.canonicalText,
                )
            }
    }

    /**
     * 读取参数的显式名字：先 `value` 再 `name`，空串视为未声明。
     */
    private fun readExplicitName(annotation: PsiAnnotation): String? {
        val value = readStringAttribute(annotation, "value")
        if (!value.isNullOrBlank()) return value
        val name = readStringAttribute(annotation, "name")
        return name?.takeIf { it.isNotBlank() }
    }

    /**
     * 读取字符串类型的注解属性，支持字面量、常量引用与数组首元素。
     */
    private fun readStringAttribute(annotation: PsiAnnotation, attributeName: String): String? {
        val raw = annotation.findAttributeValue(attributeName) ?: return null
        return resolveString(raw)
    }

    /**
     * 把注解属性值解析为字符串。
     */
    private fun resolveString(value: PsiAnnotationMemberValue): String? = when (value) {
        is PsiLiteralExpression -> value.value as? String
        is PsiReferenceExpression -> (value.resolve() as? PsiField)?.computeConstantValue() as? String
        is PsiArrayInitializerMemberValue -> value.initializers.firstOrNull()?.let { resolveString(it) }
        else -> null
    }

    /**
     * 读取布尔类型的注解属性。
     */
    private fun readBooleanAttribute(annotation: PsiAnnotation, attributeName: String): Boolean? =
        (annotation.findAttributeValue(attributeName) as? PsiLiteralExpression)?.value as? Boolean

    /**
     * 解析类型对应的 PSI 类。
     */
    private fun resolveClass(type: PsiType): PsiClass? =
        (type as? PsiClassType)?.resolve()?.takeIf { it.isValid }

    /**
     * 判断是否为简单类型：基本类型、包装类型、字符串、时间与数值类型及其数组。
     *
     * 类型解析失败时回退到类型文本判断，保证在未配置 JDK 的测试夹具中行为一致。
     */
    private fun isSimpleType(type: PsiType): Boolean {
        if (type is PsiPrimitiveType) return true
        if (type is PsiArrayType) return isSimpleType(type.componentType)
        val fqn = resolveClass(type)?.qualifiedName
        if (fqn != null) {
            return fqn in SIMPLE_TYPE_FQNS || fqn.startsWith("java.time.") || fqn.startsWith("java.math.")
        }
        return isSimpleTypeName(type.canonicalText) || type.canonicalText in SIMPLE_TYPE_FQNS
    }

    /**
     * 按类型文本判断是否为简单类型，用于类型解析失败时的兜底。
     */
    private fun isSimpleTypeName(text: String): Boolean =
        text.substringAfterLast('.') in SIMPLE_TYPE_NAMES

    /**
     * 判断是否为可被序列化为请求体的 DTO 类型。
     *
     * 排除 JDK / Kotlin / Spring 自带的类型，以及枚举与注解。
     */
    private fun isDtoType(type: PsiType): Boolean {
        if (type is PsiArrayType) return false
        if (isSimpleType(type)) return false
        val psiClass = resolveClass(type)
            ?: return !isSimpleTypeName(type.canonicalText)
        if (psiClass.isEnum || psiClass.isAnnotationType) return false
        val fqn = psiClass.qualifiedName ?: return false
        return !fqn.startsWith("java.") &&
                !fqn.startsWith("kotlin.") &&
                !fqn.startsWith("org.springframework.")
    }

    /**
     * 判断字段是否非空：Kotlin 可空类型与 `@Nullable` 注解视为可空。
     */
    private fun isNonNull(type: PsiType): Boolean =
        type.annotations.none { it.qualifiedName?.endsWith("Nullable") == true }

    /**
     * 判断方法是否声明在客户端接口中。
     */
    private fun isClientSide(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return AnnotationParser.isClientInterface(containingClass)
    }

    /**
     * 读取字段的序列化名字。
     */
    private fun serializedName(field: PsiField): String {
        val modifierList = field.modifierList
        if (modifierList != null) {
            for ((fqn, attribute) in FIELD_NAME_ANNOTATIONS) {
                val annotation = modifierList.findAnnotation(fqn) ?: continue
                val value = readStringAttribute(annotation, attribute)
                if (!value.isNullOrBlank()) return value
            }
        }
        return field.name
    }

    /**
     * `@RequestPart` 的 FQN。
     */
    private const val REQUEST_PART = "org.springframework.web.bind.annotation.RequestPart"
}