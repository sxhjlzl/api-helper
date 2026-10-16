package com.lizhuolun.apihelper.ui

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiArrayType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTypesUtil
import com.lizhuolun.apihelper.core.annotation.SpringAnnotations

/**
 * 端点参数解析器，将 PSI 参数转换为调试请求与接口文档可复用的参数描述。
 *
 * @author lizhuolun
 * @date 2026/6/24
 */
internal object EndpointParameterResolver {

    /**
     * 判断参数是否带有指定注解。
     *
     * @param parameter 方法参数
     * @param fqn 注解全限定名
     * @return 参数存在该注解时返回 true
     */
    fun hasAnnotation(parameter: PsiParameter, fqn: String): Boolean =
        parameter.modifierList?.hasAnnotation(fqn) == true

    /**
     * 判断参数是否为 Feign QueryMap 参数。
     *
     * @param parameter 方法参数
     * @return 参数存在 QueryMap 注解时返回 true
     */
    fun hasQueryMapAnnotation(parameter: PsiParameter): Boolean =
        hasAnnotation(parameter, SpringAnnotations.SPRING_QUERY_MAP) ||
                hasAnnotation(parameter, SpringAnnotations.FEIGN_QUERY_MAP)

    /**
     * 按注解名解析单个端点参数。
     *
     * @param parameter 方法参数
     * @param fqn 注解全限定名
     * @return 端点参数描述
     */
    fun annotatedParameter(parameter: PsiParameter, fqn: String): EndpointParameterDescriptor {
        val fallback = parameterName(parameter)
        val annotation = parameter.modifierList?.findAnnotation(fqn)
        val name = annotation?.let { readAnnotationString(it, "value") ?: readAnnotationString(it, "name") }
            ?.takeIf { it.isNotBlank() }
            ?: fallback
        val required = annotation?.let { readAnnotationBoolean(it, "required") } ?: true
        val defaultOrExample = annotation?.let { readAnnotationString(it, "defaultValue") }
            ?.takeIf { it.isNotBlank() && it != SPRING_DEFAULT_NONE }
            ?: annotationExampleValue(parameter)
            ?: sampleValue(parameter.type)
        return EndpointParameterDescriptor(
            name,
            parameter.type.canonicalText,
            required,
            defaultOrExample,
            parameterDescription(parameter),
        )
    }

    /**
     * 解析方法文档注释中的接口说明。
     *
     * @param method 接口方法
     * @return 接口说明
     */
    fun methodDescription(method: PsiMethod): String =
        docDescription(method)

    /**
     * 解析 Query 参数。复杂 DTO 参数会展开为字段，基础类型会保留参数本身。
     *
     * @param parameter 方法参数
     * @return Query 参数描述列表
     */
    fun queryParameters(parameter: PsiParameter): List<EndpointParameterDescriptor> {
        if (isIgnoredFrameworkParameter(parameter)) {
            return emptyList()
        }
        val fields = fieldParameters(parameter, required = false)
        if (fields.isNotEmpty()) {
            return fields
        }
        return listOf(parameterDescriptor(parameter))
    }

    /**
     * 解析 Body 参数。复杂 DTO 参数会展开为字段，基础类型会保留参数本身。
     *
     * @param parameter 方法参数
     * @param annotationFqn 参数注解全限定名
     * @return Body 参数描述列表
     */
    fun bodyParameters(
        parameter: PsiParameter,
        annotationFqn: String? = null,
    ): List<EndpointParameterDescriptor> {
        if (isIgnoredFrameworkParameter(parameter)) {
            return emptyList()
        }
        val fallback = annotationFqn?.let { annotatedParameter(parameter, it) } ?: parameterDescriptor(parameter)
        val fields = fieldParameters(parameter, required = false)
        if (fields.isNotEmpty()) {
            return fields
        }
        return listOf(fallback)
    }

    /**
     * 解析请求体参数。
     *
     * @param parameter 方法参数
     * @return 端点参数描述
     */
    fun parameterDescriptor(parameter: PsiParameter): EndpointParameterDescriptor =
        EndpointParameterDescriptor(
            name = parameterName(parameter),
            type = parameter.type.canonicalText,
            required = true,
            sampleValue = annotationExampleValue(parameter) ?: sampleValue(parameter.type),
            description = parameterDescription(parameter),
        )

