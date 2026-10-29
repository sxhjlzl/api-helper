package com.lizhuolun.apihelper.ui.openapi

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.lizhuolun.apihelper.cache.BilateralMappingCacheService
import com.lizhuolun.apihelper.core.EndpointKind
import com.lizhuolun.apihelper.core.HttpMappingInfo
import com.lizhuolun.apihelper.core.HttpMethod
import com.lizhuolun.apihelper.core.annotation.SpringAnnotations
import com.lizhuolun.apihelper.ui.EndpointParameterDescriptor
import com.lizhuolun.apihelper.ui.EndpointParameterResolver

/**
 * OpenAPI 3.0 导出器：把全工程端点（Controller 与 Feign / HttpExchange）组装为 OpenAPI 文档。
 *
 * 数据来源是双边缓存快照与 [EndpointParameterResolver] 的参数提取结果，
 * 与接口树、调试面板、Markdown 文档共用同一份解析逻辑，保证四处口径一致。
 *
 * @author lizhuolun
 * @date 2026/10/29
 */
internal object OpenApiExporter {

    /**
     * 导出结果。
     *
     * @property json 格式化后的 OpenAPI JSON 文本
     * @property operationCount 导出的操作数（路径 × 方法）
     */
    data class ExportResult(
        val json: String,
        val operationCount: Int,
    )

    private val log = thisLogger()

    /**
     * 导出全工程端点为 OpenAPI 3.0 JSON。必须在允许访问 PSI 的上下文中调用，内部会包 read action。
     *
     * @param project 当前工程
     * @return 导出结果；工程已释放时返回 null
     */
    fun export(project: Project): ExportResult? {
        return ApplicationManager.getApplication().runReadAction(Computable {
            if (project.isDisposed) return@Computable null
            val snapshot = BilateralMappingCacheService.of(project).snapshot()
            val root = JsonObject()
            root.addProperty("openapi", "3.0.3")
            root.add("info", JsonObject().apply {
                addProperty("title", project.name)
                addProperty("version", "")
            })
            val paths = JsonObject()
            var operationCount = 0
            for (mapping in snapshot.controllers + snapshot.clients) {
                try {
                    val method = mapping.resolveMethod() ?: continue
                    if (addOperation(paths, mapping, method)) {
                        operationCount++
                    }
                } catch (e: Exception) {
                    log.warn("ApiHelper: OpenAPI 导出跳过端点, url=${mapping.url}", e)
                }
            }
            root.add("paths", paths)
            ExportResult(GsonBuilder().setPrettyPrinting().create().toJson(root), operationCount)
        })
    }

    /**
     * 把一个端点方法写入 paths 树；路径相同的不同端点合并到同一路径对象下。
     *
     * @return 成功写入返回 true（ANY 方法无法映射到 OpenAPI，跳过返回 false）
     */
    private fun addOperation(paths: JsonObject, mapping: HttpMappingInfo, method: PsiMethod): Boolean {
        if (mapping.httpMethod == HttpMethod.ANY) return false
        val pathKey = mapping.url.ifBlank { "/" }.let { if (it.startsWith("/")) it else "/$it" }
        val pathItem = paths.getAsJsonObject(pathKey) ?: JsonObject().also { paths.add(pathKey, it) }

        val operation = JsonObject()
        operation.add("tags", com.google.gson.JsonArray().apply {
            add(method.containingClass?.name ?: mapping.qualifier)
        })
        val summary = EndpointParameterResolver.methodDescription(method)
        if (summary.isNotBlank()) {
            operation.addProperty("summary", summary)
        }
        operation.addProperty("operationId", method.name)
        if (mapping.kind != EndpointKind.CONTROLLER) {
            operation.addProperty("x-endpoint-kind", mapping.kind.name)
        }

        val parameters = com.google.gson.JsonArray()
        val bodyFields = ArrayList<EndpointParameterDescriptor>()
        collectParameters(method, mapping.httpMethod, parameters, bodyFields)
        if (parameters.size() > 0) {
            operation.add("parameters", parameters)
        }
        if (bodyFields.isNotEmpty()) {
            operation.add("requestBody", JsonObject().apply {
                add("content", JsonObject().apply {
                    add("application/json", JsonObject().apply {
                        add("schema", fieldsSchema(bodyFields))
                    })
                })
            })
        }

        operation.add("responses", JsonObject().apply {
            add("200", JsonObject().apply {
                addProperty("description", "OK")
                val responseFields = EndpointParameterResolver.responseParameters(method.returnType)
                if (responseFields.isNotEmpty()) {
                    add("content", JsonObject().apply {
                        add("application/json", JsonObject().apply {
                            add("schema", fieldsSchema(responseFields))
                        })
                    })
                }
            })
        })

        pathItem.add(mapping.httpMethod.name.lowercase(), operation)
        return true
    }

