package com.lizhuolun.apihelper.core.contract

/**
 * 契约参数的来源分类，决定它会被拼进 URL、Header、Cookie 还是请求体。
 *
 * @author lizhuolun
 * @date 2026/10/27
 */
enum class ContractParamKind {
    /** Query 参数，对应 `@RequestParam` 或 `@SpringQueryMap` 展开出来的字段 */
    QUERY,

    /** 路径变量，对应 `@PathVariable` */
    PATH,

    /** 请求头，对应 `@RequestHeader` */
    HEADER,

    /** Cookie，对应 `@CookieValue` */
    COOKIE,

    /** 请求体，对应 `@RequestBody`，或客户端侧无注解的 DTO 参数 */
    BODY,

    /** multipart 表单字段，对应 `@RequestPart` */
    FORM,

    /** 无法识别归属的参数 */
    UNKNOWN,
}

/**
 * 单个契约参数的描述。
 *
 * [explicitName] 与 [sourceName] 必须分开保存：
 * 契约体检只在**两端都显式声明了名字**时才敢断言不一致，
 * 否则容易把「依赖编译参数 -parameters 的隐式命名」误判成错误。
 *
 * @property kind 参数来源分类
 * @property explicitName 注解上显式写出的名字，例如 `@RequestParam("userId")` 中的 `userId`；未写时为 null
 * @property sourceName 源码中的参数名，例如 `String userId` 中的 `userId`
 * @property required 声明为必填时为 true；注解缺省按 Spring 语义视为 true
 * @property typeText 参数类型的规范文本，例如 `java.util.List<java.lang.String>`
 * @author lizhuolun
 * @date 2026/10/27
 */
data class ContractParam(
    val kind: ContractParamKind,
    val explicitName: String?,
    val sourceName: String,
    val required: Boolean,
    val typeText: String,
) {

    /**
     * 参数在协议层面的实际名字：优先注解显式名，其次源码参数名。
     */
    val effectiveName: String
        get() = explicitName?.takeIf { it.isNotBlank() } ?: sourceName
}

/**
 * 单个端点方法的契约签名。
 *
 * 与 [com.lizhuolun.apihelper.core.HttpMappingInfo] 的区别：
 * HttpMappingInfo 在每次全工程扫描与 gutter 渲染时都会构造，必须保持轻量；
 * 契约签名需要遍历参数、读取注解、解析类型，成本更高，因此只在体检流程中按需计算并缓存。
 *
 * @property qualifier 方法唯一标识，与 HttpMappingInfo.qualifier 一致
 * @property params 全部参数描述，按声明顺序
 * @property bodyTypeText 请求体类型的规范文本，没有请求体时为 null
 * @property returnTypeText 返回类型的规范文本（P0 阶段仅记录，暂不参与规则）
 * @author lizhuolun
 * @date 2026/10/27
 */
data class ContractSignature(
    val qualifier: String,
    val params: List<ContractParam>,
    val bodyTypeText: String? = null,
    val returnTypeText: String? = null,
) {

    /**
     * 取指定来源分类的参数。
     *
     * @param kind 参数来源分类
     * @return 该分类下的参数列表
     */
    fun paramsOf(kind: ContractParamKind): List<ContractParam> =
        params.filter { it.kind == kind }

    /**
     * 全部 Query 参数。
     */
    val queryParams: List<ContractParam>
        get() = paramsOf(ContractParamKind.QUERY)

    /**
     * 全部路径变量参数。
     */
    val pathParams: List<ContractParam>
        get() = paramsOf(ContractParamKind.PATH)
}