    /**
     * 为请求体参数生成带缩进的 JSON 草稿，支持多层嵌套 DTO、泛型与集合类型，并识别 @JsonProperty 与 @Schema/@ApiModelProperty example。
     *
     * @param parameters 请求体候选参数列表
     * @return 格式化后的 JSON 字符串，无请求体字段时返回空串
     */
    fun buildJsonSampleForParameters(parameters: List<PsiParameter>): String {
        val activeParams = parameters.filterNot(::isIgnoredFrameworkParameter)
        if (activeParams.isEmpty()) return ""
        if (activeParams.size == 1) {
            val single = activeParams[0]
            val elementType = collectionElementType(single.type) ?: (single.type as? PsiArrayType)?.componentType
            if (elementType != null) {
                val itemJson = renderJsonTypeValue(elementType, emptyMap(), mutableSetOf(), depth = 1, explicitExample = null)
                return "[\n  $itemJson\n]"
            }
            if (isExpandableQueryType(single.type)) {
                return renderJsonObject(single.type, emptyMap(), mutableSetOf(), depth = 0)
            }
        }
        val entries = LinkedHashMap<String, String>()
        for (parameter in activeParams) {
            if (isExpandableQueryType(parameter.type)) {
                collectObjectJsonEntries(parameter.type, emptyMap(), mutableSetOf(), depth = 0, entries)
            } else {
                val name = parameterName(parameter)
                val example = annotationExampleValue(parameter)
                entries.putIfAbsent(name, renderJsonTypeValue(parameter.type, emptyMap(), mutableSetOf(), depth = 0, explicitExample = example))
            }
        }
        return formatJsonEntries(entries, depth = 0)
    }

    /**
     * 解析返回参数。复杂返回对象会展开为字段，泛型集合字段会继续展开一层元素字段。
     *
     * @param type 返回类型
     * @return 返回参数描述列表
     */
    fun responseParameters(type: PsiType?): List<EndpointParameterDescriptor> {
        if (type == null || type == PsiTypes.voidType()) {
            return emptyList()
        }
        val elementType = collectionElementType(type)
        val effectiveType = elementType ?: type
        if (!isExpandableQueryType(effectiveType)) {
            return listOf(EndpointParameterDescriptor("return", type.canonicalText, true, sampleValue(type)))
        }

        val classType = effectiveType as? PsiClassType ?: return emptyList()
        val psiClass = classType.resolveGenerics().element ?: return emptyList()
        val actualTypes = ownerTypeParameterMap(classType, psiClass)
        val firstActualType = classType.parameters.firstOrNull()?.takeUnless(::isTypeParameter)
            ?: ownerTypeArgumentFromText(classType, psiClass, 0)
        val prefix = if (elementType == null) "" else "items[]"
        val sourceCommentCache = mutableMapOf<String, Map<String, String>>()
        return psiClass.allFields
            .asSequence()
            .filter(::isQueryableField)
            .flatMap { field ->
                val serializedName = serializedFieldName(field)
                val displayName = if (prefix.isBlank()) serializedName else "$prefix.$serializedName"
                val actualFieldType = actualTypes[typeParameterName(field.type)] ?: field.type
                val descriptor = EndpointParameterDescriptor(
                    name = displayName,
                    type = actualFieldType.canonicalText,
                    required = false,
                    sampleValue = annotationExampleValue(field) ?: sampleValue(actualFieldType),
                    description = fieldDescription(field, sourceCommentCache),
                )
                val nestedElementType = responseCollectionElementType(actualFieldType, actualTypes, firstActualType)
                    ?: responseCollectionElementType(field.type, actualTypes, firstActualType)
                val nested = when {
                    nestedElementType != null -> responseObjectFields(nestedElementType, "$displayName[]", sourceCommentCache, depth = 1)
                    actualFieldType != field.type || isExpandableQueryType(actualFieldType) ->
                        responseObjectFields(actualFieldType, displayName, sourceCommentCache, depth = 1)
                    else -> emptyList()
                }
                sequenceOf(descriptor) + nested.asSequence()
            }
            .distinctBy { it.name }
            .toList()
    }