    /**
     * 按 Spring 注解把方法参数分拣到 OpenAPI parameters 与请求体字段。
     */
    private fun collectParameters(
        method: PsiMethod,
        httpMethod: HttpMethod,
        parameters: com.google.gson.JsonArray,
        bodyFields: MutableList<EndpointParameterDescriptor>,
    ) {
        val useQueryForUnannotated = httpMethod == HttpMethod.GET ||
                httpMethod == HttpMethod.DELETE ||
                httpMethod == HttpMethod.HEAD
        for (parameter in method.parameterList.parameters) {
            when {
                EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.PATH_VARIABLE) ->
                    parameters.add(parameterObject(
                        EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.PATH_VARIABLE),
                        location = "path",
                        required = true,
                    ))
                EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_PARAM) ->
                    parameters.add(parameterObject(
                        EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.REQUEST_PARAM),
                        location = "query",
                    ))
                EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_HEADER) ->
                    parameters.add(parameterObject(
                        EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.REQUEST_HEADER),
                        location = "header",
                    ))
                EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.COOKIE_VALUE) ->
                    parameters.add(parameterObject(
                        EndpointParameterResolver.annotatedParameter(parameter, SpringAnnotations.COOKIE_VALUE),
                        location = "cookie",
                    ))
                EndpointParameterResolver.hasAnnotation(parameter, SpringAnnotations.REQUEST_BODY) ->
                    bodyFields += EndpointParameterResolver.bodyParameters(parameter, SpringAnnotations.REQUEST_BODY)
                EndpointParameterResolver.hasQueryMapAnnotation(parameter) ->
                    EndpointParameterResolver.queryParameters(parameter)
                        .filter { '.' !in it.name && '[' !in it.name }
                        .forEach { parameters.add(parameterObject(it, location = "query")) }
                useQueryForUnannotated ->
                    EndpointParameterResolver.queryParameters(parameter)
                        .filter { '.' !in it.name && '[' !in it.name }
                        .forEach { parameters.add(parameterObject(it, location = "query")) }
                else ->
                    bodyFields += EndpointParameterResolver.bodyParameters(parameter)
            }
        }
    }

    /**
     * 构造一个 OpenAPI parameter 对象。
     */
    private fun parameterObject(
        descriptor: EndpointParameterDescriptor,
        location: String,
        required: Boolean = descriptor.required,
    ): JsonObject = JsonObject().apply {
        addProperty("name", descriptor.name)
        addProperty("in", location)
        addProperty("required", location == "path" || required)
        if (descriptor.description.isNotBlank()) {
            addProperty("description", descriptor.description)
        }
        add("schema", leafSchema(descriptor.type))
        if (descriptor.sampleValue.isNotBlank()) {
            addProperty("example", descriptor.sampleValue)
        }
    }

    /**
     * 把点分字段描述（含 `list[]` 集合标记）组装为嵌套 JSON Schema。
     */
    private fun fieldsSchema(fields: List<EndpointParameterDescriptor>): JsonObject {
        val root = JsonObject().apply { addProperty("type", "object") }
        val properties = JsonObject()
        root.add("properties", properties)
        for (field in fields) {
            insertField(properties, field.name.split('.'), field)
        }
        return root
    }

    /**
     * 递归地把一个字段写入 schema 树的 properties 节点。
     */
    private fun insertField(properties: JsonObject, segments: List<String>, field: EndpointParameterDescriptor) {
        val rawSegment = segments.first()
        val isArray = rawSegment.endsWith("[]")
        val name = rawSegment.removeSuffix("[]")
        if (name.isEmpty()) return

        if (segments.size == 1) {
            val leaf = leafSchema(field.type, field)
            properties.add(name, if (isArray) arrayOf(leaf) else leaf)
            return
        }

        val existing = properties.getAsJsonObject(name)
        val childProperties: JsonObject
        if (existing != null) {
            childProperties = objectProperties(existing)
        } else {
            val child = JsonObject().apply { addProperty("type", "object") }
            val childProps = JsonObject()
            child.add("properties", childProps)
            properties.add(name, if (isArray) arrayOf(child) else child)
            childProperties = childProps
        }
        insertField(childProperties, segments.drop(1), field)
    }

    /**
     * 取对象 schema 的 properties 节点；数组 schema 时取其 items 的 properties。
     */
    private fun objectProperties(schema: JsonObject): JsonObject {
        val target = if (schema.get("type")?.asString == "array") {
            schema.getAsJsonObject("items")
        } else {
            schema
        }
        return target.getAsJsonObject("properties") ?: JsonObject().also { target.add("properties", it) }
    }

    private fun arrayOf(items: JsonObject): JsonObject = JsonObject().apply {
        addProperty("type", "array")
        add("items", items)
    }

    /**
     * 按 Java 类型文本推导叶子 schema。
     */
    private fun leafSchema(typeText: String, field: EndpointParameterDescriptor? = null): JsonObject {
        val simpleName = typeText
            .replace(Regex("<.*>"), "")
            .substringAfterLast('.')
            .removeSuffix("[]")
        val (type, format) = when (simpleName.lowercase()) {
            "int", "integer", "short", "byte" -> "integer" to "int32"
            "long" -> "integer" to "int64"
            "double", "float", "bigdecimal" -> "number" to null
            "boolean" -> "boolean" to null
            "string", "char", "character", "charsequence" -> "string" to null
            else -> when {
                "List" in typeText || "Set" in typeText || typeText.endsWith("[]") -> "array" to null
                "Map" in typeText -> "object" to null
                else -> "object" to null
            }
        }
        return JsonObject().apply {
            addProperty("type", type)
            if (format != null) {
                addProperty("format", format)
            }
            if (field != null) {
                if (field.description.isNotBlank()) {
                    addProperty("description", field.description)
                }
                if (field.sampleValue.isNotBlank()) {
                    addProperty("example", field.sampleValue)
                }
            }
        }
    }
}