    private fun fieldParameters(
        parameter: PsiParameter,
        required: Boolean,
    ): List<EndpointParameterDescriptor> {
        if (!isExpandableQueryType(parameter.type)) {
            return emptyList()
        }
        val sourceCommentCache = mutableMapOf<String, Map<String, String>>()
        return expandTypeFields(parameter.type, prefix = "", required = required, sourceCommentCache = sourceCommentCache, visited = mutableSetOf(), depth = 0)
            .distinctBy { it.name }
            .toList()
    }

    private fun expandTypeFields(
        type: PsiType,
        prefix: String,
        required: Boolean,
        sourceCommentCache: MutableMap<String, Map<String, String>>,
        visited: MutableSet<String>,
        depth: Int,
    ): List<EndpointParameterDescriptor> {
        if (depth > MAX_NESTED_DEPTH || !isExpandableQueryType(type)) {
            return emptyList()
        }
        val classType = type as? PsiClassType ?: return emptyList()
        val psiClass = classType.resolveGenerics().element ?: PsiTypesUtil.getPsiClass(type) ?: return emptyList()
        val qName = psiClass.qualifiedName ?: psiClass.name ?: return emptyList()
        if (!visited.add(qName)) return emptyList()
        try {
            val actualTypes = ownerTypeParameterMap(classType, psiClass)
            val firstActualType = classType.parameters.firstOrNull()?.takeUnless(::isTypeParameter)
                ?: ownerTypeArgumentFromText(classType, psiClass, 0)
            return psiClass.allFields
                .asSequence()
                .filter(::isQueryableField)
                .flatMap { field ->
                    val fieldName = serializedFieldName(field)
                    val displayName = if (prefix.isBlank()) fieldName else "$prefix.$fieldName"
                    val actualFieldType = actualTypes[typeParameterName(field.type)] ?: field.type
                    val descriptor = EndpointParameterDescriptor(
                        name = displayName,
                        type = actualFieldType.canonicalText,
                        required = required,
                        sampleValue = annotationExampleValue(field) ?: sampleValue(actualFieldType),
                        description = fieldDescription(field, sourceCommentCache),
                    )
                    val nestedCollectionElem = responseCollectionElementType(actualFieldType, actualTypes, firstActualType)
                        ?: responseCollectionElementType(field.type, actualTypes, firstActualType)
                    val nested = when {
                        depth >= MAX_NESTED_DEPTH -> emptyList()
                        nestedCollectionElem != null && isExpandableQueryType(nestedCollectionElem) ->
                            expandTypeFields(nestedCollectionElem, "$displayName[]", false, sourceCommentCache, visited, depth + 1)
                        isExpandableQueryType(actualFieldType) ->
                            expandTypeFields(actualFieldType, displayName, false, sourceCommentCache, visited, depth + 1)
                        else -> emptyList()
                    }
                    sequenceOf(descriptor) + nested.asSequence()
                }
                .toList()
        } finally {
            visited.remove(qName)
        }
    }

    private fun responseObjectFields(
        type: PsiType,
        prefix: String,
        sourceCommentCache: MutableMap<String, Map<String, String>>,
        depth: Int = 1,
    ): List<EndpointParameterDescriptor> {
        return expandTypeFields(
            type = type,
            prefix = prefix,
            required = false,
            sourceCommentCache = sourceCommentCache,
            visited = mutableSetOf(),
            depth = depth,
        )
    }

    private fun responseCollectionElementType(
        type: PsiType,
        actualTypes: Map<String, PsiType>,
        firstActualType: PsiType?,
    ): PsiType? {
        val elementType = collectionElementType(type) ?: return null
        return if (isTypeParameter(elementType)) {
            actualTypes[typeParameterName(elementType)] ?: firstActualType
        } else {
            elementType
        }
    }

    private fun ownerTypeParameterMap(
        ownerType: PsiClassType,
        ownerClass: PsiClass,
    ): Map<String, PsiType> =
        ownerClass.typeParameters
            .mapIndexedNotNull { index, parameter ->
                val name = parameter.name ?: return@mapIndexedNotNull null
                val ownerArgument = ownerType.parameters.getOrNull(index)
                val textArgument = ownerTypeArgumentFromText(ownerType, ownerClass, index)
                val argument = if (ownerArgument == null || typeParameterName(ownerArgument) == name) {
                    textArgument ?: ownerArgument
                } else {
                    ownerArgument
                }
                argument?.let { name to it }
            }
            .toMap()

    private fun ownerTypeArgumentFromText(
        ownerType: PsiClassType,
        ownerClass: PsiClass,
        index: Int,
    ): PsiType? {
        val argumentText = ownerType.canonicalText
            .substringAfter('<', "")
            .substringBeforeLast('>', "")
            .split(',')
            .getOrNull(index)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            JavaPsiFacade.getElementFactory(ownerClass.project).createTypeFromText(argumentText, ownerClass)
        }.getOrNull()
    }

    private fun typeParameterName(type: PsiType): String =
        (PsiTypesUtil.getPsiClass(type) as? PsiTypeParameter)?.name
            ?: type.presentableText.substringAfterLast('.')

    private fun isTypeParameter(type: PsiType): Boolean =
        PsiTypesUtil.getPsiClass(type) is PsiTypeParameter

    private fun collectionElementType(type: PsiType): PsiType? {
        val classType = type as? PsiClassType ?: return null
        val qualifiedName = classType.resolve()?.qualifiedName
        val rawCanonical = type.canonicalText.substringBefore('<')
        val rawPresentable = type.presentableText.substringBefore('<')
        val isCollectionType = qualifiedName in COLLECTION_TYPES ||
                rawCanonical in COLLECTION_TYPES ||
                rawPresentable in COLLECTION_SHORT_NAMES
        if (!isCollectionType) {
            return null
        }
        return classType.parameters.firstOrNull()
    }

    private fun isQueryableField(field: PsiField): Boolean {
        val name = field.name
        return name.isNotBlank() &&
                name != SERIAL_VERSION_UID &&
                name.contains('$').not() &&
                field.hasModifierProperty(PsiModifier.STATIC).not() &&
                field.hasModifierProperty(PsiModifier.TRANSIENT).not()
    }

    private fun isExpandableQueryType(type: PsiType): Boolean {
        if (type is PsiPrimitiveType || type is PsiArrayType) {
            return false
        }
        val psiClass = PsiTypesUtil.getPsiClass(type) ?: return false
        val qualifiedName = psiClass.qualifiedName ?: return false
        if (qualifiedName in NON_EXPANDABLE_TYPES) {
            return false
        }
        return NON_EXPANDABLE_PACKAGE_PREFIXES.none { qualifiedName.startsWith(it) }
    }

    private fun isIgnoredFrameworkParameter(parameter: PsiParameter): Boolean {
        val psiClass = PsiTypesUtil.getPsiClass(parameter.type) ?: return false
        val qualifiedName = psiClass.qualifiedName ?: return false
        if (qualifiedName in IGNORED_PARAMETER_TYPES) {
            return true
        }
        return IGNORED_PARAMETER_PACKAGE_PREFIXES.any { qualifiedName.startsWith(it) }
    }

    private fun parameterName(parameter: PsiParameter): String =
        parameter.name.takeIf { it.isNotBlank() } ?: DEFAULT_PARAMETER_NAME

    private fun parameterDescription(parameter: PsiParameter): String {
        val method = parameter.declarationScope as? PsiMethod ?: return ""
        return docParamDescriptions(method)[parameter.name].orEmpty()
            .ifBlank { annotationDescription(parameter) }
    }

    private fun fieldDescription(
        field: PsiField,
        sourceCommentCache: MutableMap<String, Map<String, String>>? = null,
    ): String {
        val candidates = listOf(field, field.navigationElement, field.originalElement).distinct()
        return candidates
            .asSequence()
            .mapNotNull { it as? PsiDocCommentOwner }
            .map { docDescription(it) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .ifBlank { sourceFieldDescription(field, sourceCommentCache) }
            .ifBlank { propertyAccessorDescription(field) }
    }

    private fun propertyAccessorDescription(field: PsiField): String {
        val containingClass = field.containingClass ?: return ""
        val capitalizedName = field.name.replaceFirstChar { it.uppercase() }
        val candidates = listOf("get$capitalizedName", "is$capitalizedName")
        return candidates
            .asSequence()
            .flatMap { containingClass.findMethodsByName(it, false).asSequence() }
            .map { docDescription(it) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun docDescription(owner: PsiDocCommentOwner): String {
        val annotationDescription = annotationDescription(owner)
        if (annotationDescription.isNotBlank()) {
            return annotationDescription
        }
        val lines = docCommentLines(owner)
        return normalizeDocText(lines.takeWhile { it.startsWith("@").not() })
            .ifBlank { leadingCommentDescription(owner) }
    }

    private fun docParamDescriptions(method: PsiMethod): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var currentName: String? = null
        val currentText = mutableListOf<String>()

        fun flushCurrent() {
            val name = currentName ?: return
            val description = normalizeDocText(currentText)
            if (name.isNotBlank() && description.isNotBlank()) {
                result[name] = description
            }
            currentName = null
            currentText.clear()
        }

        for (line in docCommentLines(method)) {
            if (line.startsWith("@")) {
                flushCurrent()
                val tag = line.removePrefix("@").trim()
                if (tag.startsWith("param")) {
                    val content = tag.removePrefix("param").trim()
                    val parts = content.split(Regex("\\s+"), limit = 2)
                    currentName = parts.getOrNull(0)?.trim()?.takeUnless { it.startsWith("<") }
                    parts.getOrNull(1)?.let(currentText::add)
                }
            } else if (currentName != null) {
                currentText += line
            }
        }
        flushCurrent()

        return result
    }

    private fun docCommentLines(owner: PsiDocCommentOwner): List<String> {
        val text = owner.docComment?.text ?: return emptyList()
        return text.lineSequence()
            .map {
                it.trim()
                    .removePrefix("/**")
                    .removeSuffix("*/")
                    .removePrefix("*")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun normalizeDocText(lines: List<String>): String =
        lines.joinToString(" ")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun annotationDescription(owner: PsiElement): String {
        val modifierList = when (owner) {
            is PsiMethod -> owner.modifierList
            is PsiField -> owner.modifierList
            is PsiParameter -> owner.modifierList
            else -> null
        } ?: return ""
        return modifierList.annotations
            .asSequence()
            .mapNotNull { annotation ->
                val attributes = DESCRIPTION_ANNOTATION_ATTRIBUTES[annotation.qualifiedName].orEmpty()
                attributes.firstNotNullOfOrNull { readAnnotationString(annotation, it)?.takeIf(String::isNotBlank) }
            }
            .firstOrNull()
            .orEmpty()
    }

    private fun leadingCommentDescription(element: PsiElement): String {
        val elementComment = elementLeadingCommentDescription(element)
        if (elementComment.isNotBlank()) {
            return elementComment
        }
        val fileText = element.containingFile?.text ?: return ""
        val beforeElement = fileText.substring(0, element.textRange.startOffset)
        val collected = mutableListOf<String>()
        var insideBlockComment = false

        for (line in beforeElement.lineSequence().toList().asReversed()) {
            val trimmed = line.trim()
            when {
                trimmed.isBlank() && collected.isEmpty() -> continue
                insideBlockComment -> {
                    collected += trimmed
                    if (trimmed.startsWith("/*")) {
                        break
                    }
                }
                trimmed.startsWith("//") -> collected += trimmed
                trimmed.endsWith("*/") -> {
                    collected += trimmed
                    insideBlockComment = trimmed.startsWith("/*").not()
                    if (!insideBlockComment) {
                        break
                    }
                }
                else -> break
            }
        }
        return normalizeCommentText(collected.asReversed())
    }

    private fun sourceFieldDescription(
        field: PsiField,
        sourceCommentCache: MutableMap<String, Map<String, String>>?,
    ): String {
        val containingClass = field.containingClass ?: return ""
        val cacheKey = sourceCommentCacheKey(containingClass)
        if (sourceCommentCache != null && cacheKey != null) {
            return sourceCommentCache.getOrPut(cacheKey) {
                sourceFieldCommentMap(containingClass)
            }[field.name].orEmpty()
        }
        return sourceFieldCommentMap(containingClass)[field.name].orEmpty()
    }

    private fun sourceElementsFromClass(psiClass: com.intellij.psi.PsiClass): List<PsiElement> =
        listOfNotNull(psiClass, psiClass.navigationElement, psiClass.originalElement).distinct()

    private fun sourceCommentCacheKey(psiClass: com.intellij.psi.PsiClass): String? {
        val filePath = psiClass.containingFile?.virtualFile?.path ?: return null
        return "$filePath#${psiClass.qualifiedName ?: psiClass.name.orEmpty()}"
    }

    private fun sourceFieldCommentMap(psiClass: com.intellij.psi.PsiClass): Map<String, String> {
        val result = linkedMapOf<String, String>()
        sourceElementsFromClass(psiClass)
            .asSequence()
            .mapNotNull { it.containingFile?.text }
            .forEach { source ->
                for (match in FIELD_COMMENT_REGEX.findAll(source)) {
                    val name = match.groupValues[2]
                    if (name.isNotBlank() && name !in result) {
                        result[name] = normalizeSourceComment(match.groupValues[1])
                    }
                }
            }
        return result
    }

    private fun elementLeadingCommentDescription(element: PsiElement): String {
        val collected = mutableListOf<String>()
        for (line in element.text.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.isBlank() && collected.isEmpty() -> continue
                trimmed.startsWith("@") -> continue
                trimmed.startsWith("//") ||
                        trimmed.startsWith("/*") ||
                        trimmed.startsWith("*") ||
                        trimmed.endsWith("*/") -> collected += trimmed
                else -> break
            }
        }
        return normalizeCommentText(collected)
    }

    private fun normalizeCommentText(comments: List<String>): String =
        normalizeDocText(comments.flatMap { comment ->
            comment.lineSequence().map {
                it.trim()
                    .removePrefix("/**")
                    .removePrefix("/*")
                    .removePrefix("//")
                    .removeSuffix("*/")
                    .removePrefix("*")
                    .trim()
            }.filter { it.isNotBlank() }
        })

    private fun normalizeSourceComment(comment: String): String =
        normalizeCommentText(listOf(comment))

    private fun sampleValue(type: PsiType): String {
        return when (type.canonicalText) {
            "boolean",
            "Boolean",
            "java.lang.Boolean",
            "kotlin.Boolean",
            -> "true"
            "int",
            "long",
            "short",
            "byte",
            "double",
            "float",
            "Integer",
            "Long",
            "Short",
            "Byte",
            "Double",
            "Float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Double",
            "java.lang.Float",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Short",
            "kotlin.Byte",
            "kotlin.Double",
            "kotlin.Float",
            -> "0"
            else -> ""
        }
    }

    private fun readAnnotationString(annotation: PsiAnnotation, attributeName: String): String? {
        val rawValue = annotation.findAttributeValue(attributeName) ?: return null
        return resolveStringMemberValue(rawValue)
    }

    private fun resolveStringMemberValue(value: PsiAnnotationMemberValue): String? {
        return when (value) {
            is PsiLiteralExpression -> value.value as? String
            is PsiReferenceExpression -> (value.resolve() as? PsiField)?.computeConstantValue() as? String
            is PsiArrayInitializerMemberValue -> value.initializers.firstOrNull()?.let(::resolveStringMemberValue)
            else -> null
        }
    }

    private fun readAnnotationBoolean(annotation: PsiAnnotation, attributeName: String): Boolean? {
        val value = annotation.findAttributeValue(attributeName) ?: return null
        return (value as? PsiLiteralExpression)?.value as? Boolean
    }

    /**
     * 提取字段的 JSON 序列化名称，优先读取 @JsonProperty 的 value。
     */
    private fun serializedFieldName(field: PsiField): String {
        val modifierList = field.modifierList ?: return field.name
        for (fqn in JSON_NAME_ANNOTATIONS) {
            val annotation = modifierList.findAnnotation(fqn) ?: continue
            val customName = (readAnnotationString(annotation, "value") ?: readAnnotationString(annotation, "name"))
                ?.takeIf { it.isNotBlank() }
            if (customName != null) return customName
        }
        return field.name
    }

    /**
     * 提取字段或参数上 Swagger / OpenAPI 注解声明的 example 值。
     */
    private fun annotationExampleValue(owner: PsiElement): String? {
        val modifierList = when (owner) {
            is PsiField -> owner.modifierList
            is PsiParameter -> owner.modifierList
            else -> null
        } ?: return null
        return modifierList.annotations
            .asSequence()
            .mapNotNull { annotation ->
                val attrs = EXAMPLE_ANNOTATION_ATTRIBUTES[annotation.qualifiedName].orEmpty()
                attrs.firstNotNullOfOrNull { attr ->
                    readAnnotationString(annotation, attr)?.takeIf { it.isNotBlank() }
                }
            }
            .firstOrNull()
    }

    private fun renderJsonObject(
        type: PsiType,
        parentTypeMap: Map<String, PsiType>,
        visited: MutableSet<String>,
        depth: Int,
    ): String {
        val entries = LinkedHashMap<String, String>()
        collectObjectJsonEntries(type, parentTypeMap, visited, depth, entries)
        return formatJsonEntries(entries, depth)
    }

    private fun collectObjectJsonEntries(
        type: PsiType,
        parentTypeMap: Map<String, PsiType>,
        visited: MutableSet<String>,
        depth: Int,
        out: MutableMap<String, String>,
    ) {
        if (depth > MAX_NESTED_DEPTH || !isExpandableQueryType(type)) return
        val classType = type as? PsiClassType ?: return
        val psiClass = classType.resolveGenerics().element ?: PsiTypesUtil.getPsiClass(type) ?: return
        val qName = psiClass.qualifiedName ?: psiClass.name ?: return
        if (!visited.add(qName)) return
        try {
            val actualTypes = parentTypeMap + ownerTypeParameterMap(classType, psiClass)
            val firstActualType = classType.parameters.firstOrNull()?.takeUnless(::isTypeParameter)
                ?: ownerTypeArgumentFromText(classType, psiClass, 0)
            for (field in psiClass.allFields) {
                if (!isQueryableField(field)) continue
                val key = serializedFieldName(field)
                if (key in out) continue
                val actualFieldType = actualTypes[typeParameterName(field.type)] ?: field.type
                val example = annotationExampleValue(field)
                val nestedCollectionElem = responseCollectionElementType(actualFieldType, actualTypes, firstActualType)
                    ?: responseCollectionElementType(field.type, actualTypes, firstActualType)
                val valueJson = when {
                    nestedCollectionElem != null -> {
                        if (depth < MAX_NESTED_DEPTH) {
                            val elemJson = renderJsonTypeValue(nestedCollectionElem, actualTypes, visited, depth + 1, explicitExample = null)
                            "[$elemJson]"
                        } else {
                            "[]"
                        }
                    }
                    else -> renderJsonTypeValue(actualFieldType, actualTypes, visited, depth + 1, explicitExample = example)
                }
                out[key] = valueJson
            }
        } finally {
            visited.remove(qName)
        }
    }

    private fun renderJsonTypeValue(
        type: PsiType,
        actualTypes: Map<String, PsiType>,
        visited: MutableSet<String>,
        depth: Int,
        explicitExample: String?,
    ): String {
        if (!explicitExample.isNullOrBlank()) {
            return formatJsonScalar(type, explicitExample)
        }
        val arrayComponent = (type as? PsiArrayType)?.componentType ?: collectionElementType(type)
        if (arrayComponent != null) {
            if (depth > MAX_NESTED_DEPTH) return "[]"
            val resolvedElem = actualTypes[typeParameterName(arrayComponent)] ?: arrayComponent
            val itemJson = renderJsonTypeValue(resolvedElem, actualTypes, visited, depth + 1, explicitExample = null)
            return "[$itemJson]"
        }
        val psiClass = PsiTypesUtil.getPsiClass(type)
        if (psiClass?.isEnum == true) {
            val firstConstant = psiClass.fields.firstOrNull { it.javaClass.simpleName.contains("EnumConstant") }?.name
            return "\"${escapeJsonString(firstConstant.orEmpty())}\""
        }
        if (depth <= MAX_NESTED_DEPTH && isExpandableQueryType(type)) {
            return renderJsonObject(type, actualTypes, visited, depth)
        }
        return formatJsonScalar(type, sampleValue(type))
    }

    private fun formatJsonEntries(entries: Map<String, String>, depth: Int): String {
        if (entries.isEmpty()) return "{}"
        val indent = "  ".repeat(depth)
        val childIndent = "  ".repeat(depth + 1)
        return entries.entries.joinToString(
            separator = ",\n",
            prefix = "{\n",
            postfix = "\n$indent}",
        ) { (key, value) ->
            "$childIndent\"${escapeJsonString(key)}\": $value"
        }
    }

    private fun formatJsonScalar(type: PsiType, rawValue: String): String {
        val sample = sampleValue(type)
        if (sample == "true" || sample == "false") {
            return if (rawValue.equals("false", ignoreCase = true)) "false" else "true"
        }
        if (sample == "0") {
            return if (rawValue.toBigDecimalOrNull() != null) rawValue else "0"
        }
        return "\"${escapeJsonString(rawValue)}\""
    }

    private fun escapeJsonString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")

    private const val MAX_NESTED_DEPTH = 2
    private const val DEFAULT_PARAMETER_NAME = "param"
    private const val SERIAL_VERSION_UID = "serialVersionUID"
    private const val SPRING_DEFAULT_NONE = "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n"

    private val JSON_NAME_ANNOTATIONS = listOf(
        "com.fasterxml.jackson.annotation.JsonProperty",
        "com.google.gson.annotations.SerializedName",
        "com.alibaba.fastjson.annotation.JSONField",
        "com.alibaba.fastjson2.annotation.JSONField",
    )

    private val EXAMPLE_ANNOTATION_ATTRIBUTES = mapOf(
        "io.swagger.v3.oas.annotations.Parameter" to listOf("example"),
        "io.swagger.v3.oas.annotations.media.Schema" to listOf("example", "defaultValue"),
        "io.swagger.annotations.ApiModelProperty" to listOf("example"),
        "io.swagger.annotations.ApiParam" to listOf("example", "defaultValue"),
    )
    private val FIELD_COMMENT_REGEX = Regex(
        """(?s)(/\*\*.*?\*/|/\*.*?\*/|(?://[^\n]*\n\s*)+)\s*(?:@\w[\w.]*\s*(?:\([^)]*\))?\s*)*(?:private|protected|public)?\s*(?:final\s+)?[\w.<>, ?\[\]]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:[=;])""",
    )

    private val DESCRIPTION_ANNOTATION_ATTRIBUTES = mapOf(
        "io.swagger.v3.oas.annotations.Operation" to listOf("summary", "description"),
        "io.swagger.annotations.ApiOperation" to listOf("value", "notes"),
        "io.swagger.v3.oas.annotations.Parameter" to listOf("description", "name"),
        "io.swagger.v3.oas.annotations.media.Schema" to listOf("description", "title", "name"),
        "io.swagger.annotations.ApiModelProperty" to listOf("value", "notes", "name"),
    )

    private val NON_EXPANDABLE_TYPES = setOf(
        "java.lang.String",
        "java.lang.Object",
        "java.util.Date",
        "java.util.UUID",
        "java.math.BigDecimal",
        "java.math.BigInteger",
        "kotlin.String",
        "kotlin.Any",
    )

    private val NON_EXPANDABLE_PACKAGE_PREFIXES = listOf(
        "java.",
        "javax.",
        "jakarta.",
        "kotlin.",
        "org.springframework.",
        "com.intellij.",
    )

    private val IGNORED_PARAMETER_TYPES = setOf(
        "java.security.Principal",
        "org.springframework.ui.Model",
        "org.springframework.ui.ModelMap",
        "org.springframework.validation.BindingResult",
    )

    private val IGNORED_PARAMETER_PACKAGE_PREFIXES = listOf(
        "javax.servlet.",
        "jakarta.servlet.",
        "org.springframework.ui.",
        "org.springframework.validation.",
        "org.springframework.web.context.request.",
    )

    private val COLLECTION_TYPES = setOf(
        "java.lang.Iterable",
        "java.util.Collection",
        "java.util.List",
        "java.util.Set",
        "kotlin.collections.Collection",
        "kotlin.collections.List",
        "kotlin.collections.Set",
    )

    private val COLLECTION_SHORT_NAMES = setOf(
        "Iterable",
        "Collection",
        "List",
        "Set",
    )
}

/**
 * 端点参数描述。
 *
 * @property name 参数名
 * @property type 参数类型
 * @property required 是否必填
 * @property sampleValue 示例值
 * @property description 参数说明
 */
internal data class EndpointParameterDescriptor(
    val name: String,
    val type: String,
    val required: Boolean,
    val sampleValue: String,
    val description: String = "",
)
